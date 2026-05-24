package sk.ainet.app.notebook.integration

import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import sk.ainet.app.notebook.NotebookInfo
import sk.ainet.app.notebook.checkSimd
import sk.ainet.app.notebook.display.Dot
import sk.ainet.app.notebook.display.renderDot
import sk.ainet.app.notebook.display.toBase64
import sk.ainet.lang.tensor.Tensor
import sk.ainet.lang.tensor.pprint
import java.awt.image.BufferedImage

/**
 * Kotlin Jupyter integration for SKaiNET.
 *
 * When `sk.ainet.app:kotlin-notebook` is on the notebook classpath, the
 * Jupyter kernel discovers this class via `META-INF/kotlin-jupyter-libraries`
 * and runs [onLoaded] once. That provides:
 *
 *  - Default imports for the hot SKaiNET packages so users don't have to
 *    re-type `import sk.ainet.context.*` in every notebook.
 *  - Renderers for [Tensor] and [BufferedImage] so returning one from a cell
 *    displays inline instead of calling `toString()`.
 */
class SKaiNETJupyterIntegration : JupyterIntegration() {

    override fun Builder.onLoaded() {
        import(
            // Execution contexts + the `data { }` DSL
            "sk.ainet.context.*",
            // `computation<T>(ctx) { ... }`
            "sk.ainet.execute.context.*",
            // `definition { network { dense / input / ... } }`, `Module`
            "sk.ainet.lang.nn.*",
            // `Tensor`, `Shape`, `pprint`, `relu`, and the tensor-op extensions
            "sk.ainet.lang.tensor.*",
            // Tensor builder DSL: `tensor<FP32, Float> { shape(...) { ... } }`
            "sk.ainet.lang.tensor.dsl.*",
            // Dtype markers: `FP32`, `FP16`, `Int8`, ...
            "sk.ainet.lang.types.*",
            // Notebook-side helpers: typealiases, display, tensor→image tools
            "sk.ainet.app.notebook.*",
            "sk.ainet.app.notebook.display.*",
            "sk.ainet.app.notebook.tools.*",
        )

        render<Tensor<*, *>> { tensor ->
            HTML("<pre>${escape(tensor.pprint())}</pre>")
        }

        render<BufferedImage> { image ->
            MimeTypedResult(
                mapOf("image/png" to image.toBase64("png")),
            )
        }

        // Return a Dot(...) from a cell to render its DOT source as SVG via
        // the bundled Graphviz wasm executed by chasm on the JVM kernel.
        render<Dot> { dot -> renderDot(dot) }

        onLoaded {
            display(HTML("<i>${NotebookInfo.NAME} v${NotebookInfo.VERSION} ready</i>"), null)
            val simd = checkSimd()
            if (!simd.simdActive) {
                display(HTML(simdWarningHtml(simd.reason)), null)
            }
        }
    }

    // Visible at notebook load when SIMD is unreachable. Distinct color band
    // so it doesn't read like an error — the kernel still works on the scalar
    // fallback, it's just slower for matmul-heavy code.
    private fun simdWarningHtml(reason: String): String =
        """<div style="border-left:3px solid #d97706;padding:8px 12px;margin-top:6px;background:#fef3c7;color:#78350f;font-family:sans-serif">
            ⚠ <b>SKaiNET SIMD path is NOT active</b> — falling back to scalar CPU kernels.<br>
            <span style="font-size:90%">${escape(reason)}<br>
            IntelliJ Kotlin Notebook: Settings → Languages &amp; Frameworks → Kotlin → Kotlin Notebook → JVM options, add <code>--add-modules jdk.incubator.vector</code>.<br>
            Run <code>checkSimd()</code> for the full diagnostic.</span>
        </div>"""

    private fun escape(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(ch)
        }
    }
}
