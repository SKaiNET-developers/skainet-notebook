plugins {
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.jupyter.api) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.jetbrains.dokka) apply false
    alias(libs.plugins.binary.compatibility.validator) apply false

}

allprojects {
    group = "sk.ainet"
}