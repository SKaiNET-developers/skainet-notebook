package sk.ainet.app.notebook.integration

import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import sk.ainet.app.notebook.NotebookInfo
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

        onLoaded {
            display(HTML("<i>${NotebookInfo.NAME} v${NotebookInfo.VERSION} ready</i>"), null)
        }
    }

    private fun escape(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(ch)
        }
    }
}
