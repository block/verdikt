plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.binary.compatibility.validator)
    `maven-publish`
    signing
}

apiValidation {
    ignoredProjects += listOf("demo", "verdikt-benchmark")
}

allprojects {
    group = "xyz.block"
    version = "0.2.0"

    repositories {
        mavenCentral()
        google()
    }
}
