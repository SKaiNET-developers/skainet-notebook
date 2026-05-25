package sk.ainet.app.notebook.tools

import sk.ainet.lang.dag.DagBuilder
import sk.ainet.lang.dag.GraphValue
import sk.ainet.lang.dag.mulScalar
import sk.ainet.lang.dag.sigmoid
import sk.ainet.lang.dag.subScalar
import sk.ainet.lang.tensor.Tensor
import sk.ainet.lang.tensor.minus
import sk.ainet.lang.tensor.sigmoid
import sk.ainet.lang.tensor.times
import sk.ainet.lang.types.DType

/**
 * Polyfill for `tanh`: SKaiNET 0.25.0 doesn't ship `tanh` as a primitive
 * — neither on `TensorOps` (so no `Tensor.tanh()` extension) nor as a
 * `DagBuilder.tanh(...)` DSL op. Until the upstream proposal in
 * `docs/upstream/tanh-activation.md` lands, every notebook that wants the
 * micrograd-default activation has to either substitute `sigmoid` or write
 * the same `2 * sigmoid(2x) - 1` composition by hand.
 *
 * Both shims here record as a `mulScalar -> sigmoid -> mulScalar ->
 * subScalar` chain in the tape, so the rendered graph shows the
 * decomposition rather than a single `tanh` block — that's faithful to
 * what the kernel is actually computing today, and collapses to a real
 * `tanh` node once the upstream primitive lands and these polyfills are
 * removed.
 *
 * The identity is exact: `tanh(x) = 2*sigmoid(2x) - 1` for all real `x`.
 * Numerical stability for very large `|x|` is bounded by `sigmoid`'s own
 * saturation behaviour — fine for typical NN inputs.
 */

/** `Tensor<T, V>.tanh()` polyfill — used inside `sequential { ... }` activations. */
public fun <T : DType, V> Tensor<T, V>.tanh(): Tensor<T, V> =
    (this * 2).sigmoid() * 2 - 1

/** `DagBuilder.tanh(input)` polyfill — used inside `dag { ... }` programs. */
public fun DagBuilder.tanh(input: GraphValue<*>, id: String = ""): GraphValue<*> =
    subScalar(mulScalar(sigmoid(mulScalar(input, 2)), 2), 1, id = id)
