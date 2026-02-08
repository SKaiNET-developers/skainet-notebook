pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://pkg.jetbrains.space/public/p/kotlin/kotlin-jupyter") }
    }
}

rootProject.name = "skainet-kotlin-notebook"

include("kotlin-notebook")