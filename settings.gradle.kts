pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("com.android.application") version "8.2.0"
        id("com.android.library") version "8.2.0"
        kotlin("multiplatform") version "2.0.21"
        kotlin("plugin.compose") version "2.0.21"
        kotlin("plugin.allopen") version "2.0.21"
        id("org.jetbrains.compose") version "1.7.1"
        id("org.jetbrains.kotlinx.benchmark") version "0.4.13"
        id("com.vanniktech.maven.publish") version "0.30.0" apply false
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
