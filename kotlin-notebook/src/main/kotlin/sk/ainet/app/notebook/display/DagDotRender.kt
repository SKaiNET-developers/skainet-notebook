package sk.ainet.app.notebook.display

import sk.ainet.lang.dag.GraphProgram
import sk.ainet.lang.graph.dsl.toComputeGraph
import sk.ainet.lang.graph.utils.toGraphviz

/**
 * Render a DAG-DSL [GraphProgram] as a [Dot] cell result.
 *
 * Hides the two-step lowering (`GraphProgram` → `ComputeGraph` → DOT) that a
 * notebook author would otherwise repeat at every cell. Return the result from
 * a cell to have it rendered inline as SVG by the Jupyter integration.
 *
 * ```
 * dag {
 *     val x = input<FP32>("x", TensorSpec("x", listOf(1, 4), "FP32"))
 *     val w = parameter<FP32, Float>("w") { shape(4, 4) { ones() } }
 *     output(relu(matmul(x, w)))
 * }.asDot()
 * ```
 *
 * Lives notebook-side rather than in `skainet-notebook-extensions` because the
 * extensions module is intentionally skainet-free (any DOT producer can use
 * it). Upstreaming target: a `GraphProgram.toGraphviz()` overload in
 * `skainet-compile-dag` that drops the explicit `.toComputeGraph()` call. See
 * `docs/upstream/graphprogram-to-graphviz.md`.
 */
public fun GraphProgram.asDot(rankdir: String = "LR"): Dot =
    Dot(this.toComputeGraph().toGraphviz(rankdir))
