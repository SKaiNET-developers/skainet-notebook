package sk.ainet.app.notebook

// === Execution contexts ======================================================
typealias ExecutionContext = sk.ainet.context.ExecutionContext
typealias DirectCpuExecutionContext = sk.ainet.context.DirectCpuExecutionContext

// === Tensor core =============================================================
typealias Shape = sk.ainet.lang.tensor.Shape
typealias Tensor<T, V> = sk.ainet.lang.tensor.Tensor<T, V>
typealias DenseTensorDataFactory = sk.ainet.lang.tensor.data.DenseTensorDataFactory

// === Neural-network DSL ======================================================
typealias Module<T, V> = sk.ainet.lang.nn.Module<T, V>
/** Short alias for the neural-network [Module] type — convenient in notebook cells. */
typealias Net<T, V> = sk.ainet.lang.nn.Module<T, V>

// === DType markers ===========================================================
// These are singleton objects used as phantom type parameters in the tensor DSL
// (e.g. `tensor<FP32, Float> { ... }`). Aliasing them avoids long FQNs in cells.
typealias DType = sk.ainet.lang.types.DType
typealias FP16 = sk.ainet.lang.types.FP16
typealias FP32 = sk.ainet.lang.types.FP32
typealias FP64 = sk.ainet.lang.types.FP64
typealias Int4 = sk.ainet.lang.types.Int4
typealias Int8 = sk.ainet.lang.types.Int8
typealias Int16 = sk.ainet.lang.types.Int16
typealias Int32 = sk.ainet.lang.types.Int32
typealias Int64 = sk.ainet.lang.types.Int64
typealias UInt8 = sk.ainet.lang.types.UInt8
typealias UInt16 = sk.ainet.lang.types.UInt16
typealias UInt32 = sk.ainet.lang.types.UInt32
typealias UInt64 = sk.ainet.lang.types.UInt64
