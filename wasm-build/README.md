# `wasm-build/` — reproducible C → wasm builds

Docker-based toolchain for compiling C libraries to WebAssembly using
[wasi-sdk](https://github.com/WebAssembly/wasi-sdk). Used in this repo to
produce the `graphviz.wasm` artifact consumed by `GraphvizWasm.kt`, but
deliberately generic enough that other C libraries could be cross-compiled
the same way.

## Quick start

```sh
make image                         # build the wasi-sdk Docker image (once)
make graphviz                      # produce dist/graphviz.wasm
make shell                         # interactive shell in the toolchain
make clean                         # wipe local build/ and dist/
```

When `dist/graphviz.wasm` is ready, copy it into the resources directory the
runtime loads from:

```sh
cp dist/graphviz.wasm \
   ../kotlin-notebook/src/main/resources/sk/ainet/app/notebook/wasm/
```

## What's in here

| File              | Purpose                                                                  |
| ----------------- | ------------------------------------------------------------------------ |
| `Dockerfile`      | debian-slim base + pinned wasi-sdk + autoconf/automake/libtool + clang   |
| `Makefile`        | host-side orchestration: `image`, `graphviz`, `shell`, `clean`           |
| `build-graphviz.sh` | runs INSIDE the image: fetch + configure + make + link a `graphviz.wasm` |
| `wrapper.c`       | C bridge linked at the tail — exports `gv_render`, `gv_free`, `malloc`, `free` |

## Pinned versions

| Component   | Version   | Where to bump                       |
| ----------- | --------- | ----------------------------------- |
| wasi-sdk    | 24.0      | `Dockerfile` (`WASI_SDK_VERSION` ARG) |
| Graphviz    | 12.2.1    | `build-graphviz.sh` (`GRAPHVIZ_VERSION`) |

Bumping is intentional. After each bump, rebuild the wasm, copy it into
`kotlin-notebook` resources, and re-run `./gradlew :kotlin-notebook:test`.
Record the wasi-sdk and Graphviz versions that produced the bundled artifact
in the commit message so reproduction is straightforward.

## Status — what works, what doesn't

**Works today**
- `make image` builds a clean wasi-sdk environment with everything autoconf
  needs to configure a typical C project against `wasm32-wasi`.
- `make shell` drops you into that environment so you can iterate manually.
- `make clean` is a real no-op cleaner.

**Skeleton, will fail without more work**
- `make graphviz` runs end-to-end up to Graphviz's `./configure`, but the
  Graphviz tree has not been verified to fully configure under wasi-sdk.
  Known issues to chase:
  - `AC_TRY_RUN` checks in `configure` need cross-compilation overrides
    (`ac_cv_*=` env vars to pre-decide the answer).
  - `pathplan` uses `setjmp`/`longjmp` — wasi-sdk needs the
    `-mllvm -wasm-enable-sjlj` flag plus matching linker switch.
  - Some files include `<sys/wait.h>` which wasi-sysroot doesn't provide.
    Either patch out (we don't fork wasm) or shim.
- The static-lib paths in `build-graphviz.sh` are best-guess from a typical
  autotools layout. Verify against `find $SRC_DIR -name '*.a'` once the build
  succeeds.

## Two paths for sourcing the wasm

This harness is set up for **Path A**: build Graphviz from official C source
against wasi-sdk. Output: a slim wasm whose imports are pure WASI, handled by
`at.released.weh:bindings-chasm-wasip1` on the JVM side. Cleanest endpoint;
the iteration to land it is what's left.

**Path B** (fallback): extract the wasm bytes from
`@hpcc-js/wasm-graphviz`'s `dist/index.js` (it's base64-embedded in the JS),
and supply the Emscripten host ABI via
`at.released.weh:bindings-chasm-emscripten` on the JVM side. Lower-quality
endpoint — we'd inherit hpcc-js's Emscripten build flags — but a working
wasm is available immediately. Useful as a v0.1 if Path A drags.

The decision is left to whoever picks up the follow-up; this directory is
written for Path A but `wrapper.c` and the export list are the same either
way.

## Why Docker rather than a host-side install

- wasi-sdk on macOS occasionally lags behind Linux; pinning a Linux image
  keeps the build deterministic across contributor machines.
- Autoconf-based C projects often need a precise version of `bison`/`flex`/
  `libtool` that may or may not match what's on a given host.
- CI can run the same image — the `make image` target is the only thing CI
  has to learn.
