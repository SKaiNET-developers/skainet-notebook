# Upstream proposal: `GraphProgram.toGraphviz()` (and `.asDot()`)

**Status:** draft — to be filed against `mlx/SKaiNET` once we agree on the
shape. Tracks a small ergonomic gap surfaced by the notebook integration.

**Affected modules**

- `skainet-lang-dag` — owns `GraphProgram`, the symbolic DAG produced by the
  `dag { … }` DSL.
- `skainet-compile-dag` — owns `GraphProgram.toComputeGraph()` and the
  `ComputeGraph.toGraphviz()` exporter.

## Problem

To render a DSL-built model as a Graphviz diagram today, a caller has to chain
two unrelated extensions:

```kotlin
val program = dag { … }
val dot: String = program.toComputeGraph().toGraphviz()
```

`toComputeGraph` is a compilation step. From the perspective of someone who
just wants to *look at the graph*, lowering it through the compiler is an
implementation detail — they don't need the resulting `ComputeGraph` for
anything else.

The notebook integration in `skainet-notebook` wraps this two-step composition
in `GraphProgram.asDot()` (see
`kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/display/DagDotRender.kt`)
so a cell can just write:

```kotlin
dag { … }.asDot()
```

That helper belongs upstream. It's not notebook-specific — every consumer that
visualizes a DAG before training (CLI tools, tests, IDE inspectors) hits the
same friction.

## Proposal

Add an extension in `skainet-compile-dag`:

```kotlin
// skainet-compile/skainet-compile-dag/src/commonMain/kotlin/sk/ainet/lang/graph/utils/graphviz.kt
public fun GraphProgram.toGraphviz(rankdir: String = "LR"): String =
    this.toComputeGraph().toGraphviz(rankdir)
```

Symmetric with the existing `ComputeGraph.toGraphviz(rankdir: String)` — same
signature shape, same default — so the two are interchangeable at the call
site once the caller decides which level they want to operate on.

## Non-goals

- No new `DotGraph`/`Dot` type at the public API. The notebook side already
  has a `Dot(val source: String)` wrapper for cell-result dispatch; keeping
  upstream as `String`-typed avoids tying skainet to a renderer choice.
- No change to the existing `DotGraph` data class or `drawDot(...)` signatures
  in `sk.ainet.lang.graph.utils.graphviz` — the new overload sits next to them.

## Migration

Downstream of merge, the notebook helper collapses to a one-liner:

```kotlin
// kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/display/DagDotRender.kt
public fun GraphProgram.asDot(rankdir: String = "LR"): Dot =
    Dot(this.toGraphviz(rankdir))
```

…and the `sk.ainet.lang.graph.dsl.toComputeGraph` import goes away from the
notebook module. The `asDot` wrapper stays here because it produces the
notebook-side `Dot` cell-result type, not a raw `String`.

## Alternatives considered

- **Make `toGraphviz` live on `GraphProgram` instead of `ComputeGraph`.** No —
  `ComputeGraph` is the more general type (tape-built graphs and tests use it
  too). Adding an overload is strictly additive.
- **Return a `DotGraph` wrapper instead of `String`.** Consistent with the
  existing `drawDot(...)` return type, but every current caller of the
  `ComputeGraph.toGraphviz()` extension wants the raw string. Keeping the new
  overload `String`-typed mirrors that.
