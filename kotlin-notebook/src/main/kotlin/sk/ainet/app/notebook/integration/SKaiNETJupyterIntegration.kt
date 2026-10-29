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
            // NN DSL: top-level `sequential<T, V>` builder (members like
            // `input`, `dense`, `activation` resolve via the lambda receiver).
            "sk.ainet.lang.nn.dsl.*",
            // DAG DSL: `dag { input / parameter / constant / conv2d / relu / output }`
            "sk.ainet.lang.dag.*",
            // `TensorSpec(name, shape, dtype)` referenced from `input(...)`.
            "sk.ainet.lang.tensor.ops.*",
            // `Tensor`, `Shape`, `pprint`, `relu`, and the tensor-op extensions
            "sk.ainet.lang.tensor.*",
            // Tensor builder DSL: `tensor<FP32, Float> { shape(...) { ... } }`
            "sk.ainet.lang.tensor.dsl.*",
            // Dtype markers: `FP32`, `FP16`, `Int8`, ...
            "sk.ainet.lang.types.*",
            // Data sources: `JvmDataSourceResolver`, `DataSourceRequest`,
            // dataset helpers
            "sk.ainet.data.*",
            "sk.ainet.data.common.*",
            "sk.ainet.data.source.*",
            // Frontends: `TextFrontEnds`, tokenizers, text/image pipelines
            "sk.ainet.frontend.*",
            "sk.ainet.frontend.tokenizer.*",
            "sk.ainet.frontend.text.*",
            // Model I/O: `ModelSource`, format loaders for GGUF / ONNX /
            // SafeTensors, weight utilities
            "sk.ainet.io.*",
            "sk.ainet.io.model.*",
            "sk.ainet.io.gguf.*",
            "sk.ainet.io.onnx.*",
            "sk.ainet.io.safetensors.*",
            "sk.ainet.io.weights.*",
            // sk.ainet.io.gguf and sk.ainet.io.safetensors both declare an
            // identical top-level `createRandomAccessSource(String)` (same
            // body, same return type sk.ainet.io.RandomAccessSource), so the
            // two star imports above make any unqualified call ambiguous. A
            // NAMED import outranks star imports in Kotlin resolution, so this
            // line picks a deterministic winner; which one wins is irrelevant
            // as the functions are byte-identical.
            "sk.ainet.io.safetensors.createRandomAccessSource",
            // Notebook-side helpers: display, tensor→image tools
            "sk.ainet.app.notebook.*",
            "sk.ainet.app.notebook.display.*",
            "sk.ainet.app.notebook.tools.*",
            // NOTE for maintainers: before adding a package here, audit the
            // shaded jar against this whole list for TWO kinds of collisions:
            //  1. duplicate top-level CLASS names — the K2 REPL silently drops
            //     such a name in later cells ("Unresolved reference", not an
            //     ambiguity error); this is why NotebookAliases.kt was removed
            //     in 0.62.8.
            //  2. duplicate top-level FUNCTIONS with the same signature and no
            //     distinguishing receiver — unqualified calls become
            //     "Overload resolution ambiguity" (0.62.9:
            //     createRandomAccessSource, resolved by the named import
            //     above). Same-named EXTENSION functions on different receiver
            //     types (relu, gelu, planInput, ...) are fine.
            // Audit: unzip the shadow jar, `javap -public` the top-level
            // classes of every package in this list, compare simple names.
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
