package sk.ainet.app.notebook.display

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
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
 * Decode an image from a filesystem [Path]. Validates existence and readability.
 */
fun pathToImage(path: Path): BufferedImage {
    require(Files.exists(path)) { "File not found: $path" }
    require(Files.isRegularFile(path)) { "Not a regular file: $path" }
    Files.newInputStream(path).use { input ->
        val img = ImageIO.read(input)
        if (img != null) return img
    }
    throw IllegalArgumentException("Unable to read image from path: $path")
}

/**
 * Decode an image from a filesystem path provided as [String].
 */
fun stringToImage(path: String): BufferedImage = pathToImage(Path.of(path))

/**
 * Decode an image from a [URL].
 */
fun urlToImage(url: URL): BufferedImage {
    url.openStream().use { input ->
        val img = ImageIO.read(input)
        if (img != null) return img
    }
    throw IllegalArgumentException("Unable to read image from URL: $url")
}

/**
 * Convert supported input sources into a [BufferedImage].
 * Supported types: BufferedImage, ByteArray, String (filesystem path), Path, URL.
 * Throws [IllegalArgumentException] for unsupported types.
 */
fun toImage(input: Any): BufferedImage = when (input) {
    is BufferedImage -> input
    is ByteArray -> bytesToImage(input)
    is String -> stringToImage(input)
    is Path -> pathToImage(input)
    is URL -> urlToImage(input)
    else -> throw IllegalArgumentException(
        "Unsupported image input type: ${input::class.java.name}. Supported types: BufferedImage, ByteArray, String (path), java.nio.file.Path, java.net.URL"
    )
}

/**
 * Public API: Display an image from multiple possible input types.
 * Supported inputs are the same as [toImage]: BufferedImage, ByteArray, String (path), Path, URL.
 * Use [configure] to adjust rendering options via [DisplayOptions].
 */
fun display(image: Any, configure: DisplayOptions.() -> Unit = {}) {
    val img = toImage(image)
    render(img, configure)
}

/** Convenience overload: display a [BufferedImage] without additional options. */
fun display(img: BufferedImage) = render(img)

/** Convenience overload: display from raw [ByteArray] image data. */
fun display(bytes: ByteArray) = render(bytesToImage(bytes))

/** Convenience overload: display from filesystem path provided as [String]. */
fun display(path: String) = render(stringToImage(path))

/** Convenience overload: display from a [URL]. */
fun display(url: URL) = render(urlToImage(url))

/**
 * Convenience overload: display multiple images in a responsive grid.
 */
fun display(images: List<BufferedImage>) {
    renderGrid(images)
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
    val html = buildImgTag(img, opts)
    emitHtml(html)
}

/**
 * Render multiple images in a responsive grid using CSS flex layout.
 * Images inherit the same options and are rendered as individual <img> tags.
 */
fun renderGrid(images: List<BufferedImage>, configure: DisplayOptions.() -> Unit = {}) {
    if (images.isEmpty()) {
        emitHtml("<div></div>")
        return
    }
    val opts = DisplayOptions().apply(configure)
    val sb = StringBuilder()
    sb.append("<div style=\"display:flex;flex-wrap:wrap;gap:8px;align-items:flex-start;\">")
    images.forEach { img ->
        sb.append(buildImgTag(img, opts))
    }
    sb.append("</div>")
    emitHtml(sb.toString())
}

private fun buildImgTag(img: BufferedImage, opts: DisplayOptions): String {
    val base64 = img.toBase64("png")
    val sb = StringBuilder()
    sb.append("<img src=\"data:image/png;base64,")
        .append(base64)
        .append("\"")

    opts.width?.let { sb.append(" width=\"").append(it).append("\"") }
    opts.height?.let { sb.append(" height=\"").append(it).append("\"") }

    // Build class attribute if provided (applied per image)
    opts.cssClass?.takeIf { it.isNotBlank() }?.let { css ->
        sb.append(" class=\"").append(htmlEscape(css)).append("\"")
    }

    // Build style attribute for border if requested (applied per image)
    if (opts.border) {
        sb.append(" style=\"border:1px solid #ccc;\"")
    }

    // Alt text per image. Always include alt attribute for accessibility; empty if null
    val altText = opts.alt?.let { htmlEscape(it) } ?: ""
    sb.append(" alt=\"").append(altText).append("\"")
    sb.append("/>")
    return sb.toString()
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
