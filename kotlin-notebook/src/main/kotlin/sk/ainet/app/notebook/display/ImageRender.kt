package sk.ainet.app.notebook.display

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Core rendering utilities for inline HTML image display in Kotlin Notebooks.
 *
 * The APIs here avoid hard dependencies on the Kotlin Notebook runtime by using
 * reflection for HTML(html).display() when available, and falling back to printing
 * the HTML string otherwise. This keeps the module light-weight while working
 * seamlessly inside notebooks.
 */

/**
 * Encode this BufferedImage to a Base64 string. PNG is used by default.
 */
fun BufferedImage.toBase64(format: String = "png"): String {
    val baos = ByteArrayOutputStream()
    check(ImageIO.write(this, format, baos)) { "ImageIO could not write image using format=$format" }
    val bytes = baos.toByteArray()
    return Base64.getEncoder().encodeToString(bytes)
}

/**
 * Decode bytes into a BufferedImage using ImageIO.
 */
fun bytesToImage(bytes: ByteArray): BufferedImage {
    val img = ImageIO.read(ByteArrayInputStream(bytes))
        ?: throw IllegalArgumentException("Unable to decode image from provided bytes")
    return img
}

/**
 * Emit raw HTML to the Kotlin Notebook output area, if the rich HTML API is present.
 * Falls back to println when executed outside of a notebook environment.
 */
fun emitHtml(html: String) {
    try {
        // Try org.jetbrains.kotlinx.jupyter.api.HTML(html).display()
        val htmlClass = Class.forName("org.jetbrains.kotlinx.jupyter.api.HTML")
        val ctor = htmlClass.getConstructor(String::class.java)
        val htmlObj = ctor.newInstance(html)

        // Top-level function display(Any) is compiled to DisplayKt class
        val displayHost = runCatching { Class.forName("org.jetbrains.kotlinx.jupyter.api.DisplayKt") }
            .getOrElse { Class.forName("org.jetbrains.kotlinx.jupyter.api.DisplayApiKt") }
        val displayMethod = displayHost.methods.firstOrNull { it.name == "display" && it.parameterTypes.size == 1 }
        if (displayMethod != null) {
            displayMethod.invoke(null, htmlObj)
            return
        }
    } catch (_: Throwable) {
        // ignore and fallback
    }
    // Fallback in non-notebook environments
    println(html)
}

/**
 * Render a BufferedImage as an inline <img> with a data URL source and display it.
 */
fun render(img: BufferedImage, configure: DisplayOptions.() -> Unit = {}) {
    val opts = DisplayOptions().apply(configure)
    val base64 = img.toBase64("png")
    val sb = StringBuilder()
    sb.append("<img src=\"data:image/png;base64,")
        .append(base64)
        .append("\"")

    opts.width?.let { sb.append(" width=\"").append(it).append("\"") }
    opts.height?.let { sb.append(" height=\"").append(it).append("\"") }

    // Build class attribute if provided
    opts.cssClass?.takeIf { it.isNotBlank() }?.let { css ->
        sb.append(" class=\"").append(htmlEscape(css)).append("\"")
    }

    // Build style attribute for border if requested
    if (opts.border) {
        sb.append(" style=\"border:1px solid #ccc;\"")
    }

    // Alt text. Always include alt attribute for accessibility; empty if null
    val altText = opts.alt?.let { htmlEscape(it) } ?: ""
    sb.append(" alt=\"").append(altText).append("\"")
    sb.append("/>")

    emitHtml(sb.toString())
}

private fun htmlEscape(input: String): String = buildString(input.length) {
    input.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}
