# Upstream proposal: `Module<T, V>.toGraphviz(input)`

**Status:** draft, Phase 1 — to be filed against `mlx/SKaiNET` once the
`GraphProgram.toGraphviz()` proposal lands (see
[`graphprogram-to-graphviz.md`](./graphprogram-to-graphviz.md)). The
Phase 2 follow-up that extends the same exporter with per-node `data`
and `grad` (matching micrograd's `draw_dot` output) is tracked
separately in [`graphviz-with-grads.md`](./graphviz-with-grads.md) and
depends on the autograd backward pass landing first.

**Affected modules**

- `skainet-lang-core` — owns `Module<T, V>` and the `sequential { … }`
  builder.
- `skainet-compile-dag` — owns the recording infrastructure
  (`DefaultGraphExecutionContext`, `DefaultExecutionTape`,
  `Execution.tapeStack`, `tape.toComputeGraph()`,
  `ComputeGraph.toGraphviz()`).

## Problem

Today, rendering a Module built with the NN DSL as a Graphviz graph
requires the caller to assemble four moving pieces by hand:

```kotlin
val ctx = DefaultGraphExecutionContext.tape(baseOps = VoidTensorOps())
val (tape, _) = ctx.record {
    val stack = Execution.tapeStack
    val pushed = ctx.currentTape
    if (pushed != null) stack.pushTape(pushed)
    try { module.forward(input, ctx) }
    finally { if (pushed != null) stack.popTape() }
}
val graph = when (tape) {
    is DefaultExecutionTape -> tape.toComputeGraph()
    else -> tape?.toComputeGraph() ?: DefaultComputeGraph()
}
val dot: String = graph.toGraphviz()
```

This is exactly the dance `ModelExportFacade.exportModelToJson(model,
forwardPass = { ... })` does internally for JSON export. Visualizers,
notebooks, tests, and CLI inspectors all need the same flow — just with
a different output format on the end.

The notebook integration in `skainet-notebook` wraps this in
`Module<T, V>.asDot(input)` (see
`kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/display/ModuleDotRender.kt`)
so a cell can write:

```kotlin
n.asDot(x)
```

That helper belongs upstream. It uses purely public types from
`skainet-compile-dag` + `skainet-compile-core` (the same machinery the
forthcoming autograd backprop pass exercises), so there's no notebook-
specific anything inside it.

## Proposal

Add an extension in `skainet-compile-dag`, alongside the
`GraphProgram.toGraphviz()` overload from the sibling proposal:

```kotlin
// skainet-compile/skainet-compile-dag/src/commonMain/kotlin/sk/ainet/lang/graph/utils/graphviz.kt
public fun <T : DType, V> Module<T, V>.toGraphviz(
    input: Tensor<T, V>,
    rankdir: String = "LR"
): String {
    val ctx = DefaultGraphExecutionContext.tape(baseOps = VoidTensorOps())
    val module = this
    val (tape, _) = ctx.record {
        val stack = Execution.tapeStack
        val pushed = ctx.currentTape
        if (pushed != null) stack.pushTape(pushed)
        try { module.forward(input, ctx) }
        finally { if (pushed != null) stack.popTape() }
    }
    val graph = when (tape) {
        is DefaultExecutionTape -> tape.toComputeGraph()
        else -> tape?.toComputeGraph() ?: DefaultComputeGraph()
    }
    return graph.toGraphviz(rankdir)
}
```

Symmetric with the existing `ComputeGraph.toGraphviz(rankdir)` and the
proposed `GraphProgram.toGraphviz(rankdir)`. Same signature shape, same
default — the three are interchangeable at the call site once the
caller picks which level (eager Module, symbolic GraphProgram, or
lowered ComputeGraph) they want to render.

## Non-goals

- Don't expose `DefaultGraphExecutionContext` /
  `Execution.tapeStack` machinery in the helper's signature. The whole
  point is that callers shouldn't have to know about recording — they
  hand in a Module + input and get a DOT string back.
- Don't change `Module.forward(...)` semantics. The helper creates a
  *separate* recording context internally; the user's own ctx is never
  touched.
- Don't introduce a new return type. The string is consumed by
  Graphviz tooling (renderers, file writers, asserters) the same way
  the existing exporter's String is.

## Migration

Downstream of merge, the notebook helper collapses to:

```kotlin
// kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/display/ModuleDotRender.kt
public fun <T : DType, V> Module<T, V>.asDot(
    input: Tensor<T, V>,
    rankdir: String = "LR"
): Dot = Dot(this.toGraphviz(input, rankdir))
```

…and the `DefaultGraphExecutionContext` / `VoidTensorOps` /
`Execution.tapeStack` / `DefaultExecutionTape.toComputeGraph` imports
disappear from the notebook module. The `asDot` wrapper stays because
it produces the notebook-side `Dot` cell-result type (whose render hook
turns it into inline SVG), not a raw `String`.

## Alternatives considered

- **Make it an overload on `ExecutionContext` instead of `Module`.**
  Possible (`ctx.toGraphviz(module, input)`) but the model-first call
  site is more readable — `module.toGraphviz(input)` reads "render
  this module with this input", which matches how mental models work.
- **Take a `forwardPass: () -> Unit` lambda like
  `exportModelToJson(...)` does.** More flexible (caller can run any
  multi-step forward) but the common case is a single
  `module.forward(input, ctx)` call, and overloading specialization
  reads cleanly. The lambda form can be added later as a second
  overload without breaking the simple form.
- **Use `tapeAndGraph(...)` and skip the tape→graph conversion.**
  `DefaultGraphExecutionContext.tapeAndGraph()` builds a graph online
  via `GraphSink`, no tape conversion needed. Worth investigating as a
  follow-up optimization — the API surface this proposal commits to
  stays the same either way.
