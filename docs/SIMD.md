# SIMD support in skainet-notebook

SKaiNET's CPU backend installs SIMD-accelerated kernels (matmul, Q4_K /
Q6_K / Q8_0 / TurboQuant dequant + matmul, elementwise / reduction ops)
via the JDK Vector API (`jdk.incubator.vector`). The Vector API is an
incubator module shipped with the JDK but not in the default module
graph — the kernel JVM has to be started with
`--add-modules jdk.incubator.vector`, otherwise SKaiNET silently falls
back to the scalar `DefaultCpuOps` implementation.

This document is the index for what skainet-notebook does about that.

## Status

Resolved on `develop`. The notebook integration:

1. Probes the same three preconditions SKaiNET's `PlatformCpuOpsFactory`
   checks at backend startup (JDK 21+, `jdk.incubator.vector` loaded,
   `skainet.cpu.vector.enabled` / `SKAINET_CPU_VECTOR_ENABLED` not set
   to `false`).
2. Surfaces the result to the cell author at integration-load time —
   green path is silent; missing-SIMD renders an inline yellow warning
   that names the specific missing precondition and tells the user how
   to fix it for the IntelliJ Kotlin Notebook.
3. Exposes the probe as a cell-callable helper so authors can assert
   the fast path before running benchmarks.

## Where it lives

| Concern                       | File                                                                                       |
| ----------------------------- | ------------------------------------------------------------------------------------------ |
| Probe + `SimdReport`          | `kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/SKaiNETNotebook.kt`                  |
| Integration-load warning      | `kotlin-notebook/src/main/kotlin/sk/ainet/app/notebook/integration/SKaiNETJupyterIntegration.kt` (`onLoaded` block, `simdWarningHtml`) |
| Test-JVM `--add-modules` flag | `kotlin-notebook/build.gradle.kts` (`tasks.test { jvmArgs("--add-modules", "jdk.incubator.vector") }`) |
| Tests pinning both branches   | `kotlin-notebook/src/test/kotlin/sk/ainet/app/notebook/NotebookInfoTest.kt`                |
| User-facing setup guide       | `README.adoc` → "Enabling SIMD" (IntelliJ + plain Jupyter recipes, kill-switch)            |

## How a notebook author uses it

```kotlin
%use skainet-notebook
checkSimd()
// SimdReport(jdkFeatureVersion=21, jdkOk=true, vectorApiAvailable=true,
//            configEnabled=true, simdActive=true, reason=Vector API kernels active)
```

If `simdActive` is `false`, `reason` names exactly which precondition is
missing. Two examples the integration surfaces verbatim:

- `jdk.incubator.vector module not loaded — start the kernel with --add-modules jdk.incubator.vector`
- `disabled by skainet.cpu.vector.enabled / SKAINET_CPU_VECTOR_ENABLED`

The kill-switch is documented under "Disabling SIMD" in `README.adoc`
and exists so benchmarks and numerical-regression triage can compare
the scalar path against the SIMD path without rebuilding the kernel.

## Mapping back to issue #60

The original asks were:

- *Add Vector API into the build.* — done via the `--add-modules`
  flag in `tasks.test`; the published shadow jar carries no JVM-args
  requirements of its own (the JVM args are necessarily set on the
  kernel, not the dependency, since `--add-modules` cannot be injected
  from a classpath jar — see the README NOTE under "SKaiNET notebook
  dependency").
- *Helper methods showing what we are using.* — done via `checkSimd()`
  (top-level in `sk.ainet.app.notebook`, also `NotebookInfo.checkSimd()`)
  returning a structured `SimdReport`, plus the auto-rendered warning
  at integration load.

## Verification

```sh
./gradlew :kotlin-notebook:test --tests 'sk.ainet.app.notebook.NotebookInfoTest'
```

`NotebookInfoTest` pins both branches of the probe: the active-SIMD
path (under `--add-modules jdk.incubator.vector`, which the build
configures for the test JVM) and the kill-switch path (with
`skainet.cpu.vector.enabled=false`).
