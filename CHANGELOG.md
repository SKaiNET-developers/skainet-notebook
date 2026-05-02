# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

### Added
- README "SKaiNET notebook dependency" section now documents the `%use skainet-notebook` magic alongside the existing `@file:DependsOn` form, including version-pin variants (`%use skainet-notebook(0.22.1)`, `@0.22.1`) and a callout that JVM startup args (e.g. `--add-modules jdk.incubator.vector`) still have to be set on the kernel — the registry descriptor cannot inject them. Companion descriptor `skainet-notebook.json` is staged for submission to https://github.com/Kotlin/kotlin-jupyter-libraries; once merged it makes `%use skainet-notebook` work out of the box from any Kotlin Jupyter kernel.

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