package sk.ainet.app.notebook

import org.jetbrains.kotlinx.jupyter.api.libraries.*
import sk.ainet.lang.tensor.Tensor
import sk.ainet.app.notebook.display.display

open class SKaiNETIntegration : JupyterIntegration() {

    init {
        println("[DEBUG_LOG] SKaiNETIntegration classloader: ${this::class.java.classLoader}")
        println("[DEBUG_LOG] JupyterIntegration classloader: ${JupyterIntegration::class.java.classLoader}")
        
        try {
            val libDefClass = Class.forName("org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition")
            println("[DEBUG_LOG] LibraryDefinition classloader: ${libDefClass.classLoader}")
            println("[DEBUG_LOG] LibraryDefinition location: ${libDefClass.protectionDomain.codeSource.location}")
        } catch (e: Exception) {
            println("[DEBUG_LOG] Could not load LibraryDefinition: ${e.message}")
        }

        var cl = this::class.java.classLoader
        while (cl != null) {
            println("[DEBUG_LOG] ClassLoader Hierarchy: $cl")
            cl = cl.parent
        }
    }

    override fun Builder.onLoaded() {
        import("skainet.*")
        import("sk.ainet.app.notebook.*")
        import("sk.ainet.app.notebook.display.*")
        import("sk.ainet.lang.tensor.*")
        import("sk.ainet.lang.tensor.dsl.*")
        import("sk.ainet.lang.ops.*")
        import("sk.ainet.lang.nn.*")
        import("sk.ainet.lang.types.*")
        import("sk.ainet.context.*")
        import("sk.ainet.execute.context.*")

        render<Tensor<*, *>> { tensor ->
            display(tensor)
        }

        onShutdown {
            println("Bye! (2)")
        }

        onLoaded {
            println("SKaiNET Integration Loaded")
        }
    }
}
