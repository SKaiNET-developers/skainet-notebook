import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.binary.compatibility.validator)
}

kotlin {
    explicitApi()

    jvmToolchain(21)

    android {
        namespace = "sk.ainet.app.notebook.extensions"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    linuxX64()
    linuxArm64()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        // The DOT renderer's test suite uses JUnit 5 (`useJUnitPlatform`)
        // because kotlin-notebook does the same; matching keeps the
        // assertion DSL and runner consistent across modules.
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // The DOT renderer currently lives in jvmMain only; commonMain
            // stays dependency-free so future cross-platform extractions
            // (e.g. a browser-wasm renderer) can grow into it cleanly.
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
            // Pure-Kotlin Wasm 3.0 runtime that executes the bundled
            // graphviz.wasm on the JVM kernel — no JS, no CDN.
            implementation(libs.chasm.runtime)
            implementation(libs.weh.bindings.chasm.wasip1)
            implementation(libs.weh.bindings.chasm.emscripten)
            // `renderDot` returns `MimeTypedResult` so notebook integrations
            // can hand its output straight to a cell. Standalone consumers
            // that don't depend on Kotlin Jupyter can call `GraphvizWasm`
            // directly for raw SVG.
            api(libs.kotlin.jupyter.api)
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
        }
    }
}
