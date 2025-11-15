plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.johnrengelman.shadow)
    alias(libs.plugins.jetbrains.dokka)
}

dependencies {
    implementation(libs.skainet.lang.core)
    implementation(libs.skainet.compile.core)
    implementation(libs.skainet.backend.cpu)
    implementation(libs.skainet.lang.models)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    archiveClassifier.set("")

    // Include all skainet dependencies including JVM variants
    dependencies {
        include(dependency("sk.ainet.core:skainet-lang-core"))
        include(dependency("sk.ainet.core:skainet-lang-models"))
        include(dependency("sk.ainet.core:skainet-lang-core-jvm"))
        include(dependency("sk.ainet.core:skainet-lang-models-jvm"))
        include(dependency("sk.ainet.core:skainet-compile-core"))
        include(dependency("sk.ainet.core:skainet-backend-cpu"))
    }
}
