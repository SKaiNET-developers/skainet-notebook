import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.bundling.Jar

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.shadow.jar)
    alias(libs.plugins.jetbrains.dokka)
    alias(libs.plugins.jupyter.api)
}

// Configuration to resolve source JARs for dependencies (we'll include only SKaiNET libs)
val skainetSources by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    // Ask specifically for the 'sources' variants
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

dependencies {
    implementation(libs.skainet.lang.core)
    implementation(libs.skainet.lang.models)
    implementation(libs.skainet.model.yolo)
    implementation(libs.skainet.lang.dag)
    implementation(libs.skainet.compile.core)
    implementation(libs.skainet.compile.dag)
    implementation(libs.skainet.backend.cpu)
    implementation(libs.skainet.data.api)
    implementation(libs.skainet.data.simple)
    implementation(libs.skainet.io.core)
    implementation(libs.skainet.io.gguf)
    implementation(libs.skainet.io.onnx)

    // JVM-side WebAssembly runtime for the Graphviz cell renderer. The bundled
    // graphviz.wasm runs entirely on the kernel JVM via chasm; nothing in the
    // notebook frontend executes JS to render a graph.
    implementation(libs.chasm.runtime)
    implementation(libs.weh.bindings.chasm.wasip1)
    implementation(libs.weh.bindings.chasm.emscripten)

    // Resolve sources for SKaiNET libraries to package into our -sources.jar
    add("skainetSources", libs.skainet.lang.core)
    add("skainetSources", libs.skainet.lang.models)
    add("skainetSources", libs.skainet.lang.dag)
    add("skainetSources", libs.skainet.compile.core)
    add("skainetSources", libs.skainet.compile.dag)
    add("skainetSources", libs.skainet.backend.cpu)
    add("skainetSources", libs.skainet.data.api)
    add("skainetSources", libs.skainet.data.simple)
    add("skainetSources", libs.skainet.model.yolo)
    add("skainetSources", libs.skainet.io.core)
    add("skainetSources", libs.skainet.io.gguf)
    add("skainetSources", libs.skainet.io.onnx)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // SKaiNET's CPU backend uses the JDK Vector API (jdk.incubator.vector) for
    // SIMD-accelerated kernels. The module is not in the default module graph,
    // so without --add-modules the runtime probe in NotebookInfoTest would
    // always see "Vector API not available" and we'd never exercise the
    // active-path branch.
    jvmArgs("--add-modules", "jdk.incubator.vector")
}

tasks.processJupyterApiResources {
    libraryProducers = listOf("sk.ainet.app.notebook.integration.SKaiNETJupyterIntegration")
}

kotlin {
    jvmToolchain(21)
}


val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(kotlin.sourceSets.main.get().kotlin)

    // Also package sources from SKaiNET dependencies (if they publish sources)
    from(providers.provider {
        configurations["skainetSources"].resolve().map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

artifacts {
    add("archives", sourcesJar)
}

tasks.shadowJar {
    archiveClassifier.set("")

    // Include all skainet dependencies including JVM variants
    dependencies {
        include(dependency("sk.ainet.core:skainet-lang-core"))
        include(dependency("sk.ainet.core:skainet-lang-models"))
        include(dependency("sk.ainet.core:skainet-lang-core-jvm"))
        include(dependency("sk.ainet.core:skainet-lang-models-jvm"))
        include(dependency("sk.ainet.core:skainet-model-yolo-jvm"))
        include(dependency("sk.ainet.core:skainet-lang-dag"))
        include(dependency("sk.ainet.core:skainet-lang-dag-jvm"))
        include(dependency("sk.ainet.core:skainet-compile-core"))
        include(dependency("sk.ainet.core:skainet-compile-core-jvm"))
        include(dependency("sk.ainet.core:skainet-compile-dag"))
        include(dependency("sk.ainet.core:skainet-compile-dag-jvm"))
        include(dependency("sk.ainet.core:skainet-backend-cpu"))
        include(dependency("sk.ainet.core:skainet-compile-core-jvm"))
        include(dependency("sk.ainet.core:skainet-backend-cpu-jvm"))
        include(dependency("sk.ainet.core:skainet-backend-api-jvm"))
        include(dependency("sk.ainet.core:skainet-lang-ksp-annotations-jvm"))
        include(dependency("sk.ainet.core:skainet-data-api-jvm"))
        include(dependency("sk.ainet.core:skainet-data-basic-jvm"))
        include(dependency("sk.ainet.core:skainet-io-core-jvm"))
        include(dependency("sk.ainet.core:skainet-io-gguf-jvm"))
        include(dependency("sk.ainet.core:skainet-io-onnx-jvm"))

        // Bundle the wasm runtime so notebook consumers resolving the
        // published kotlin-notebook jar via @file:DependsOn don't need any
        // additional repositories. The POM is rewritten to drop runtime deps,
        // so chasm + weh have to live inside the shaded artifact.
        include(dependency("io.github.charlietap.chasm:.*"))
        include(dependency("at.released.weh:.*"))
    }
}

// Publish the shadow uber-jar as the main artifact and drop all runtime deps
// from the POM. Kotlin Jupyter's `@file:DependsOn` resolves transitively from
// the published POM, so a single self-contained jar with an empty
// <dependencies> block is the most reliable distribution shape for notebook
// consumers and sidesteps any future POM-coordinate breakage in upstream
// SKaiNET modules.
tasks.named<Jar>("jar") {
    enabled = false
}

// Gradle module metadata (`.module`) takes precedence over the POM for any
// resolver that knows how to read it — including the Kotlin Jupyter kernel.
// The default `.module` for this project lists apiElements/runtimeElements
// variants whose `files[]` are empty (because `jar` is disabled above), and
// the actual shadow jar lives only in a `shadowRuntimeElements` variant that
// Kotlin Jupyter doesn't select. The consumer ends up downloading our
// transitive deps without our main artifact — so imports like
// `sk.ainet.app.notebook.display.*` fail to resolve in cell compilation.
//
// Disabling module metadata generation forces resolvers back to the POM,
// which correctly points at the shadow jar (empty dependencies + a single
// .jar artifact = exactly what we want for notebook consumers).
tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

afterEvaluate {
    publishing.publications.withType<MavenPublication>().configureEach {
        artifacts.toList()
            .filter { it.classifier.isNullOrEmpty() && it.extension == "jar" }
            .forEach { artifacts.remove(it) }
        artifact(tasks.shadowJar) { classifier = "" }

        pom.withXml {
            val root = asNode()
            (root.get("dependencies") as? groovy.util.NodeList)
                ?.toList()
                ?.forEach { root.remove(it as groovy.util.Node) }
        }
    }
}
