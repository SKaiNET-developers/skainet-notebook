# Upstream proposal: Graphviz export with per-node `data` + `grad`

**Status:** draft, Phase 2 — depends on (a) the
[`Module<T, V>.toGraphviz(input)`](./module-to-graphviz.md) Phase 1
proposal landing first, and (b) the upcoming autograd backward pass
being available end-to-end. Decoupled from Phase 1 so the simpler
shape-only renderer can ship without waiting on autograd.

**Affected modules**

- `skainet-compile-dag` — owns the `ComputeGraph.toGraphviz()` exporter
  and the `DefaultExecutionTape`/`tape.toComputeGraph()` lowering this
  proposal reads node values from.
- `skainet-compile-core` — owns the gradient-tracer infrastructure
  (`Execution.tapeStack`, `RecordingTensorOpsDecorator`). No changes
  needed here; the proposal just consumes what the backward pass
  records.

## Problem

Karpathy's micrograd `draw_dot(y)` renders each `Value` node as a
three-field record label:

```
{ name | data  1.0000 | grad  -0.5000 }
```

Both forward `data` and backward `grad` are populated — the SVG is
produced *after* `y.backward()` has propagated gradients up the tape,
so every node carries both numbers. That's what makes the picture
pedagogically useful: a reader can trace one number forward to the
output, then chase the partial back to the input, and the whole story
is on one diagram.

SKaiNET's current `ComputeGraph.toGraphviz()` exporter is shape-only:
each node carries `operationName | id` and the edges carry nothing.
The Phase 1 renderers we ship (`GraphProgram.toGraphviz`,
`Module.toGraphviz(input)`) inherit the same limitation. With
`VoidTensorOps` driving the recording context, there's no real `data`
to put on the graph in the first place — and with no autograd backward
having run, there's no `grad` either.

Once both pieces exist upstream (real ops + a backward pass that
writes gradients onto the tape), the renderer can finally produce
micrograd-style diagrams. That's this proposal.

## Proposal

Add an opt-in `includeValues: Boolean = false` flag to the
`toGraphviz` overload family in `skainet-compile-dag`:

```kotlin
// skainet-compile/skainet-compile-dag/src/commonMain/kotlin/sk/ainet/lang/graph/utils/graphviz.kt
public fun ComputeGraph.toGraphviz(
    rankdir: String = "LR",
    includeValues: Boolean = false
): String

public fun GraphProgram.toGraphviz(
    rankdir: String = "LR",
    includeValues: Boolean = false
): String =
    this.toComputeGraph().toGraphviz(rankdir, includeValues)

public fun <T : DType, V> Module<T, V>.toGraphviz(
    input: Tensor<T, V>,
    rankdir: String = "LR",
    includeValues: Boolean = false
): String =
    runUnderRecordingContext(input).toGraphviz(rankdir, includeValues)
```

When `includeValues = true`, each node's record label expands from:

```
{ operationName | id }
```

to:

```
{ operationName | id | data <formatted> | grad <formatted> }
```

`<formatted>` is a small fixed-precision summary of the underlying
tensor — e.g. `1.0000` for scalars, `[1.0000, -2.0000]` for tiny
vectors, `<shape=(1,2)>` for anything large enough to be unreadable.
The exact format is a renderer detail; the API surface only commits to
"data + grad appear on the node when the flag is set."

The flag's default is `false` so existing callers (and the Phase 1
shape-only path that ships before autograd lands) stay fast and don't
break when they have no real tensor values to pull from.

## Where the numbers come from

The tape conversion (`DefaultExecutionTape.toComputeGraph()`) already
threads `TensorSpec` metadata onto each `GraphNode.outputs` entry. The
spec carries `name`, `shape`, `dtype`, and a `metadata: Map<String,
Any>` bag. Two new well-known keys:

- `"data"` — the formatted forward value, written when the recording
  context is run with a non-`VoidTensorOps` backend.
- `"grad"` — the formatted backward value, written when the autograd
  pass walks the tape and computes gradients.

The renderer only reads these keys; it never computes anything. If a
key is missing (e.g. `"grad"` because no backward ran), the renderer
omits that field rather than printing a placeholder — same visual
behaviour as micrograd, which only renders `grad` once `y.backward()`
has been called.

## Notebook-side migration

Once this lands, the notebook helper passes the flag through:

```kotlin
// kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/display/ModuleDotRender.kt
public fun <T : DType, V> Module<T, V>.asDot(
    input: Tensor<T, V>,
    rankdir: String = "LR",
    includeValues: Boolean = false
): Dot = Dot(this.toGraphviz(input, rankdir, includeValues))
```

…and the micrograd tribute notebooks (`MicrogradNeuron.ipynb`,
`MicrogradNeuronNN.ipynb`) get one extra line — a backward call before
`asDot(x, includeValues = true)` — that turns the shape-only diagram
into the full micrograd-style picture.

## Non-goals

- **Don't change the default.** `toGraphviz()` (no flag) stays shape-
  only. Adding `data`/`grad` to the default would punish every
  shape-only caller (CI test fixtures, structural diffs, "is the
  topology I expect there?" checks) with formatting cost and noisier
  diffs.
- **Don't define a backward-pass API here.** That's an entirely
  separate upstream change. This proposal just commits to *consuming*
  whatever the backward pass writes into `TensorSpec.metadata["grad"]`.
- **Don't take a separate `forwardPass` lambda.** The
  Module-overload's contract is "one forward call with the given
  input"; if a caller needs multi-step training or backward inside the
  recording window, they should drop down to the lower-level
  `ComputeGraph.toGraphviz()` path and drive the recording themselves.

## Alternatives considered

- **Separate `toGraphvizWithGrads(...)` function.** Cleaner names, but
  doubles the API surface and forces callers who want both topology
  *and* values to choose at the call site rather than at one flag.
  A flag also lets us add a third level later (`includeValues = true`
  + `includeGradFlow = true` for highlighting the backward path) with
  a second opt-in flag, no new function.
- **Sidecar JSON instead of inline labels.** Emit DOT + a separate
  JSON dump of values, let the renderer correlate them. Rejected: the
  whole point of micrograd's draw_dot is that one diagram tells the
  whole story. Splitting the values into a sidecar defeats the
  pedagogical use case.
- **Skip flag, always emit when grads exist.** "If the metadata is
  there, render it." Tempting but surprising — a test that asserts on
  DOT contents would start failing the day a caller upstream of it
  starts running backward. Explicit opt-in keeps the contract loud.

## Open questions

1. **Multi-output ops.** What does "the node's value" mean for an op
   with N outputs? Two reasonable answers: render each output as its
   own row in the record label, or pick the primary output and label
   it. micrograd doesn't have this problem (every op is single-output)
   so there's no canonical answer to inherit.
2. **Value formatting for large tensors.** Scalars and tiny vectors
   are easy. For a `Shape(1, 224, 224, 3)` activation, what goes on
   the diagram — `<shape=(1,224,224,3)>`, a min/max/mean summary, or
   nothing? Lean toward "nothing" by default and let a future
   `valueFormatter` callback override.
3. **Pre-backward grad rendering.** Should `includeValues = true`
   before `backward()` runs render `data` only (no `grad` field), or
   render `grad N/A` placeholders? micrograd's behaviour (omit
   entirely) is the path of least surprise.
