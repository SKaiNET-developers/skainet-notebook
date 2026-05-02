@file:JvmName("SKaiNETNotebook")

package sk.ainet.app.notebook

/**
 * SKaiNET Kotlin Notebook Library
 *
 * This library provides convenient access to SKaiNET functionality for Kotlin Notebooks.
 * It aggregates the core SKaiNET libraries for easy consumption in Jupyter environments.
 */

object NotebookInfo {
    const val VERSION = "0.22.1"
    const val NAME = "SKaiNET Kotlin Notebook"

    fun info() {
        println("$NAME v$VERSION")
        println("Deep learning framework for Kotlin Notebooks")
    }

    fun checkSimd(): SimdReport = sk.ainet.app.notebook.checkSimd()
}

/**
 * Snapshot of whether SKaiNET's SIMD-accelerated CPU backend is reachable
 * from the current JVM.
 *
 * @property jdkFeatureVersion The detected JDK feature version (e.g. 21), or
 *   `null` if it could not be determined.
 * @property jdkOk True iff [jdkFeatureVersion] >= 21.
 * @property vectorApiAvailable True iff `jdk.incubator.vector.FloatVector` and
 *   `jdk.incubator.vector.VectorSpecies` resolve via [Class.forName]. Requires
 *   the JVM to have been started with `--add-modules jdk.incubator.vector`.
 * @property configEnabled True iff neither the system property
 *   `skainet.cpu.vector.enabled` nor the env var `SKAINET_CPU_VECTOR_ENABLED`
 *   is set to `false`. Defaults to true.
 * @property simdActive [jdkOk] && [vectorApiAvailable] && [configEnabled].
 *   When false, SKaiNET's CPU backend silently falls back to the scalar
 *   `DefaultCpuOps` implementation.
 * @property reason Human-readable explanation of [simdActive]. Stable enough
 *   to log; not stable enough to pattern-match on.
 */
data class SimdReport(
    val jdkFeatureVersion: Int?,
    val jdkOk: Boolean,
    val vectorApiAvailable: Boolean,
    val configEnabled: Boolean,
    val simdActive: Boolean,
    val reason: String,
)

/**
 * Probes the current JVM for the three preconditions SKaiNET's CPU backend
 * checks at startup before installing the Vector-API-accelerated kernels:
 *
 *   1. JDK feature version >= 21.
 *   2. The `jdk.incubator.vector` module is in the runtime module graph
 *      (the JVM was started with `--add-modules jdk.incubator.vector`).
 *   3. The runtime kill-switch (`-Dskainet.cpu.vector.enabled` /
 *      `SKAINET_CPU_VECTOR_ENABLED`) is not set to false.
 *
 * Mirrors the logic in `sk.ainet.exec.tensor.ops.PlatformCpuOpsFactory.jvm.kt`
 * and `JvmCpuBackendConfig` (which are `internal` upstream and therefore not
 * directly callable). If those drift, this probe drifts with them — keep them
 * in sync at SKaiNET version bumps.
 */
fun checkSimd(): SimdReport {
    val jdkFeature = runCatching { Runtime.version().feature() }.getOrNull()
    val jdkOk = (jdkFeature ?: 0) >= 21

    val vectorOk = runCatching {
        Class.forName("jdk.incubator.vector.FloatVector")
        Class.forName("jdk.incubator.vector.VectorSpecies")
        true
    }.getOrElse { false }

    val configEnabled = readVectorConfigFlag()

    val active = jdkOk && vectorOk && configEnabled
    val reason = when {
        active -> "Vector API kernels active"
        !jdkOk -> "JDK 21+ required (running on ${jdkFeature ?: "unknown"})"
        !vectorOk -> "jdk.incubator.vector module not loaded — start the kernel with --add-modules jdk.incubator.vector"
        !configEnabled -> "disabled by skainet.cpu.vector.enabled / SKAINET_CPU_VECTOR_ENABLED"
        else -> "unknown"
    }
    return SimdReport(
        jdkFeatureVersion = jdkFeature,
        jdkOk = jdkOk,
        vectorApiAvailable = vectorOk,
        configEnabled = configEnabled,
        simdActive = active,
        reason = reason,
    )
}

private fun readVectorConfigFlag(): Boolean {
    val sysProp = runCatching { System.getProperty("skainet.cpu.vector.enabled") }
        .getOrNull()?.toBooleanStrictOrNull()
    val env = runCatching { System.getenv("SKAINET_CPU_VECTOR_ENABLED") }
        .getOrNull()?.toBooleanStrictOrNull()
    return sysProp ?: env ?: true
}
