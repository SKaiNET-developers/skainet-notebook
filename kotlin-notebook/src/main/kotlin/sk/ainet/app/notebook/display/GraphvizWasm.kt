package sk.ainet.app.notebook.display

import io.github.charlietap.chasm.embedding.dsl.imports
import io.github.charlietap.chasm.embedding.exports
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.memory.readInt
import io.github.charlietap.chasm.embedding.memory.readNullTerminatedUtf8String
import io.github.charlietap.chasm.embedding.memory.writeByte
import io.github.charlietap.chasm.embedding.memory.writeBytes
import io.github.charlietap.chasm.embedding.memory.writeInt
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.runtime.value.ExecutionValue
import io.github.charlietap.chasm.runtime.value.NumberValue

/**
 * JVM-side Graphviz renderer.
 *
 * The bundled `graphviz.wasm` (at the classpath resource path below) is decoded
 * once per kernel session and instantiated freshly per render call. The wasm
 * exposes a small plain-C ABI — `char *render_dot_svg(char *dot)` plus
 * `malloc` / `free` and a `memory` export — so chasm calls it directly without
 * needing any Emscripten Embind runtime.
 *
 * Bundled artifact provenance: see
 * `kotlin-notebook/src/main/resources/sk/ainet/app/notebook/wasm/README.md`.
 */
internal object GraphvizWasm {

    private const val WASM_RESOURCE_PATH: String = "/sk/ainet/app/notebook/wasm/graphviz.wasm"

    /**
     * The wasm module, decoded once per kernel session. Decode is the dominant
     * cost (~hundreds of ms on a 600 KB module with 1400 functions);
     * instantiation off a decoded module is cheap, so we pay it once and keep
     * per-call instantiation cheap.
     */
    private val module: Module by lazy {
        val bytes = GraphvizWasm::class.java.getResourceAsStream(WASM_RESOURCE_PATH)
            ?.use { it.readBytes() }
            ?: throw GraphvizNotBundledException(
                "Bundled Graphviz wasm not found at classpath resource $WASM_RESOURCE_PATH. " +
                    "See kotlin-notebook/src/main/resources/sk/ainet/app/notebook/wasm/README.md " +
                    "and wasm-build/ for how the artifact is produced.",
            )
        when (val result = module(bytes)) {
            is ChasmResult.Success -> result.result
            is ChasmResult.Error -> error(
                "Failed to decode bundled graphviz.wasm: ${result.error}",
            )
        }
    }

    fun render(source: String, engine: DotEngine): String {
        if (engine != DotEngine.DOT) {
            // Kraphviz's api.c only links the dot + core plugins; other layout
            // engines need `neato_layout` (and friends) linked into the wasm.
            // Restriction is documented in resources/wasm/README.md; the
            // follow-up wasm-build/ will link the rest.
            throw GraphvizException(
                message = "Engine ${engine.name} is not linked into the bundled wasm. " +
                    "Only DOT is supported until wasm-build/ produces an artifact with the " +
                    "neato_layout plugin.",
                source = source,
                engine = engine,
            )
        }

        val store = store()
        val imports = buildImports(store)

        val instance = when (val result = instance(store, module, imports)) {
            is ChasmResult.Success -> result.result
            is ChasmResult.Error -> throw GraphvizException(
                "wasm instantiation failed: ${result.error}",
                source = source,
                engine = engine,
            )
        }

        val memory = exports(instance)
            .firstOrNull { it.name == "memory" }
            ?.value as? Memory
            ?: throw GraphvizException(
                "bundled wasm does not export `memory`",
                source = source,
                engine = engine,
            )

        val utf8 = source.encodeToByteArray()
        val dotPtr = mallocOrThrow(store, instance, source, engine, utf8.size + 1)
        writeBytesOrThrow(store, memory, dotPtr, utf8, source, engine)
        writeByteOrThrow(store, memory, dotPtr + utf8.size, 0, source, engine)

        val svgPtr = invokeI32(
            store, instance, "render_dot_svg", listOf(NumberValue.I32(dotPtr)), source, engine,
        )

        // free the DOT buffer regardless of render outcome.
        invokeUnit(store, instance, "free", listOf(NumberValue.I32(dotPtr)), source, engine)

        if (svgPtr == 0) {
            throw GraphvizException(
                "render_dot_svg returned NULL — Graphviz couldn't lay out the input",
                source = source,
                engine = engine,
            )
        }

        val svg = when (val result = readNullTerminatedUtf8String(store, memory, svgPtr)) {
            is ChasmResult.Success -> result.result
            is ChasmResult.Error -> {
                // Best-effort free; report the original read error.
                invokeUnit(store, instance, "free", listOf(NumberValue.I32(svgPtr)), source, engine)
                throw GraphvizException(
                    "failed reading SVG from wasm memory: ${result.error}",
                    source = source,
                    engine = engine,
                )
            }
        }

        invokeUnit(store, instance, "free", listOf(NumberValue.I32(svgPtr)), source, engine)

        return svg
    }

    // -------- chasm result unwrapping helpers ----------

    private fun mallocOrThrow(
        store: Store,
        instance: Instance,
        source: String,
        engine: DotEngine,
        size: Int,
    ): Int {
        val ptr = invokeI32(
            store, instance, "malloc", listOf(NumberValue.I32(size)), source, engine,
        )
        if (ptr == 0) {
            throw GraphvizException(
                "wasm malloc($size) returned NULL (out of linear memory?)",
                source = source,
                engine = engine,
            )
        }
        return ptr
    }

    private fun invokeI32(
        store: Store,
        instance: Instance,
        name: String,
        args: List<ExecutionValue>,
        source: String,
        engine: DotEngine,
    ): Int {
        return when (val result = invoke(store, instance, name, args)) {
            is ChasmResult.Success -> (result.result.firstOrNull() as? NumberValue.I32)?.value
                ?: throw GraphvizException(
                    "wasm function `$name` returned no i32 result",
                    source, engine,
                )

            is ChasmResult.Error -> throw GraphvizException(
                "wasm `$name` invocation failed: ${result.error}",
                source, engine,
            )
        }
    }

    private fun invokeUnit(
        store: Store,
        instance: Instance,
        name: String,
        args: List<ExecutionValue>,
        source: String,
        engine: DotEngine,
    ) {
        when (val result = invoke(store, instance, name, args)) {
            is ChasmResult.Success -> Unit
            is ChasmResult.Error -> throw GraphvizException(
                "wasm `$name` invocation failed: ${result.error}",
                source, engine,
            )
        }
    }

    private fun writeBytesOrThrow(
        store: Store,
        memory: Memory,
        ptr: Int,
        bytes: ByteArray,
        source: String,
        engine: DotEngine,
    ) {
        when (val result = writeBytes(store, memory, ptr, bytes)) {
            is ChasmResult.Success -> Unit
            is ChasmResult.Error -> throw GraphvizException(
                "writing ${bytes.size} bytes to wasm memory@$ptr failed: ${result.error}",
                source, engine,
            )
        }
    }

    private fun writeByteOrThrow(
        store: Store,
        memory: Memory,
        ptr: Int,
        byte: Int,
        source: String,
        engine: DotEngine,
    ) {
        when (val result = writeByte(store, memory, ptr, byte.toByte())) {
            is ChasmResult.Success -> Unit
            is ChasmResult.Error -> throw GraphvizException(
                "writing byte 0x${byte.toString(16)} to wasm memory@$ptr failed: ${result.error}",
                source, engine,
            )
        }
    }

    // -------- host function bodies (12 imports) ----------

    /**
     * Build the import list the bundled wasm expects.
     *
     * Names are stable across wasm rebuilds: we ship `wasi_snapshot_preview1.*`
     * for the WASI surface and `env.__syscall_*` for the four file-status
     * Emscripten syscalls Graphviz never actually exercises on the render
     * path. The list was determined by parsing the wasm's import section
     * directly (see git history for the extraction script).
     *
     * Most functions are deliberately no-op stubs — Kraphviz's reference
     * implementation confirms only `fd_write` actually fires during a
     * `render_dot_svg` call. Stubs return fails-loudly errnos rather than 0
     * so that any unexpected call surfaces clearly in the trap message.
     */
    private fun buildImports(store: Store) = imports(store) {
        // ---- wasi_snapshot_preview1 ----

        function {
            moduleName = "wasi_snapshot_preview1"
            entityName = "clock_time_get"
            type {
                // (clock_id i32, precision i64, time_out_ptr i32) -> errno i32
                params { i32(); i64(); i32() }
                results { i32() }
            }
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = "wasi_snapshot_preview1"
            entityName = "proc_exit"
            type {
                // (rval i32) -> ()
                params { i32() }
                results { }
            }
            reference { _ -> emptyList() }
        }

        function {
            moduleName = "wasi_snapshot_preview1"
            entityName = "fd_write"
            type {
                // (fd i32, iovs_ptr i32, iovs_len i32, nwritten_ptr i32) -> errno i32
                params { i32(); i32(); i32(); i32() }
                results { i32() }
            }
            reference { args -> fdWrite(this.store, this.instance, args) }
        }

        function {
            moduleName = "wasi_snapshot_preview1"
            entityName = "fd_read"
            type {
                params { i32(); i32(); i32(); i32() }
                results { i32() }
            }
            // ENOENT — render path doesn't read files; loud signal if it ever does.
            reference { _ -> listOf(NumberValue.I32(WASI_ERRNO_NOENT)) }
        }

        function {
            moduleName = "wasi_snapshot_preview1"
            entityName = "fd_close"
            type {
                params { i32() }
                results { i32() }
            }
            // Success — `close` on an fd we never opened is fine to no-op.
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        function {
            moduleName = "wasi_snapshot_preview1"
            entityName = "fd_seek"
            type {
                // (fd i32, offset i64, whence i32, new_offset_ptr i32) -> errno i32
                params { i32(); i64(); i32(); i32() }
                results { i32() }
            }
            reference { _ -> listOf(NumberValue.I32(WASI_ERRNO_NOENT)) }
        }

        function {
            moduleName = "wasi_snapshot_preview1"
            entityName = "environ_sizes_get"
            type {
                params { i32(); i32() }
                results { i32() }
            }
            reference { args -> environSizesGet(this.store, this.instance, args) }
        }

        function {
            moduleName = "wasi_snapshot_preview1"
            entityName = "environ_get"
            type {
                params { i32(); i32() }
                results { i32() }
            }
            // No env to write (sizes_get reported 0); just return success.
            reference { _ -> listOf(NumberValue.I32(0)) }
        }

        // ---- env (Emscripten libc syscalls; never called on render path) ----

        function {
            moduleName = "env"
            entityName = "__syscall_faccessat"
            type {
                params { i32(); i32(); i32(); i32() }
                results { i32() }
            }
            reference { _ -> listOf(NumberValue.I32(EMSCRIPTEN_NEG_ENOENT)) }
        }

        function {
            moduleName = "env"
            entityName = "__syscall_stat64"
            type {
                params { i32(); i32() }
                results { i32() }
            }
            reference { _ -> listOf(NumberValue.I32(EMSCRIPTEN_NEG_ENOENT)) }
        }

        function {
            moduleName = "env"
            entityName = "__syscall_newfstatat"
            type {
                params { i32(); i32(); i32(); i32() }
                results { i32() }
            }
            reference { _ -> listOf(NumberValue.I32(EMSCRIPTEN_NEG_ENOENT)) }
        }

        function {
            moduleName = "env"
            entityName = "__syscall_unlinkat"
            type {
                params { i32(); i32(); i32() }
                results { i32() }
            }
            reference { _ -> listOf(NumberValue.I32(EMSCRIPTEN_NEG_ENOENT)) }
        }
    }

    /**
     * Resolve the wasm's exported memory from a host-function callback. The
     * memory export is not yet available when imports are being constructed,
     * so host functions look it up on demand via the running instance.
     */
    private fun lookupMemoryForHost(instance: Instance): Memory? =
        exports(instance).firstOrNull { it.name == "memory" }?.value as? Memory

    /**
     * Drain the iovecs Graphviz hands us via `fd_write`, sum their lengths,
     * and report the total back through `nwritten_ptr`. We don't actually
     * surface the contents anywhere — Graphviz writes its own warnings to
     * stderr; in a notebook those would be noise. Returning the summed write
     * count is what keeps the caller's retry loop from spinning.
     */
    private fun fdWrite(
        store: Store,
        instance: Instance,
        args: List<ExecutionValue>,
    ): List<ExecutionValue> {
        val memory = lookupMemoryForHost(instance)
            ?: return listOf(NumberValue.I32(WASI_ERRNO_NOENT))
        // args = [fd, iovs_ptr, iovs_len, nwritten_ptr]
        val iovsPtr = (args[1] as NumberValue.I32).value
        val iovsLen = (args[2] as NumberValue.I32).value
        val nwrittenPtr = (args[3] as NumberValue.I32).value

        var total = 0
        for (i in 0 until iovsLen) {
            // Each ciovec is 8 bytes: 4-byte buf pointer + 4-byte length.
            val lenResult = readInt(store, memory, iovsPtr + i * 8 + 4)
            val len = when (lenResult) {
                is ChasmResult.Success -> lenResult.result
                is ChasmResult.Error -> return listOf(NumberValue.I32(WASI_ERRNO_NOENT))
            }
            total += len
        }
        when (writeInt(store, memory, nwrittenPtr, total)) {
            is ChasmResult.Success -> Unit
            is ChasmResult.Error -> return listOf(NumberValue.I32(WASI_ERRNO_NOENT))
        }
        return listOf(NumberValue.I32(0))
    }

    /**
     * `environ_sizes_get(count_ptr, buf_size_ptr)` — report 0 vars by writing
     * zeros to both output pointers. Returning success without filling them
     * could leave Graphviz reading uninitialised memory and keying off a
     * non-zero count.
     */
    private fun environSizesGet(
        store: Store,
        instance: Instance,
        args: List<ExecutionValue>,
    ): List<ExecutionValue> {
        val memory = lookupMemoryForHost(instance)
            ?: return listOf(NumberValue.I32(WASI_ERRNO_NOENT))
        val countPtr = (args[0] as NumberValue.I32).value
        val bufSizePtr = (args[1] as NumberValue.I32).value
        writeInt(store, memory, countPtr, 0)
        writeInt(store, memory, bufSizePtr, 0)
        return listOf(NumberValue.I32(0))
    }

    // -------- constants ----------

    /** WASI Preview 1 errno ENOENT — used by host functions that fail loud. */
    private const val WASI_ERRNO_NOENT: Int = 44

    /**
     * Emscripten's `__syscall_*` family returns negated errno (Linux ABI
     * convention). -2 = ENOENT under the standard syscall mapping.
     */
    private const val EMSCRIPTEN_NEG_ENOENT: Int = -2
}

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
 * Thrown if the bundled `graphviz.wasm` resource is absent from the kotlin-notebook
 * classpath. Distinct type from [GraphvizException] so the integration's
 * scaffold-friendly catch can match it without hiding a real wasm runtime error.
 *
 * Under normal operation this never fires — the wasm ships with the shadow jar.
 * Kept as a distinct exception so any future rebuild that accidentally drops the
 * resource is caught cleanly.
 */
class GraphvizNotBundledException(message: String) : RuntimeException(message)
