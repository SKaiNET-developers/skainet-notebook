package sk.ainet.app.notebook

import org.jetbrains.kotlinx.jupyter.api.libraries.*
import sk.ainet.lang.tensor.Tensor
import sk.ainet.app.notebook.display.display

internal class SKaiNETIntegration : JupyterIntegration() {
    override fun Builder.onLoaded() {
        /*
        import("sk.ainet.app.notebook.*")
        import("sk.ainet.app.notebook.display.*")
        import("sk.ainet.lang.tensor.*")
        import("sk.ainet.lang.tensor.dsl.*")
        import("sk.ainet.lang.ops.*")
        import("sk.ainet.lang.nn.*")
        import("sk.ainet.lang.types.*")
        import("sk.ainet.context.*")
        import("sk.ainet.execute.context.*")

         */

        render<Tensor<*, *>> { tensor ->
            display(tensor)
        }

        onLoaded {
            println("SKaiNET Integration Loaded")
        }
    }
}
