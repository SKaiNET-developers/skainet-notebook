package sk.ainet.app.notebook.display

import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult

/**
 * Render Graphviz DOT graphs as a cell result.
 *
 * The DOT → SVG conversion runs **entirely on the JVM kernel** via
 * [GraphvizWasm], which executes a bundled Graphviz wasm artifact on the
 * [chasm](https://github.com/CharlieTap/chasm) WebAssembly engine. Cell
 * outputs are finished SVG markup — no JS executes in the notebook frontend,
 * no CDN is contacted, no browser-side wasm engine is required.
 *
 * The public surface here (`Dot`, `DotEngine`, `DotOptions`, [renderDot])
 * deliberately mentions nothing about wasm. That implementation detail can
 * change — and will — when the follow-up PR replaces the current scaffold of
 * [GraphvizWasm] with the real wasm wiring.
 */

/** Layout engines exposed by Graphviz. */
enum class DotEngine {
    DOT,
    NEATO,
    TWOPI,
    CIRCO,
    FDP,
    OSAGE,
    PATCHWORK,
}

/** Configuration for [renderDot]. */
data class DotOptions(
    /** Layout engine. Defaults to the standard hierarchical `dot` layout. */
    var engine: DotEngine = DotEngine.DOT,
    /** Optional CSS width applied to the SVG container. */
    var width: String? = null,
    /** Optional CSS max-height; useful for very tall graphs in a notebook flow. */
    var maxHeight: String? = null,
)

/**
 * A DOT source string. Return one from a cell to have it rendered as SVG.
 *
 * ```
 * Dot("digraph G { A -> B -> C }")
 * ```
 *
 * Use [asDot] for terser call sites: `"digraph G { A -> B }".asDot()`.
 */
@JvmInline
value class Dot(val source: String)

/** Wrap a DOT string for the cell-result renderer. */
fun String.asDot(): Dot = Dot(this)

/**
 * Render a [Dot] graph as an HTML mime result containing the SVG inline.
 *
 * Throws [GraphvizException] for runtime errors surfaced by the renderer
 * (malformed DOT, layout failure). Throws [GraphvizNotBundledException] on
 * the scaffold branch — until the follow-up PR bundles the wasm artifact.
 */
fun renderDot(dot: Dot, configure: DotOptions.() -> Unit = {}): MimeTypedResult {
    val opts = DotOptions().apply(configure)
    val svg = GraphvizWasm.render(dot.source, opts.engine)
    return HTML(wrapSvg(svg, opts))
}

/** Convenience overload so notebook authors can pass the source directly. */
fun renderDot(source: String, configure: DotOptions.() -> Unit = {}): MimeTypedResult =
    renderDot(Dot(source), configure)

/**
 * Wrap the rendered SVG in a sizing `<div>` if any container styling was
 * requested; otherwise return the SVG as-is. Done in Kotlin (not via wasm)
 * because container styling is purely a notebook-layout concern.
 */
private fun wrapSvg(svg: String, opts: DotOptions): String {
    val width = opts.width
    val maxHeight = opts.maxHeight
    if (width == null && maxHeight == null) return svg

    val style = buildString {
        append("display:block;")
        width?.let { append("width:").append(it).append(';') }
        maxHeight?.let { append("max-height:").append(it).append(";overflow:auto;") }
    }
    return "<div style=\"$style\">$svg</div>"
}
