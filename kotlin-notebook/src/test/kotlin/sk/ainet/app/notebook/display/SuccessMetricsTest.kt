package sk.ainet.app.notebook.display

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SuccessMetricsTest {

    private fun img(w: Int = 6, h: Int = 4): BufferedImage {
        val bi = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = bi.createGraphics()
        try {
            g.color = java.awt.Color.GREEN
            g.fillRect(0, 0, w, h)
        } finally { g.dispose() }
        return bi
    }

    private var server: HttpServer? = null

    @BeforeTest
    fun startServer() {
        val http = HttpServer.create(InetSocketAddress(0), 0)
        val exec = Executors.newSingleThreadExecutor()
        http.executor = exec

        val baos = ByteArrayOutputStream()
        ImageIO.write(img(3, 2), "png", baos)
        val payload = baos.toByteArray()

        http.createContext("/img", HttpHandler { ex: HttpExchange ->
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        })
        http.start()
        server = http
    }

    @AfterTest
    fun stopServer() {
        server?.stop(0)
        (server?.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
    }

    @Test
    fun oneLiners_work_for_supported_inputs() {
        val results = mutableListOf<Any>()

        results += display(img())

        val pngBytes = ByteArrayOutputStream().also { ImageIO.write(img(), "png", it) }.toByteArray()
        results += display(pngBytes)

        val tmp = Files.createTempFile("success-metrics", ".png")
        try {
            ImageIO.write(img(), "png", tmp.toFile())
            results += display(tmp.toString())
        } finally { Files.deleteIfExists(tmp) }

        val port = server?.address?.port ?: error("server not started")
        results += display(URL("http://127.0.0.1:$port/img"))

        val combined = results.joinToString("\n") { r ->
            (r as? Map<*, *>)?.get("text/html")?.toString() ?: r.toString()
        }
        assertContains(combined, "<img ")
        assertContains(combined, "src=\"data:image/png;base64,")
    }

    @Test
    fun no_swing_or_awt_windows_are_shown() {
        render(img())

        val frames = Frame.getFrames()
        val anyVisible = frames.any { it.isDisplayable && it.isVisible }
        assertTrue(!anyVisible, "No Swing/AWT windows should be visible during image display")
    }
}
