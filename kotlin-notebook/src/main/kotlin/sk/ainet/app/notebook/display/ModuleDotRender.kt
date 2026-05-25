package sk.ainet.app.notebook.display

import sk.ainet.lang.graph.DefaultComputeGraph
import sk.ainet.lang.graph.DefaultExecutionTape
import sk.ainet.lang.graph.DefaultGraphExecutionContext
import sk.ainet.lang.graph.utils.toGraphviz
import sk.ainet.lang.nn.Module
import sk.ainet.lang.tape.toComputeGraph
import sk.ainet.lang.tensor.Tensor
import sk.ainet.lang.tensor.ops.VoidTensorOps
import sk.ainet.lang.types.DType
import sk.ainet.tape.Execution

/**
 * Render an NN-DSL [Module] as a [Dot] cell result by running a single
 * forward pass under a recording tape, then lowering the captured tape to a
 * `ComputeGraph` and serializing with SKaiNET's `toGraphviz` exporter.
 *
 * ```
 * val n = sequential<FP32, Float> {
 *     input(2)
 *     dense(1)
 *     activation { it.sigmoid() }
 * }
 * val x = tensor<FP32, Float>(ctx, FP32::class) {
 *     tensor { shape(1, 2) { fromArray(floatArrayOf(1f, -2f)) } }
 * }
 * n.asDot(x)
 * ```
 *
 * The recording context uses [VoidTensorOps] (shape-only, no real
 * computation), so this is cheap and side-effect-free regardless of what
 * the model would do at training/eval time. The same gradient tracer
 * underpins SKaiNET's autograd path — visualizing a module here exercises
 * the same op-recording machinery backprop will rely on.
 *
 * Upstreaming target: `Module<T, V>.toGraphviz(input)` in
 * `skainet-compile-dag`. See `docs/upstream/module-to-graphviz.md`.
 */
public fun <T : DType, V> Module<T, V>.asDot(
    input: Tensor<T, V>,
    rankdir: String = "LR"
): Dot {
    val ctx = DefaultGraphExecutionContext.tape(baseOps = VoidTensorOps())
    val module = this
    val (tape, _) = ctx.record {
        val stack = Execution.tapeStack
        val pushed = ctx.currentTape
        if (pushed != null) stack.pushTape(pushed)
        try {
            module.forward(input, ctx)
        } finally {
            if (pushed != null) stack.popTape()
        }
    }
    val graph = when (tape) {
        is DefaultExecutionTape -> tape.toComputeGraph()
        else -> tape?.toComputeGraph() ?: DefaultComputeGraph()
    }
    return Dot(graph.toGraphviz(rankdir))
}
