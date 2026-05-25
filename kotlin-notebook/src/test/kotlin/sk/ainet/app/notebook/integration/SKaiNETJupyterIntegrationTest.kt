package sk.ainet.app.notebook.integration

import sk.ainet.app.notebook.NotebookInfo
import sk.ainet.app.notebook.display.toBase64
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Static checks on the Jupyter integration.
 *
 * The full functional test — "does a live kernel wire up the auto-imports and
 * renderers?" — requires a real Jupyter REPL and is covered by manual smoke
 * testing of the tutorial notebooks. Here we verify the pieces that *can* fail
 * silently at the build level:
 *
 *  - The integration class is on the classpath and instantiable.
 *  - The Jupyter metadata resource `META-INF/kotlin-jupyter-libraries/libraries.json`
 *    is generated and points at our producer FQN.
 */
class SKaiNETJupyterIntegrationTest {

    @Test
    fun integration_class_instantiates() {
        // Catches accidental removal of the zero-arg constructor that
        // JupyterIntegration requires for classpath discovery.
        val integration = SKaiNETJupyterIntegration()
        assertNotNull(integration)
    }

    @Test
    fun metadata_resource_registers_producer() {
        val resource = this::class.java.classLoader
            .getResource("META-INF/kotlin-jupyter-libraries/libraries.json")
        assertNotNull(
            resource,
            "Kotlin-Jupyter metadata resource missing. The `jupyter-api` Gradle " +
                "plugin should generate it — check `processJupyterApiResources { libraryProducers = ... }`.",
        )
        val json = resource.readText()
        val expectedFqn = SKaiNETJupyterIntegration::class.qualifiedName
        assertNotNull(expectedFqn)
        assertContains(
            json,
            expectedFqn,
            message = "libraries.json should register the integration by FQN. Got: $json",
        )
    }

    @Test
    fun buffered_image_can_be_base64_encoded() {
        // The BufferedImage renderer delegates to BufferedImage.toBase64().
        // If someone drops the extension, the renderer will break silently.
        val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        val encoded = img.toBase64()
        assertTrue(encoded.isNotBlank(), "base64 PNG should be non-empty")
    }

    @Test
    fun notebook_info_exposes_current_version() {
        // Catches a forgotten version bump when upgrading to a new SKaiNET release.
        assertEquals("0.25.1", NotebookInfo.VERSION)
    }
}
