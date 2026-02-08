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
    implementation(libs.skainet.lang.kan)
    implementation(libs.skainet.lang.dag)
    implementation(libs.skainet.compile.core)
    implementation(libs.skainet.compile.dag)
    implementation(libs.skainet.backend.cpu)
    implementation(libs.skainet.data.api)
    implementation(libs.skainet.data.simple)
    implementation(libs.skainet.io.core)
    implementation(libs.skainet.io.gguf)
    implementation(libs.skainet.io.onnx)
    implementation(libs.skainet.app.kllama)



    // Resolve sources for SKaiNET libraries to package into our -sources.jar
    add("skainetSources", libs.skainet.lang.core)
    add("skainetSources", libs.skainet.lang.models)
    add("skainetSources", libs.skainet.lang.kan)
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
    add("skainetSources", libs.skainet.app.kllama)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
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
        include(dependency("sk.ainet.core:skainet-lang-kan-jvm"))
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
        include(dependency("sk.ainet.core:skainet-data-simple-jvm"))
        include(dependency("sk.ainet.core:skainet-io-core-jvm"))
        include(dependency("sk.ainet.core:skainet-io-gguf-jvm"))
        include(dependency("sk.ainet.core:skainet-io-onnx-jvm"))
        include(dependency("sk.ainet.core:skainet-apps-kllama-jvm"))
    }
}
