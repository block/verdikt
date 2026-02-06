plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.14.0"
}

apiValidation {
    ignoredProjects += listOf("demo", "verdikt-benchmark")
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}
