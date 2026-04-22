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

// SKaiNET 0.19.0 shipped with a broken POM for skainet-backend-cpu-jvm: it
// declares a runtime dependency on sk.ainet:skainet-backend-api-jvm:unspecified
// (wrong group coordinate, bogus version) that is not published anywhere. The
// backend-api module only re-exports interfaces already in skainet-lang-core,
// which we depend on directly, so excluding the bogus coordinate is safe.
configurations.configureEach {
    exclude(group = "sk.ainet", module = "skainet-backend-api")
    exclude(group = "sk.ainet", module = "skainet-backend-api-jvm")
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
        include(dependency("sk.ainet.core:skainet-data-api-jvm"))
        include(dependency("sk.ainet.core:skainet-data-basic-jvm"))
        include(dependency("sk.ainet.core:skainet-io-core-jvm"))
        include(dependency("sk.ainet.core:skainet-io-gguf-jvm"))
        include(dependency("sk.ainet.core:skainet-io-onnx-jvm"))
    }
}

// Publish the shadow uber-jar as the main artifact and drop all
// runtime deps from the POM. Upstream skainet-backend-cpu-jvm at
// 0.19.x has a broken POM (references sk.ainet:skainet-backend-api-jvm:unspecified),
// so consumers using @file:DependsOn in Kotlin Jupyter would fail to
// resolve it and end up with a classpath missing DirectCpuExecutionContext.
// Bundling everything in the uber-jar and emitting an empty <dependencies>
// block bypasses transitive resolution entirely.
tasks.named<Jar>("jar") {
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
