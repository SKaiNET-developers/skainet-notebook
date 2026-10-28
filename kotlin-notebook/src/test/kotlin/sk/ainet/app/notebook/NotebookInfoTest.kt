package sk.ainet.app.notebook

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotebookInfoTest {

    @AfterTest
    fun clearVectorEnabledOverride() {
        System.clearProperty("skainet.cpu.vector.enabled")
    }

    @Test
    fun checkSimd_reports_active_path_under_test_jvm() {
        // The Gradle `test` task starts the JVM with
        // `--add-modules jdk.incubator.vector`, JDK 21+, and no
        // skainet.cpu.vector.enabled override. All three preconditions
        // SKaiNET's CPU backend probes should be true here — if any of
        // them flip, the SIMD path would silently regress in production
        // notebooks too.
        val report = checkSimd()
        assertTrue(report.jdkOk, "Test JVM toolchain should be JDK 21+ (got ${report.jdkFeatureVersion})")
        assertTrue(
            report.vectorApiAvailable,
            "Vector API classes should resolve under --add-modules jdk.incubator.vector. " +
                "If this fails, check tasks.test { jvmArgs(...) } in build.gradle.kts.",
        )
        assertTrue(report.configEnabled, "skainet.cpu.vector.enabled defaults to true")
        assertTrue(report.simdActive, "SIMD should be active when all preconditions hold; got reason=${report.reason}")
    }

    @Test
    fun checkSimd_honors_skainet_cpu_vector_enabled_kill_switch() {
        // Mirrors SKaiNET's JvmCpuBackendConfig contract — flipping the
        // sysprop to false must take the report out of the active path
        // even though the Vector API is otherwise reachable.
        System.setProperty("skainet.cpu.vector.enabled", "false")
        val report = checkSimd()
        assertFalse(report.configEnabled)
        assertFalse(report.simdActive)
        assertTrue(
            report.reason.contains("disabled", ignoreCase = true),
            "Reason should call out the kill-switch; got: ${report.reason}",
        )
    }

    @Test
    fun checkSimd_jdkFeatureVersion_is_populated() {
        val v = checkSimd().jdkFeatureVersion
        assertNotNull(v, "Runtime.version().feature() should be available on JDK 9+")
        assertTrue(v >= 21, "Test JVM is expected to be JDK 21+; got $v")
    }

    @Test
    fun notebook_info_checkSimd_forwarder_matches_top_level() {
        // NotebookInfo.checkSimd() should be a thin forwarder; future
        // refactors must not let the two callsites diverge silently.
        val viaObject = NotebookInfo.checkSimd()
        val viaTopLevel = checkSimd()
        assertEquals(viaTopLevel, viaObject)
    }
}
