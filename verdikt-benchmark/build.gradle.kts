import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.13"
}

configure<AllOpenExtension> {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    // JVM is primary benchmark target
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    // Native targets for multiplatform benchmarks
    macosArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":verdikt-engine"))
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.13")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime-jvm:0.4.13")
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
    }

    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 1000
            iterationTimeUnit = "ms"
            outputTimeUnit = "ms"
            reportFormat = "text"
        }

        register("quick") {
            warmups = 1
            iterations = 3
            iterationTime = 500
            iterationTimeUnit = "ms"
        }
    }
}
