pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "verdikt"
include(":verdikt-core")
include(":verdikt-test")
include(":verdikt-engine")
include(":verdikt-benchmark")
include(":demo")
