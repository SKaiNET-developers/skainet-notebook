package sk.ainet.app.notebook.display

import java.awt.Graphics2D
import java.awt.RenderingHints
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
    checkJvm11()
    val t0 = System.nanoTime()
    val (html, meta) = buildImgTagWithMeta(img, opts)
    emitHtml(html)
    val t1 = System.nanoTime()
    if (opts.measureTime) {
        val ms = (t1 - t0) / 1_000_000.0
        println("[DEBUG_LOG] Image encode+render: ${"%.2f".format(ms)} ms (" +
                "${meta.origW}x${meta.origH} -> ${meta.outW}x${meta.outH}, cacheHit=${meta.cacheHit})")
    }
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
    checkJvm11()
    val sb = StringBuilder()
    sb.append("<div style=\"display:flex;flex-wrap:wrap;gap:8px;align-items:flex-start;\">")
    images.forEach { img ->
        val t0 = if (opts.measureTime) System.nanoTime() else 0L
        val (tag, meta) = buildImgTagWithMeta(img, opts)
        sb.append(tag)
        if (opts.measureTime) {
            val t1 = System.nanoTime()
            val ms = (t1 - t0) / 1_000_000.0
            println("[DEBUG_LOG] Grid item encode: ${"%.2f".format(ms)} ms (" +
                    "${meta.origW}x${meta.origH} -> ${meta.outW}x${meta.outH}, cacheHit=${meta.cacheHit})")
        }
    }
    sb.append("</div>")
    emitHtml(sb.toString())
}

private data class BuildMeta(val cacheHit: Boolean, val origW: Int, val origH: Int, val outW: Int, val outH: Int)

private fun buildImgTagWithMeta(img: BufferedImage, opts: DisplayOptions): Pair<String, BuildMeta> {
    val origW = img.width
    val origH = img.height
    val processed = maybeDownscale(img, opts)
    val outW = processed.width
    val outH = processed.height

    val (base64, hit) = encodeBase64Cached(processed, opts)

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

    // Alt text per image. Always include alt attribute for accessibility
    val altDefault = "image ${outW}x${outH}"
    val altText = opts.alt?.takeIf { it.isNotBlank() }?.let { htmlEscape(it) } ?: htmlEscape(altDefault)
    sb.append(" alt=\"").append(altText).append("\"")
    sb.append("/>")
    return sb.toString() to BuildMeta(hit, origW, origH, outW, outH)
}

private fun maybeDownscale(img: BufferedImage, opts: DisplayOptions): BufferedImage {
    var scale = 1.0
    opts.autoDownscaleMaxWidth?.let { maxW ->
        if (img.width > maxW) scale = minOf(scale, maxW.toDouble() / img.width)
    }
    opts.autoDownscaleMaxHeight?.let { maxH ->
        if (img.height > maxH) scale = minOf(scale, maxH.toDouble() / img.height)
    }
    opts.autoDownscaleMaxPixels?.let { maxPx ->
        val curPx = img.width.toLong() * img.height.toLong()
        if (curPx > maxPx) {
            val factor = kotlin.math.sqrt(maxPx.toDouble() / curPx.toDouble())
            scale = minOf(scale, factor)
        }
    }
    if (scale >= 1.0) return img
    val newW = maxOf(1, (img.width * scale).toInt())
    val newH = maxOf(1, (img.height * scale).toInt())
    val type = if (img.transparency == java.awt.Transparency.OPAQUE) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
    val out = BufferedImage(newW, newH, type)
    val g2 = out.createGraphics()
    try {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.drawImage(img, 0, 0, newW, newH, null)
    } finally {
        g2.dispose()
    }
    return out
}

private fun encodeBase64Cached(img: BufferedImage, opts: DisplayOptions): Pair<String, Boolean> {
    if (!opts.enableCache) {
        return img.toBase64("png") to false
    }
    val keyPart = opts.cacheKey ?: (System.identityHashCode(img).toString())
    val key = "png:${keyPart}:${img.width}x${img.height}"
    Base64Cache.get(key)?.let { return it to true }
    val encoded = img.toBase64("png")
    Base64Cache.put(key, encoded)
    return encoded to false
}

private object Base64Cache {
    private const val MAX = 64
    private val map = object : LinkedHashMap<String, String>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX
    }
    @Synchronized fun get(key: String): String? = map[key]
    @Synchronized fun put(key: String, value: String) { map[key] = value }
}

private fun checkJvm11() {
    try {
        val feature = Runtime.version().feature()
        if (feature < 11) {
            println("[DEBUG_LOG] Warning: Kotlin Notebook image display targets JVM 11+, current=${feature}")
        }
    } catch (_: Throwable) {
        // Ignore if Runtime.version is unavailable (very old JDKs)
    }
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
