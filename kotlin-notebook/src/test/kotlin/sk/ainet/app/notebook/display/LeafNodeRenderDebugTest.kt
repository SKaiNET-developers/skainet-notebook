package sk.ainet.app.notebook.display

import sk.ainet.lang.dag.add
import sk.ainet.lang.dag.dag
import sk.ainet.lang.dag.matmul
import sk.ainet.lang.graph.DefaultExecutionTape
import sk.ainet.lang.graph.DefaultGraphExecutionContext
import sk.ainet.lang.graph.dsl.toComputeGraph
import sk.ainet.lang.graph.utils.toGraphviz
import sk.ainet.lang.nn.DefaultNeuralNetworkExecutionContext
import sk.ainet.lang.nn.dsl.sequential
import sk.ainet.lang.tape.toComputeGraph
import sk.ainet.lang.tensor.dsl.tensor
import sk.ainet.lang.tensor.ops.VoidTensorOps
import sk.ainet.lang.tensor.sigmoid
import sk.ainet.lang.types.FP32
import sk.ainet.tape.Execution
import kotlin.test.Test

/**
 * Debug test: are leaf nodes (`constant`, `parameter`) actually surfacing
 * in the rendered DOT?
 *
 * The notebook user reported that `program.asDot()` for a `matmul(x, w) +
 * b` graph shows only the op nodes connected in a chain — `add` looks
 * like a 1-input op, and `matmul` looks like a source node, because the
 * three leaves (`const_x`, `param_w`, `param_b`) are missing from the
 * picture. This test pinpoints which layer drops them:
 *
 *  1. Print the GraphProgram's recorded node IDs (DSL layer).
 *  2. Lower to ComputeGraph and print its node IDs + edges (lowering).
 *  3. Render to DOT and dump (renderer).
 *
 * The first layer that's missing leaves is the one to fix upstream.
 */
class LeafNodeRenderDebugTest {

    @Test
    fun dump_each_layer_so_we_can_see_where_leaves_drop() {
        val program = dag {
            val x = constant<FP32, Float>("x") {
                fromArray(floatArrayOf(1.0f, -2.0f), shape = listOf(1, 2))
            }
            val w = parameter<FP32, Float>("w") { shape(2, 1) { ones() } }
            val b = parameter<FP32, Float>("b") { shape(1) { zeros() } }

            output(add(matmul(x, w), b))
        }

        println("\n=== Layer 1: GraphProgram (DSL output) ===")
        println("nodes.size = ${program.nodes.size}")
        program.nodes.forEachIndexed { i, def ->
            println("  [$i] id=${def.id}  op=${def.operation.name}  type=${def.operation.type}  inputs.size=${def.inputs.size}  outputs.size=${def.outputs.size}")
        }
        println("outputs = ${program.outputs.map { it.nodeId }}")

        val graph = program.toComputeGraph()

        println("\n=== Layer 2: ComputeGraph (after lowering) ===")
        println("nodes.size = ${graph.nodes.size}")
        graph.nodes.forEachIndexed { i, n ->
            println("  [$i] id=${n.id}  op=${n.operationName}  type=${n.operationType}  inputs.size=${n.inputs.size}  outputs.size=${n.outputs.size}")
        }
        println("edges.size = ${graph.edges.size}")
        graph.edges.forEachIndexed { i, e ->
            println("  [$i] ${e.source.id}(out=${e.sourceOutputIndex}) -> ${e.destination.id}(in=${e.destinationInputIndex})")
        }

        println("\n=== Layer 3: Rendered DOT ===")
        println(graph.toGraphviz())
    }

    @Test
    fun tape_path_with_synthesizeExternalInputs_flag_decides_whether_leaves_appear() {
        val nnCtx = DefaultNeuralNetworkExecutionContext()
        val n = sequential<FP32, Float> {
            input(2)
            dense(1)
            activation { it.sigmoid() }
        }
        val x = tensor<FP32, Float>(nnCtx, FP32::class) {
            tensor { shape(1, 2) { fromArray(floatArrayOf(1.0f, -2.0f)) } }
        }

        // Mirror our Module.asDot helper: tape-recording context, run forward,
        // capture tape.
        val recCtx = DefaultGraphExecutionContext.tape(baseOps = VoidTensorOps())
        val (tape, _) = recCtx.record {
            val stack = Execution.tapeStack
            val pushed = recCtx.currentTape
            if (pushed != null) stack.pushTape(pushed)
            try { n.forward(x, recCtx) } finally { if (pushed != null) stack.popTape() }
        }

        val tapeAsDefault = tape as? DefaultExecutionTape ?: error("expected DefaultExecutionTape")

        println("\n=== TAPE path, synthesizeExternalInputs = false (current helper behaviour) ===")
        val graphNoSynth = tapeAsDefault.toComputeGraph(synthesizeExternalInputs = false)
        println("nodes.size = ${graphNoSynth.nodes.size}")
        graphNoSynth.nodes.forEachIndexed { i, nd ->
            println("  [$i] id=${nd.id}  op=${nd.operationName}  type=${nd.operationType}  inputs=${nd.inputs.size}")
        }
        println("edges.size = ${graphNoSynth.edges.size}")

        println("\n=== TAPE path, synthesizeExternalInputs = true (proposed fix) ===")
        val graphSynth = tapeAsDefault.toComputeGraph(synthesizeExternalInputs = true)
        println("nodes.size = ${graphSynth.nodes.size}")
        graphSynth.nodes.forEachIndexed { i, nd ->
            println("  [$i] id=${nd.id}  op=${nd.operationName}  type=${nd.operationType}  inputs=${nd.inputs.size}")
        }
        println("edges.size = ${graphSynth.edges.size}")
        graphSynth.edges.forEachIndexed { i, e ->
            println("  [$i] ${e.source.id}(out=${e.sourceOutputIndex}) -> ${e.destination.id}(in=${e.destinationInputIndex})")
        }
    }
}
