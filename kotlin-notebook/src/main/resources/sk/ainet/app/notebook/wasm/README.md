# `sk.ainet.app.notebook.wasm` — bundled wasm artifacts

This directory ships the Graphviz WebAssembly binary that `GraphvizWasm`
executes on the JVM via [chasm](https://github.com/CharlieTap/chasm) to
render DOT graphs in notebook cells. No JS, no CDN, no browser dependency.

## Layout

```
sk/ainet/app/notebook/wasm/
├── README.md          # this file
└── graphviz.wasm      # the artifact loaded by GraphvizWasm
```

`GraphvizWasm.kt` reads the binary as a classpath resource via
`GraphvizWasm::class.java.getResourceAsStream("/sk/ainet/app/notebook/wasm/graphviz.wasm")`.

## Provenance — interim artifact from Kraphviz

The `graphviz.wasm` currently checked in is sourced from
[Yeicor/Kraphviz](https://github.com/Yeicor/Kraphviz), which compiles official
Graphviz to wasm via Emscripten with a minimal C wrapper exposing
`render_dot_svg(char *dot) -> char*` plus the standard `malloc` / `free`. The
wrapper statically links the `dot` and `core` layout/render plugins via
`lt_preloaded_symbols`, so the wasm has a tiny plain-C ABI (no Emscripten
Embind runtime needed) and exactly 12 imports: 8 standard `wasi_snapshot_preview1.*`
functions and 4 `env.__syscall_*` calls Graphviz never exercises on the
render path. All 12 are wired in `GraphvizWasm.kt` as Kotlin host functions.

Kraphviz has no LICENSE file in the repository, so this artifact is
**interim**. The reproducible build harness in `wasm-build/` (Docker +
Emscripten + an `api.c` wrapper, modelled on Kraphviz's recipe) is the
follow-up that produces our own artifact from official Graphviz source.

When `wasm-build/` produces a working binary, it replaces this file and this
README's "Provenance" section gets updated to point at the local build.

## Exports the host expects

| Export             | Signature                                             | Notes                                                          |
| ------------------ | ----------------------------------------------------- | -------------------------------------------------------------- |
| `render_dot_svg`   | `char* render_dot_svg(char* dot)`                     | Returns a NUL-terminated UTF-8 SVG document, malloc'd in wasm  |
| `malloc`           | `void* malloc(size_t)`                                | For Kotlin-side input buffer allocation                        |
| `free`             | `void free(void*)`                                    | Pairs with `malloc`                                            |
| `memory`           | (the linear memory)                                   |                                                                |
| `viz_set_yinvert`  | `void viz_set_yinvert(int)`                           | Optional config; not currently called                          |
| `viz_set_nop`      | `void viz_set_nop(int)`                               | Optional config; not currently called                          |

## Imports the host supplies

`wasi_snapshot_preview1.*`: `clock_time_get`, `proc_exit`, `fd_write`,
`fd_read`, `fd_close`, `fd_seek`, `environ_sizes_get`, `environ_get`.

`env.__syscall_*`: `__syscall_faccessat`, `__syscall_stat64`,
`__syscall_newfstatat`, `__syscall_unlinkat`. Stubs returning `-ENOENT`;
Kraphviz confirms these are never called during `render_dot_svg`.

All host implementations are in `GraphvizWasm.kt`. Only `fd_write` and
`environ_sizes_get` have non-trivial bodies (they read/write the wasm's
linear memory via `chasm.embedding.memory.*`).

## Engine support

The bundled wasm only links the `dot` + `core` plugins. Requesting any other
`DotEngine` from `renderDot` raises `GraphvizException` with a clear "engine
not bundled" message. When `wasm-build/` produces our own artifact it will
link `neato_layout` too.

## Future-proofing — what to verify if the artifact changes

The current `GraphvizWasm.kt` host-function list was derived by parsing the
wasm's import section directly. If a rebuilt artifact adds or removes
imports, instantiation will fail with a chasm error naming the offending
import. To dump a fresh import list:

```sh
uv run python -c "
import sys
data = open('graphviz.wasm','rb').read()
# walk the wasm section table; see git history for the full parser.
"
```
