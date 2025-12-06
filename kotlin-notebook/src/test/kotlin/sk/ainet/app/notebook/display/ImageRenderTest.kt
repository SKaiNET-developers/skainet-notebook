package sk.ainet.app.notebook.display

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.awt.Color
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageRenderTest {

    private fun makeTestImage(w: Int = 8, h: Int = 6, withAlpha: Boolean = true): BufferedImage {
        val type = if (withAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val img = BufferedImage(w, h, type)
        val g = img.createGraphics()
        try {
            g.color = Color.RED
            g.fillRect(0, 0, w, h)
            g.color = Color.BLUE
            g.drawLine(0, 0, w - 1, h - 1)
        } finally {
            g.dispose()
        }
        return img
    }

    @Test
    fun toBase64_and_decode_roundtrip() {
        val img = makeTestImage()
        val b64 = img.toBase64()
        assertTrue(b64.isNotBlank(), "base64 should not be blank")

        // decode back via ImageIO
        val bytes = java.util.Base64.getDecoder().decode(b64)
        val decoded = bytesToImage(bytes)
        assertEquals(img.width, decoded.width)
        assertEquals(img.height, decoded.height)
    }

    @Test
    fun bytesToImage_valid_and_invalid() {
        val img = makeTestImage(4, 4)
        val baos = ByteArrayOutputStream()
        assertTrue(ImageIO.write(img, "png", baos))
        val ok = bytesToImage(baos.toByteArray())
        assertEquals(4, ok.width)
        assertEquals(4, ok.height)

        assertFailsWith<IllegalArgumentException> {
            bytesToImage(byteArrayOf(1, 2, 3, 4))
        }
    }

    @Test
    fun pathToImage_valid_and_missing() {
        val img = makeTestImage(5, 7)
        val tmp = Files.createTempFile("img-render-test", ".png")
        try {
            ImageIO.write(img, "png", tmp.toFile())
            val loaded = pathToImage(tmp)
            assertEquals(5, loaded.width)
            assertEquals(7, loaded.height)
        } finally {
            Files.deleteIfExists(tmp)
        }

        val missing = tmp.resolveSibling("does-not-exist-${System.nanoTime()}.png")
        assertFailsWith<IllegalArgumentException> {
            pathToImage(missing)
        }
    }

    private var server: HttpServer? = null

    @BeforeTest
    fun startServer() {
        val http = HttpServer.create(InetSocketAddress(0), 0)
        val exec = Executors.newSingleThreadExecutor()
        http.executor = exec

        // prepare tiny PNG payload
        val img = makeTestImage(3, 2)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
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
    fun urlToImage_via_embedded_http() {
        val port = (server?.address?.port ?: error("server not started"))
        val url = URL("http://127.0.0.1:$port/img")
        val loaded = urlToImage(url)
        assertEquals(3, loaded.width)
        assertEquals(2, loaded.height)
    }

    @Test
    fun render_emits_expected_html() {
        val img = makeTestImage(10, 10)
        val out = ByteArrayOutputStream()
        val prev = System.out
        try {
            System.setOut(java.io.PrintStream(out, true, Charsets.UTF_8))
            render(img) {
                width = 100
                height = 80
                alt = "<tag> & sample"
                border = true
                cssClass = "thumb"
            }
        } finally {
            System.setOut(prev)
        }

        val html = out.toString(Charsets.UTF_8)
        // Basic structure and attributes
        assertContains(html, "<img ")
        assertContains(html, "width=\"100\"")
        assertContains(html, "height=\"80\"")
        assertContains(html, "class=\"thumb\"")
        assertContains(html, "style=\"border:1px solid #ccc;\"")
        assertContains(html, "alt=\"&lt;tag&gt; &amp; sample\"") // escaped
        assertContains(html, "src=\"data:image/png;base64,")
    }

    @Test
    fun renderGrid_wraps_images_in_flex_container() {
        val imgs = listOf(makeTestImage(4, 4), makeTestImage(5, 5))
        val out = ByteArrayOutputStream()
        val prev = System.out
        try {
            System.setOut(java.io.PrintStream(out, true, Charsets.UTF_8))
            renderGrid(imgs) {
                width = 50
            }
        } finally {
            System.setOut(prev)
        }
        val html = out.toString(Charsets.UTF_8)
        assertContains(html, "<div style=\"display:flex;flex-wrap:wrap;gap:8px;align-items:flex-start;\">")
        // two image tags present with width attribute
        assertTrue(Regex("<img ").findAll(html).count() >= 2)
        assertContains(html, "width=\"50\"")
        assertContains(html, "</div>")
    }

    @Test
    fun performance_encode_under_reasonable_threshold() {
        val img = makeTestImage(512, 512, withAlpha = false)
        val start = System.nanoTime()
        repeat(5) { img.toBase64() } // warmup
        val t0 = System.nanoTime()
        img.toBase64()
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        println("[DEBUG_LOG] toBase64(512x512) took ${"%.2f".format(ms)} ms")
        // Use a loose threshold to avoid CI flakiness while still checking perf is reasonable.
        assertTrue(ms < 250.0, "Encoding should be reasonably fast, took ${ms} ms")
        assertTrue(System.nanoTime() - start > 0) // use variables to avoid dead code elimination
    }
}
