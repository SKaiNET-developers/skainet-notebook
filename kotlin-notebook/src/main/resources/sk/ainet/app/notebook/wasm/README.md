# `sk.ainet.app.notebook.wasm` — bundled wasm artifacts

This directory is where the Graphviz WebAssembly binary lives at runtime. On
the scaffold branch the binary is **not yet present** — `GraphvizWasm` throws
`GraphvizNotBundledException` until something matching the path below appears
in the shadow jar.

## Expected layout

```
sk/ainet/app/notebook/wasm/
├── README.md          # this file (shipped alongside the binary, harmless)
└── graphviz.wasm      # the artifact loaded by GraphvizWasm.loadWasmBytes
```

`GraphvizWasm.kt` reads the binary as a classpath resource via
`GraphvizWasm::class.java.getResourceAsStream("/sk/ainet/app/notebook/wasm/graphviz.wasm")`.
Anything else in this directory (this README, future tokenizer mappings,
etc.) is ignored by the renderer.

## Expected exports

The wasm module must export, at minimum:

| Export        | Signature (C view)                                    | Notes                                                          |
| ------------- | ----------------------------------------------------- | -------------------------------------------------------------- |
| `gv_render`   | `char* gv_render(const char* dot, size_t len, int engine)` | Returns a NUL-terminated UTF-8 SVG document, owned by the wasm |
| `gv_free`     | `void gv_free(char* ptr)`                             | Frees a pointer previously returned by `gv_render`             |
| `malloc`      | `void* malloc(size_t)`                                | For Kotlin-side input buffer allocation                        |
| `free`        | `void free(void*)`                                    | Pairs with `malloc`                                            |
| `memory`      | (the linear memory)                                   | Default export name is fine                                    |

The `engine` integer corresponds to `DotEngine.ordinal` (see
`DotRender.kt`): 0 = DOT, 1 = NEATO, 2 = TWOPI, 3 = CIRCO, 4 = FDP,
5 = OSAGE, 6 = PATCHWORK.

## Expected imports

Either:

- **WASI Preview 1 only** (the clean path) — `wasi_snapshot_preview1.fd_write`
  for stderr diagnostics, `clock_time_get`, `random_get`, `proc_exit`. All
  supplied by `at.released.weh:bindings-chasm-wasip1`, no manual shims needed.
- **WASI P1 + Emscripten runtime ABI** (the hpcc-js path) —
  `env.__syscall_*`, `env.emscripten_resize_heap`, `env.memory` initial size,
  the indirect-function table. Supplied by
  `at.released.weh:bindings-chasm-emscripten`; a handful of imports may still
  need ad-hoc Kotlin shims, depending on the exact Emscripten flags hpcc-js
  built with.

Whichever route the follow-up takes, `GraphvizWasm.instantiate` is where the
wiring goes.

## How the binary is built

The reproducible build harness lives at `<repo-root>/wasm-build/`. From
the project root:

```sh
make -C wasm-build image       # build the wasi-sdk Docker image (once)
make -C wasm-build graphviz    # produce dist/graphviz.wasm
cp wasm-build/dist/graphviz.wasm \
   kotlin-notebook/src/main/resources/sk/ainet/app/notebook/wasm/
```

See `wasm-build/README.md` for what's currently working and what's still TODO.
