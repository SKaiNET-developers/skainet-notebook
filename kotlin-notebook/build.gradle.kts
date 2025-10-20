plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.johnrengelman.shadow)
}

dependencies {
    implementation(libs.skainet.lang.core)
    implementation(libs.skainet.lang.models)
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
    }
}
