import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

configure<AllOpenExtension> {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    // JVM is primary benchmark target
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    // Native targets for multiplatform benchmarks
    macosArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":verdikt-engine"))
                implementation(libs.kotlinx.benchmark.runtime)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.benchmark.runtime.jvm)
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
