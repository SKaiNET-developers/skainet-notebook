# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

## [0.25.1] - 2026-05-25

### Fixed
- Maven Central publication for `skainet-notebook-extensions` failed in the 0.25.0 release pipeline at `:skainet-notebook-extensions:generateMetadataFileForIosArm64Publication` with `FileNotFoundException: …/build/libs/skainet-notebook-extensions-iosArm64Main.klib` (and would have failed identically for iosSimulatorArm64, macosArm64, linuxX64, linuxArm64, js, wasmJs, wasmWasi). Root cause: `commonMain` shipped without a single source file, so every per-target `compileKotlinXxx` ran as NO-SOURCE, no klib was produced, and the publication's metadata writer then SHA-512-hashed a missing file. Added a single `internal` placeholder under `skainet-notebook-extensions/src/commonMain/kotlin/sk/ainet/app/notebook/extensions/Placeholder.kt` so each target compiles a non-empty klib. The renderer implementation still lives only in `jvmMain`; the placeholder is `internal` and stays out of the binary-compatibility-validator ABI dump.

### Changed
- `signAllPublications` flipped from `false` to `true` in `gradle.properties`. Sonatype Central requires PGP signatures for new releases, and the `release.yml` workflow already wires `ORG_GRADLE_PROJECT_signingInMemoryKey` / `signingInMemoryKeyPassword` from the `GPG_PRIVATE_KEY` / `SIGNING_PASSWORD` repo secrets — this just turns on the path that was sitting dormant in 0.25.0.

## [0.25.0] - 2026-05-25

### Added
- New `skainet-notebook-extensions` module — a Kotlin Multiplatform artifact published as `sk.ainet.app:skainet-notebook-extensions:0.25.0` that now owns the Graphviz/DOT cell renderer (`Dot`, `DotEngine`, `DotOptions`, `renderDot`, `GraphvizWasm`) and the bundled `graphviz.wasm`. Standalone consumers can depend on just the renderer without pulling the `kotlin-notebook` shadow jar; the notebook integration re-exposes it via the `%use skainet-notebook` cell magic. Targets the module declares: Android, iOS arm64 + simulator-arm64, macOS arm64, Linux x64/arm64, JVM, JS browser, Wasm-JS browser, Wasm-WASI node. JVM is the only target with a renderer implementation in this release; the others stay empty so a future browser-side wasm renderer can grow into them cleanly. The module ships with explicit-api enforcement and a binary-compatibility-validator API baseline (`skainet-notebook-extensions/api/jvm/`). (#117)
- `GraphProgram.asDot(rankdir)` extension (`kotlin-notebook/.../display/DagDotRender.kt`) — lowers a symbolic DAG-DSL `dag { … }` program to a `ComputeGraph` and serializes it with SKaiNET's `toGraphviz` exporter, returning a `Dot` cell-result so the renderer pipeline renders it inline as SVG. Replaces the two-step `program.toComputeGraph().toGraphviz()` chain with a single `program.asDot()` call. Upstream PRD drafted in `docs/upstream/graphprogram-to-graphviz.md` proposing the matching `String`-returning overload in `skainet-compile-dag`.
- `Module<T, V>.asDot(input, rankdir)` extension (`kotlin-notebook/.../display/ModuleDotRender.kt`) — renders a `sequential { … }` NN-DSL model by running one forward pass under a recording `DefaultGraphExecutionContext` (shape-only `VoidTensorOps`), capturing every op into an `ExecutionTape`, and lowering the tape to a `ComputeGraph` for DOT export. Uses the same gradient-tracer machinery SKaiNET's autograd backward pass relies on, so visualizing a module here exercises the recording path that backprop will reuse. Upstream PRD drafted in `docs/upstream/module-to-graphviz.md` proposing the equivalent overload in `skainet-compile-dag`.
- `Tensor<T, V>.tanh()` and `DagBuilder.tanh(input)` polyfill extensions (`kotlin-notebook/.../tools/Tanh.kt`) composing the exact identity `tanh(x) = 2*sigmoid(2x) - 1`. Both forms record as a `mulScalar -> sigmoid -> mulScalar -> subScalar` chain in the tape, so the rendered graph shows the decomposition rather than a single `tanh` block — faithful to what the kernel actually computes today. Upstream PRD drafted in `docs/upstream/tanh-activation.md` proposing the proper additive primitive (`TensorOps.tanh`, `TanhOperation`, CPU impl via `kotlin.math.tanh`, autograd backward `1 - tanh(x)^2`, recording decorator entry — six-layer wiring symmetric with the existing `sigmoid`). Filed as upstream issue [SKaiNET-developers/SKaiNET#630](https://github.com/SKaiNET-developers/SKaiNET/issues/630); patch-release safe (purely additive, no behavioural change to any existing call site).
- `notebooks/ext/DagToDot.ipynb` — sample notebook demonstrating `program.asDot()` on a small 3×3 stride-2 conv block over a 1×3×224×224 FP32 input, including a vertical-layout (`rankdir = "TB"`) variant.
- `notebooks/ext/MicrogradNeuron.ipynb` and `notebooks/ext/MicrogradNeuronNN.ipynb` — tribute notebooks porting Andrej Karpathy's [micrograd](https://github.com/karpathy/micrograd) `nn.Neuron(2); y = n([Value(1.0), Value(-2.0)]); draw_dot(y)` snippet to SKaiNET's DAG DSL and NN DSL respectively, both rendering the resulting computational graph via the `asDot()` helpers and the polyfilled `tanh`. Follow-up downstream issue [#123](https://github.com/SKaiNET-developers/skainet-notebook/issues/123) tracks the full `micrograd/demo.ipynb` port (training + decision boundary).
- `docs/upstream/graphviz-with-grads.md` — Phase 2 PRD proposing an opt-in `includeValues: Boolean = false` flag on the `toGraphviz` overload family that, when set, expands each node's record label to include `data` and `grad` summaries (matching micrograd's `draw_dot` after `y.backward()`). Depends on the Phase 1 overload PRDs landing and on SKaiNET's autograd backward pass being end-to-end verified.
- `SKaiNETJupyterIntegration` `onLoaded` now auto-imports `sk.ainet.lang.dag.*` (top-level `dag { … }` builder), `sk.ainet.lang.nn.dsl.*` (top-level `sequential<T, V>` builder; Kotlin's `import pkg.*` does NOT recurse into subpackages, so the existing `sk.ainet.lang.nn.*` line was insufficient), and `sk.ainet.lang.tensor.ops.*` (`TensorSpec` referenced from `input(...)` calls).
- README "SKaiNET notebook dependency" section now documents the `%use skainet-notebook` magic alongside the existing `@file:DependsOn` form, including version-pin variants (`%use skainet-notebook(0.25.0)`, `@0.25.0`) and a callout that JVM startup args (e.g. `--add-modules jdk.incubator.vector`) still have to be set on the kernel — the registry descriptor cannot inject them. Companion descriptor `skainet-notebook.json` is staged for submission to https://github.com/Kotlin/kotlin-jupyter-libraries; once merged it makes `%use skainet-notebook` work out of the box from any Kotlin Jupyter kernel.
- README "Rendering model architectures" section documents `program.asDot()` and `n.asDot(x)` for the DAG-DSL and NN-DSL paths respectively.
- `docs/SIMD.md` — maintainer-facing index that maps both bullets of #60 to their landed implementation (probe + integration warning + build-side `--add-modules`), lists where each piece lives, and points to `README.adoc` → "Enabling SIMD" for the user-facing setup recipes. Closes #60.

### Changed
- Updated SKaiNET libraries to version 0.25.0 (was 0.22.1). Picks up the 0.23.x / 0.24.x / 0.25.0 release line.
- `kotlin-notebook` drops its direct `chasm` + `weh` dependencies; they now come transitively through `project(":skainet-notebook-extensions")`. The `shadowJar` include list switched from a coordinate filter to `include(project(...))` so the renderer classes and the `graphviz.wasm` resource get bundled into the published uber-jar — the user-facing `@file:DependsOn` / `%use` behaviour is unchanged.
- Bumped Gradle wrapper from 9.0 to 9.5.1. 9.4.1 was a prerequisite for the Android KMP plugin landing alongside `skainet-notebook-extensions`; 9.5.1 picks up the dependabot follow-up. (#111)
- Held Kotlin Jupyter API plugin at `0.16.0-736`. The release-prep bump to `0.19.0-945` (#120) had to be reverted: `org.jetbrains.kotlinx:kotlin-jupyter-kernel:0.19.0-945`'s Maven Central POM mis-declares its `kernel-compiler-impl` transitive with `<groupId>kotlin-jupyter-kernel</groupId>` instead of `org.jetbrains.kotlinx`, and that artifact doesn't exist at any reachable coordinate (Maven Central, kotlin-dev Space, kotlinx-jupyter Space). `0.18.1-942` has a different upstream gap (its plugin requires `kotlin-gradle-plugin:2.4.0-dev-6891`, also not on Central). `0.16.0-736` is the last fully-resolvable known-good version for this repo.
- Bumped `NotebookInfo.VERSION` to `0.25.0` so `info()` and the Jupyter integration banner report the actual notebook version.
- The DOT renderer's bundled `graphviz.wasm` and its provenance README moved from `kotlin-notebook/src/main/resources/sk/ainet/app/notebook/wasm/` to `skainet-notebook-extensions/src/jvmMain/resources/sk/ainet/app/notebook/wasm/`. The classpath resource path (`/sk/ainet/app/notebook/wasm/graphviz.wasm`) is unchanged; only the on-disk source path is different.
- `GraphvizWasm` is now `public` (previously `internal`) so non-notebook consumers calling `sk.ainet.app:skainet-notebook-extensions` directly can invoke `GraphvizWasm.render(source, engine)` for raw SVG without the jupyter-api `MimeTypedResult` wrapping.
- All 8 explicit-coordinate notebooks (5 `USE { mavenLocal() }`, 3 older `@file:DependsOn` with stale versions) repointed at `mavenCentral()` and bumped to `0.25.0` for the release. The four `notebooks/ext/` intros lost the "run `:kotlin-notebook:publishToMavenLocal` first" prerequisite line — the dep resolves directly for end users. `notebooks/MLP/MLP.ipynb` is left alone (it uses the `%use skainet-notebook` descriptor magic, resolved via the upstream `kotlin-jupyter-libraries` registry, not a direct coordinate).

### Fixed
- `Module<T, V>.asDot(input)` was rendering only the *ops* of an NN model and silently dropping every *leaf* — runtime input, every parameter, every constant — so a binary op like `add(matmul, bias)` displayed as a single-input op (no `bias →` arrow) and `matmul(x, w)` displayed as a source node (no `x` or `w`). Root cause: `DefaultExecutionTape.toComputeGraph()` defaults `synthesizeExternalInputs = false`, which in the trace-based lowering skips creating placeholder nodes for tensor inputs with no producer in the trace. The helper now passes `synthesizeExternalInputs = true`; leaves wire correctly into every operand port. Covered by `LeafNodeRenderDebugTest` with a side-by-side dump of both flag values so the regression surfaces loudly if anyone touches the helper or the upstream default.

## [0.22.1] - 2026-05-02

### Added
- `checkSimd()` (top-level in `sk.ainet.app.notebook`, also exposed as `NotebookInfo.checkSimd()`) returns a `SimdReport` capturing whether SKaiNET's Vector-API CPU kernels are reachable from the current JVM. Mirrors the three preconditions SKaiNET's `PlatformCpuOpsFactory` checks at startup — JDK ≥ 21, `jdk.incubator.vector` module loaded, and the `skainet.cpu.vector.enabled` / `SKAINET_CPU_VECTOR_ENABLED` kill-switch — so notebook authors can assert the fast path before running benchmarks.
- `SKaiNETJupyterIntegration` now calls `checkSimd()` in `onLoaded` and renders an inline yellow warning when SIMD is unreachable. Previously the scalar fallback was completely silent — users running the SKaiNET banner saw "ready" while the kernel quietly served scalar matmul. The warning names the missing precondition and points at the IntelliJ JVM-options setting.
- New "Enabling SIMD" section in `README.adoc` documenting the `--add-modules jdk.incubator.vector` requirement for both IntelliJ Kotlin Notebook and plain Jupyter, plus the `checkSimd()` cell-side probe and the `skainet.cpu.vector.enabled` kill-switch.

### Changed
- Updated SKaiNET libraries to version 0.22.1 (was 0.19.1 in the build, 0.19.2 in the published artifact). Picks up the 0.20.0 Q4_K / Q6_K native CPU matmul (lazy shape-swap transpose + SIMD kernels) and StableHLO lowerings for `scaledDotProductAttention`, plus the 0.21.x / 0.22.x release line on top.
- Bumped `NotebookInfo.VERSION` to `0.22.1` so `info()` and the Jupyter integration banner report the actual notebook version (was stuck at the stale `0.19.0` constant the previous two releases forgot to bump).
- `tasks.test` now starts the test JVM with `--add-modules jdk.incubator.vector` so `NotebookInfoTest` can pin the active-SIMD branch of `checkSimd()` instead of always exercising the fallback.

### Removed
- Dropped the `configurations.configureEach { exclude(group = "sk.ainet", module = "skainet-backend-api*") }` workaround that 0.19.2 needed. Upstream fixed the broken `skainet-backend-cpu-jvm` POM in 0.19.1 (correct `sk.ainet.core` group, real version), so the exclusion is no longer needed and was actively harmful — `skainet-backend-api-jvm` is a legitimate runtime dep of the CPU backend.

### Fixed
- `shadowJar` now bundles `skainet-backend-api-jvm` and `skainet-lang-ksp-annotations-jvm`, the two newly-published transitive deps of `skainet-backend-cpu-jvm` in 0.22.1. Without them the published uber-jar would still load the CPU context but trip `NoClassDefFoundError` on the first call into anything that touches the backend-API surface.
- `Tensor.toImage(Layout.HW | Layout.CHW)` for single-channel images: the grayscale branch was dividing the source value by 255 before the `multiply255` heuristic ran, so both code paths through `toByte` collapsed to zero — a tensor of constant `128f` rendered as solid black, and the same for `Int8` `200`. The grayscale branch now mirrors the RGB branch and lets the heuristic decide whether to scale, so MNIST-style 0..255 grayscale and FP32 normalized 0..1 inputs both render with the expected intensity. Pinned by the previously-failing `ImageUtilsTest.toImage_HW_grayscale_supported` and `toImage_CHW_grayscale_Int8_Byte_is_treated_as_unsigned` cases.

## [0.19.2] - 2026-04-22

### Fixed
- Publish the shadow uber-jar as the main artifact and strip all runtime dependencies from the generated POM. Previously the thin wrapper jar was published alongside a POM that listed `sk.ainet.core:skainet-backend-cpu-jvm` as a runtime dependency; that module's upstream POM references the unpublished `sk.ainet:skainet-backend-api-jvm:unspecified` coordinate, so consumers using Kotlin Jupyter's `@file:DependsOn("sk.ainet.app:kotlin-notebook:...")` transitively failed to resolve the CPU backend and hit `NoClassDefFoundError: sk/ainet/context/DirectCpuExecutionContext` at runtime. The uber-jar already bundles every SKaiNET module it needs, so the published POM now has no `<dependencies>` block and transitive resolution is sidestepped entirely.

## [0.19.1] - 2026-04-21

### Changed
- Updated SKaiNET libraries to version 0.19.1.

## [0.19.0] - 2026-04-21

### Added
- `SKaiNETJupyterIntegration` — a `JupyterIntegration` subclass discovered automatically by the Kotlin Jupyter kernel. When the notebook jar is on the classpath, the integration pre-imports the hot SKaiNET packages (`sk.ainet.context.*`, `sk.ainet.lang.nn.*`, `sk.ainet.lang.tensor.*`, `sk.ainet.lang.tensor.dsl.*`, `sk.ainet.lang.types.*`, …) and registers renderers for `Tensor<*, *>` (HTML `<pre>` via `pprint`) and `java.awt.image.BufferedImage` (PNG mime). Closes the `import` boilerplate that every notebook cell previously had to repeat. (#43)
- Expanded `NotebookAliases.kt` with short typealiases for the dtype markers (`FP16`/`FP32`/`FP64`/`Int4`/`Int8`/`Int16`/`Int32`/`Int64`/`UInt8`/`UInt16`/`UInt32`/`UInt64`), `DType`, `Module<T, V>`, and a `Net<T, V>` shorthand for the neural-network DSL. (#43)
- `SKaiNETJupyterIntegrationTest` — verifies the integration class instantiates and that the Gradle plugin generates `META-INF/kotlin-jupyter-libraries/libraries.json` pointing at the producer FQN.

### Changed
- Updated SKaiNET libraries to version 0.19.0.
- `emitHtml(html)` in `display/ImageRender.kt` now returns `MimeTypedResult` (via the Kotlin-Jupyter `HTML()` helper) instead of a raw `String`, and `render(image, ...)` returns that result — cells that end with `render(...)` / `renderGrid(...)` now actually display HTML in the notebook. Updated `ImageRenderTest` and `SuccessMetricsTest` to assert on the returned mime bundle rather than captured stdout.

### Removed
- Dropped `skainet-lang-kan` dependency (module removed upstream in mainline SKaiNET).
- Dropped `skainet-apps-kllama` dependency (moved to the standalone `SKaiNET-transformers` repository).

### Fixed
- `shadowJar` include for data-simple now uses the correct published artifact coordinate `skainet-data-basic-jvm` (was `skainet-data-simple-jvm`, which never matched).

## [0.11.0] - 2026-02-08

### Changed
- Updated SKaiNET libraries to version 0.11.0.

## [0.8.3] - 2026-01-18
### Changed
- Updated to SKaiNET 0.8.3.

## [0.7.1] - 2026-01-15
### Changed
- Updated to SKaiNET 0.7.1.

## [0.6.3] - 2026-01-10
### Changed
- Updated to SKaiNET 0.6.3.
- Re-export SKaiNET classes als typealias in sk.ainet.app.notebook package

## [0.5.1] - 2025-12-26
### Fixed
- Version alignment in NotebookInfo and general maintenance.

## [0.5.0] - 2025-12-07
### Added
- New Image Renderer for Jupyter notebooks.
- Responsive grid support for image display.
- Nearest neighbor upsampling for tensors.
- MNIST data loader and utility notebooks.
- Performance measurements for image rendering.
- Documentation for image rendering architecture.

### Changed
- Updated to SKaiNET 0.5.0.
- Integrated with Jupyter API for better rendering.

## [0.4.0] - 2025-12-04
### Changed
- Updated to SKaiNET 0.4.0.
- Updated shadow-gradle plugin.

## [0.3.0] - 2025-11-27
### Added
- New tutorial and prototype notebooks:
  - notebooks/Tutorials/NN-Dsl.ipynb
  - notebooks/Book/ndarray.ipynb
  - notebooks/tensors/stackVectorWithMatrix.ipynb
  - notebooks/prototypes/MultimodalInputsMerge.ipynb

### Changed
- Updated notebooks/MLP/SinusApproximator.ipynb with improvements.