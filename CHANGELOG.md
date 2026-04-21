# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

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