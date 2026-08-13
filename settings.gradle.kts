// Central - settings gradle file, meant to specify repos, etc. 

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
    }
}
rootProject.name = "IQDialer"
include(":app")

// Yare Yare 😎