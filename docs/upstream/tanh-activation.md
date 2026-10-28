# Upstream proposal: `tanh` as a first-class activation primitive

**Status:** draft — to be filed against `mlx/SKaiNET`. Independent of the
graphviz-export proposals; can ship anytime.

**Affected modules**

- `skainet-lang-core` — declares the op on `TensorOps`, the
  `TanhOperation` class, the `Tensor<T, V>.tanh()` extension, and the
  `VoidTensorOps` stub.
- `skainet-backends/skainet-backend-cpu` — supplies the real
  numerically-stable CPU implementation in `DefaultCpuOps`.
- `skainet-compile/skainet-compile-core` —
  `RecordingTensorOpsDecorator` records the op into the tape;
  `stableInputName` learns to label `TanhOperation`'s single input as
  `"input"`.
- `skainet-compile/skainet-compile-dag` — autograd backward formula
  `dy/dx = 1 - tanh(x)^2`, added next to the existing `SigmoidOperation`
  backward entry in `DefaultExecutionTape`. KSP-generated DAG-DSL op
  `DagBuilder.tanh(input)` appears automatically once the `TensorOps`
  declaration is annotated.

## Problem

`tanh` is one of the canonical activation functions in ML pedagogy and
the default in micrograd, the most-tutorial'd autodiff library on the
planet. SKaiNET 0.25.0 ships `relu`, `gelu`, `elu`, `leakyRelu`,
`silu`, `sigmoid`, `softmax`, and `logSoftmax` — but not `tanh`.

The gap is visible inside the upstream codebase itself:
`skainet-compile/skainet-compile-dag/.../DefaultExecutionTape.kt:910-914`
has the comment

> `// Since we don't have tanh yet, we can use:` `tanh(x) = (exp(2x) - 1) / (exp(2x) + 1)`
> `// or just use a simpler approximation if tanh is missing.`
> `// Actually, let's implement a simple tanh using sigmoid if possible.`
> `// tanh(x) = 2 * sigmoid(2x) - 1`

— a local lambda named `tanh` synthesized inside the GELU backward
formula because no real `tanh` op exists. Every consumer (the GELU
backward there, the notebook's micrograd tribute polyfill at
`kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/tools/Tanh.kt`,
and presumably any downstream model that needs RNN/LSTM gates) has to
re-derive the same composition. That's exactly the kind of duplication a
core primitive should eliminate.

## Proposal

Add `tanh` symmetrically with `sigmoid`. The wiring follows the
existing six-layer pattern — none of it is novel except the math.

### 1. `TensorOps` interface (`skainet-lang-core`)

```kotlin
// .../sk/ainet/lang/tensor/ops/TensorOps.kt — alongside @Diff @ActivationDsl sigmoid
@Diff
@ActivationDsl
public fun <T : DType, V> tanh(tensor: Tensor<T, V>): Tensor<T, V>
```

The `@Diff` annotation enables autograd; `@ActivationDsl` makes the op
available in the NN DSL's `activation { ... }` block; KSP picks both up
and generates the `DagBuilder.tanh(input)` extension automatically (the
existing `SigmoidOperation` follows the same path).

### 2. `TanhOperation<T, V>` (`skainet-lang-core`)

```kotlin
// .../sk/ainet/lang/tensor/ops/TensorOperations.kt — copy of SigmoidOperation
public class TanhOperation<T : DType, V>(
    parameters: Map<String, Any> = emptyMap()
) : BaseOperation("tanh", "activation", parameters) {

    override fun <T2 : DType, V2> execute(inputs: List<Tensor<T2, V2>>): List<Tensor<T2, V2>> {
        require(inputs.size == 1) { "Tanh operation requires exactly 1 input" }
        throw UnsupportedOperationException("Direct execution not supported in graph mode")
    }

    override fun validateInputs(inputs: List<TensorSpec>): ValidationResult =
        if (inputs.size != 1) ValidationResult.Invalid(listOf("Tanh requires 1 input, got ${inputs.size}"))
        else ValidationResult.Valid

    override fun inferOutputs(inputs: List<TensorSpec>): List<TensorSpec> {
        require(inputs.size == 1)
        return listOf(
            TensorSpec(
                name = "tanh_output",
                shape = inputs[0].shape,
                dtype = inputs[0].dtype,
                requiresGrad = inputs[0].requiresGrad
            )
        )
    }

    override fun clone(newParameters: Map<String, Any>): Operation = TanhOperation<T, V>(newParameters)
}
```

### 3. `Tensor.tanh()` extension (`skainet-lang-core`)

```kotlin
// .../sk/ainet/lang/tensor/TensorExtensions.kt
public fun <T : DType, V> Tensor<T, V>.tanh(): Tensor<T, V> = ops.tanh(this)
```

### 4. `VoidTensorOps.tanh` (`skainet-lang-core`)

```kotlin
// .../sk/ainet/lang/tensor/ops/VoidTensorOps.kt — copy of the sigmoid stub
override fun <T : DType, V> tanh(tensor: Tensor<T, V>): Tensor<T, V> = passthrough(tensor)
```

### 5. CPU backend (`skainet-backends/skainet-backend-cpu`)

```kotlin
// .../sk/ainet/exec/tensor/ops/DefaultCpuOps.kt — alongside sigmoid
override fun <T : DType, V> tanh(tensor: Tensor<T, V>): Tensor<T, V> {
    // kotlin.math.tanh is already numerically stable across the full
    // float range — it bottoms out at ±1 cleanly and avoids the
    // overflow that the naive (exp(2x)-1)/(exp(2x)+1) form would hit.
    return tensor.elementWiseFloat { x -> kotlin.math.tanh(x) }
}
```

(Whatever `elementWiseFloat` helper the existing CPU activations use —
the pattern next to `sigmoid` in `DefaultCpuOps.kt:1639`.)

### 6. Recording decorator (`skainet-compile-core`)

```kotlin
// .../sk/ainet/tape/RecordingExecution.kt — copy of the sigmoid block
override fun <T : DType, V> tanh(tensor: Tensor<T, V>): Tensor<T, V> {
    val out = base.tanh(tensor)
    record(TanhOperation<T, V>(), listOf(tensor), listOf(out))
    return out
}

// And teach stableInputName about TanhOperation:
is TanhOperation<*, *> -> "input"
```

### 7. Autograd backward (`skainet-compile-dag`)

```kotlin
// .../sk/ainet/lang/graph/DefaultExecutionTape.kt — alongside SigmoidOperation backward
// d/dx tanh(x) = 1 - tanh(x)^2
TanhOperation -> {
    val tanhX = outputs[0]
    val gradIn = upstream * (1 - tanhX * tanhX)
    addIncomingGrad(inputId, gradIn)
}
```

And the comment block on lines 898-919 collapses from "synthesize a
local `tanh` lambda from sigmoid because the primitive doesn't exist"
to "call `ops.tanh(...)` directly."

## Migration

Downstream of merge, the notebook polyfill at
`kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/tools/Tanh.kt`
gets deleted in its entirety. The two micrograd tribute notebooks
already call `tanh(...)` / `it.tanh()` through that polyfill — they'll
silently start resolving against the upstream primitive instead, with
no source change needed. The graph renders will simplify from a
four-node `mulScalar -> sigmoid -> mulScalar -> subScalar` chain to a
single `tanh` block — a visible quality improvement.

## Non-goals

- **Don't approximate.** `kotlin.math.tanh` (and the libm `tanhf` it
  delegates to on JVM) is fast and IEEE-correct; there's no reason to
  ship the `2*sigmoid(2x) - 1` composition as the CPU primitive even
  though it's the polyfill shape downstream.
- **Don't add a Hardtanh / clamped variant in this PR.** That's a
  separate op with separate semantics (`hardtanh(x) = clamp(x, -1, 1)`)
  and a separate use case (faster mobile inference). File it as a
  follow-up if needed.
- **Don't change GELU's backward formula in the same PR.** The
  synthesized-tanh trick in `DefaultExecutionTape.kt:898-919` should
  switch to the primitive, but that's a focused cleanup commit best
  done after the new op is verified end-to-end.

## Alternatives considered

- **Ship as a notebook-only polyfill forever.** Rejected: the same
  polyfill lives inside SKaiNET itself for GELU backward; the
  duplication signals a missing primitive, not a notebook concern.
- **Synthesize `tanh` from the existing `exp` / `divide` ops in the
  CPU backend.** Works mathematically but loses numerical stability
  for `|x|` past ~10 — `exp(2 * 10)` is already pushing FP32. The
  intrinsic `kotlin.math.tanh` saturates cleanly and is the standard
  choice in every other framework's CPU path.
- **Add `tanh` only to the DAG DSL, skip `TensorOps`.** Would close
  the notebook gap but not the GELU-backward duplication, and would
  prevent the NN DSL's `activation { it.tanh() }` form (which is
  what users coming from PyTorch / Keras / micrograd will reach for
  first). Wiring all six layers is the right shape.

## Open questions

1. **Behaviour on integer dtypes.** SKaiNET's `Tensor<T, V>` is
   generic over `DType`. `tanh` is mathematically real-valued; the
   sigmoid/relu siblings already make the same assumption. Reasonable
   to require `T: FloatDType` or `Tensor<FP32, Float>`-only — match
   whatever pattern `sigmoid` uses upstream.
2. **GELU backward cleanup.** Should the synthesized-tanh removal in
   `DefaultExecutionTape.kt:898-919` ride along in this PR or come
   as a follow-up? Recommendation: follow-up, to keep this PR's
   diff focused on "add a new op."
