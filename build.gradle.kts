plugins {
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.jupyter.api) apply false
}

allprojects {
    group = "sk.ainet"
}