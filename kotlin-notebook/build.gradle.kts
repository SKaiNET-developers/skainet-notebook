import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.bundling.Jar

plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.jetbrains.dokka)
    alias(libs.plugins.jupyter.api)
}

val generateVersionInfo by tasks.registering {
    val version = libs.versions.skainet.get()
    val outputDir = layout.buildDirectory.dir("generated/kotlin/sk/ainet/app/notebook")
    inputs.property("version", version)
    outputs.dir(outputDir)

    doLast {
        val versionFile = outputDir.get().file("Version.kt").asFile
        versionFile.parentFile.mkdirs()
        versionFile.writeText(
            """
            package sk.ainet.app.notebook

            internal object GeneratedVersion {
                const val VERSION = "$version"
            }
            """.trimIndent()
        )
    }
}

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir(generateVersionInfo)
    }
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

repositories {
    mavenCentral()
    maven { url = uri("https://pkg.jetbrains.space/public/p/kotlin/kotlin-jupyter") }
}

configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlin-jupyter-api")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlin-jupyter-api-annotations")
}

dependencies {
    compileOnly("org.jetbrains.kotlinx:kotlin-jupyter-api:${libs.versions.kotlinJupyter.get()}")
    
    val skainetExclusions: ExternalModuleDependency.() -> Unit = {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlin-jupyter-api")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlin-jupyter-api-annotations")
    }

    api(libs.skainet.lang.core, skainetExclusions)
    api(libs.skainet.lang.models, skainetExclusions)
    api(libs.skainet.model.yolo, skainetExclusions)
    api(libs.skainet.lang.kan, skainetExclusions)
    api(libs.skainet.lang.dag, skainetExclusions)
    api(libs.skainet.compile.core, skainetExclusions)
    api(libs.skainet.compile.dag, skainetExclusions)
    api(libs.skainet.backend.cpu, skainetExclusions)
    api(libs.skainet.data.api, skainetExclusions)
    api(libs.skainet.data.simple, skainetExclusions)
    api(libs.skainet.io.core, skainetExclusions)
    api(libs.skainet.io.gguf, skainetExclusions)
    api(libs.skainet.io.onnx, skainetExclusions)

    skainetSources(libs.skainet.lang.core)
    skainetSources(libs.skainet.lang.models)
    skainetSources(libs.skainet.lang.kan)
    skainetSources(libs.skainet.lang.dag)
    skainetSources(libs.skainet.compile.core)
    skainetSources(libs.skainet.compile.dag)
    skainetSources(libs.skainet.backend.cpu)
    skainetSources(libs.skainet.data.api)
    skainetSources(libs.skainet.data.simple)
    skainetSources(libs.skainet.model.yolo)
    skainetSources(libs.skainet.io.core)
    skainetSources(libs.skainet.io.gguf)
    skainetSources(libs.skainet.io.onnx)

    testImplementation(kotlin("test"))
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}



val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(kotlin.sourceSets.main.get().kotlin)

    // Also package sources from SKaiNET dependencies (if they publish sources)
    // We filter to ensure we only try to open valid JAR/ZIP files
    from(providers.provider {
        configurations["skainetSources"].resolvedConfiguration.resolvedArtifacts
            .map { it.file }
            .filter { it.extension == "jar" || it.extension == "zip" }
            .mapNotNull { 
                try {
                    zipTree(it)
                } catch (e: Exception) {
                    println("Warning: Could not read $it as zipTree: ${e.message}")
                    null
                }
            }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

artifacts {
    add("archives", sourcesJar)
}

// No shadowJar needed. We'll use a regular JAR and the maven-publish plugin will handle dependencies in POM.

tasks.jar {
    archiveClassifier.set("")
}
