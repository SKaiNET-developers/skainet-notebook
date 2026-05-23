package sk.ainet.app.notebook.display

/**
 * JVM-side Graphviz renderer backed by an embedded WebAssembly artifact and
 * executed by [chasm](https://github.com/CharlieTap/chasm).
 *
 * ## Goal
 *
 * Render Graphviz DOT graphs to SVG **entirely on the JVM kernel**. Notebook
 * cells receive finished SVG markup; no notebook frontend ever parses DOT,
 * fetches a CDN, or executes JS to produce the picture. A notebook saved with
 * rendered graphs reopens with the same bytes regardless of network or IDE.
 *
 * ## Architecture
 *
 * ```text
 *      String (DOT) ─▶ writeCString ─▶ wasm linear memory
 *                                              │
 *      DotEngine    ─▶ engine ordinal ────────▶│
 *                                              ▼
 *                                       graphviz.wasm
 *                            (exports: gv_render, malloc, free)
 *                                              │
 *      String (SVG) ◀── readCString ◀──────────┘
 * ```
 *
 * The runtime is [chasm](https://github.com/CharlieTap/chasm) (pure-Kotlin
 * Wasm 3.0 engine). Host imports the wasm asks for — `fd_write`, `proc_exit`,
 * `clock_time_get`, `random_get`, etc. — are supplied by
 * [wasi-emscripten-host](https://github.com/illarionov/wasi-emscripten-host)
 * via the `bindings-chasm-wasip1` adapter. Both deps are already wired into
 * `kotlin-notebook/build.gradle.kts`.
 *
 * ## What this scaffold ships
 *
 * The public contract — input shape, return shape, exception type — is the
 * only thing that lands here. The wasm artifact is not yet bundled and the
 * chasm wiring is not yet written; both are tracked as the follow-up to this
 * PR (see `memory/graphviz-wasm-followup.md`).
 *
 * Every public function throws [GraphvizNotBundledException] with a pointer
 * to the follow-up. Tests pin that behavior so the contract can't drift
 * silently while the wasm work is in flight.
 *
 * ## What the follow-up will do
 *
 * 1. Build (or extract) a Graphviz wasm exporting `gv_render(dot_ptr, dot_len,
 *    engine_id) -> svg_ptr`, plus `malloc` / `free` for buffer ownership. The
 *    `wasm-build/` directory at the project root holds the reproducible
 *    Docker-based wasi-sdk toolchain for this; `wasm-build/README.md`
 *    documents the two sourcing strategies (wasi-sdk Graphviz from source vs.
 *    extract-and-shim hpcc-js's emscripten wasm).
 * 2. Drop the resulting `graphviz.wasm` into
 *    `kotlin-notebook/src/main/resources/sk/ainet/app/notebook/wasm/`.
 * 3. Replace each `TODO` here with the chasm call, the WASI host wiring, and
 *    the C-string marshaling.
 * 4. Decide on thread-safety: per-call instance (simplest), or shared instance
 *    behind a mutex (fastest). The contract here doesn't commit either way.
 */
internal object GraphvizWasm {

    /**
     * Render [source] (a valid DOT graph) using [engine] and return the
     * resulting SVG markup. Throws [GraphvizException] for malformed DOT or
     * other runtime errors surfaced by the wasm; throws
     * [GraphvizNotBundledException] until the wasm artifact is bundled in the
     * follow-up PR.
     *
     * The returned string is a complete `<svg ...>...</svg>` document. The
     * caller is responsible for embedding it; this layer does not wrap it in
     * HTML or apply any styling.
     */
    fun render(source: String, engine: DotEngine): String {
        val bytes = loadWasmBytes()
        // The full pipeline below is the contract subsequent PRs implement
        // against. Pre-bundle, each step is a TODO and the first one trips.
        val module = decodeModule(bytes)
        val instance = instantiate(module)
        return try {
            invokeGvRender(instance, source, engine)
        } finally {
            disposeInstance(instance)
        }
    }

    // ---------- private pipeline steps (all stubs until follow-up) ----------

    /**
     * Load the bundled `graphviz.wasm` bytes from the classpath resource at
     * `sk/ainet/app/notebook/wasm/graphviz.wasm`. Cached after the first call;
     * the bytes never change across a kernel session.
     */
    private fun loadWasmBytes(): ByteArray {
        val resource = GraphvizWasm::class.java.getResourceAsStream(WASM_RESOURCE_PATH)
            ?: throw GraphvizNotBundledException(
                "Bundled Graphviz wasm not found at classpath resource $WASM_RESOURCE_PATH. " +
                    "This is expected on the chasm-scaffold branch — the wasm artifact lands " +
                    "in the follow-up PR (see memory/graphviz-wasm-followup.md and the build " +
                    "harness in wasm-build/).",
            )
        return resource.use { it.readBytes() }
    }

    /**
     * Decode the wasm bytes into a chasm module. Pure validation; no execution
     * happens yet.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun decodeModule(bytes: ByteArray): WasmModuleHandle {
        TODO(
            "Decode `bytes` with chasm's `module(bytes)` (see chasm README). The follow-up " +
                "wraps the result in WasmModuleHandle so this signature can stay stable when " +
                "chasm's surface evolves.",
        )
    }

    /**
     * Instantiate the module with the host imports it asks for. Wires
     * `wasi_snapshot_preview1.*` via `bindings-chasm-wasip1` and supplies any
     * remaining Emscripten-flavored imports via `bindings-chasm-emscripten`
     * (only needed if we end up sourcing the wasm from hpcc-js rather than
     * building it ourselves).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun instantiate(module: WasmModuleHandle): WasmInstanceHandle {
        TODO(
            "Create a chasm store, attach WASI P1 + Emscripten bindings from at.released.weh, " +
                "and instantiate the module. Return a WasmInstanceHandle that wraps the " +
                "(store, instance) pair so callers don't depend on chasm's exact API.",
        )
    }

    /**
     * Marshal [source] into the instance's linear memory, call the `gv_render`
     * export with the engine ordinal, read the returned C string back out, and
     * free the wasm-side buffer.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun invokeGvRender(
        instance: WasmInstanceHandle,
        source: String,
        engine: DotEngine,
    ): String {
        TODO(
            "1) call malloc(source.utf8.length+1) on the instance, 2) write the UTF-8 bytes + " +
                "NUL into linear memory at the returned pointer, 3) call gv_render(ptr, len, " +
                "engine.ordinal) and capture the SVG pointer, 4) readCString(svgPtr), 5) call " +
                "free on both pointers. Wrap chasm Trap exceptions in GraphvizException with " +
                "the offending DOT source attached.",
        )
    }

    /** Release any per-call wasm resources (the instance, its store). */
    @Suppress("UNUSED_PARAMETER")
    private fun disposeInstance(instance: WasmInstanceHandle) {
        // No-op until the per-call vs. shared-instance decision is made in the
        // follow-up. Kept as a hook so callers don't have to learn a new shape.
    }

    /**
     * Classpath resource path for the bundled wasm. Lives under the same
     * package directory as this file so it travels with the shadow jar.
     */
    private const val WASM_RESOURCE_PATH: String = "/sk/ainet/app/notebook/wasm/graphviz.wasm"
}

// ----------- opaque handles (replaced with chasm types in the follow-up) -----

/** Placeholder for chasm's `Module` type. Kept opaque so this file compiles. */
private class WasmModuleHandle

/** Placeholder for the `(Store, Instance)` pair chasm exposes. */
private class WasmInstanceHandle

// ----------- exceptions -----------------------------------------------------

/**
 * Thrown for runtime failures inside the wasm module — malformed DOT, layout
 * failures, OOM. Carries the DOT source so notebook authors can debug without
 * scraping kernel logs.
 */
class GraphvizException(
    message: String,
    val source: String,
    val engine: DotEngine,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Thrown until the wasm artifact is bundled into kotlin-notebook resources.
 * Distinct type from [GraphvizException] so tests can pin "scaffold state"
 * separately from "real renderer state".
 */
class GraphvizNotBundledException(message: String) : RuntimeException(message)
