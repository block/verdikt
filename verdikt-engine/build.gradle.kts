plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    explicitApi()

    // JVM
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // JS
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        nodejs()
    }

    // iOS
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // macOS
    macosX64()
    macosArm64()

    // Linux
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":verdikt-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("Verdikt Engine")
            description.set("A forward-chaining production rules engine for Kotlin Multiplatform")
            url.set("https://github.com/block/verdikt")

            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }

            developers {
                developer {
                    id.set("block")
                    name.set("Block, Inc.")
                    url.set("https://block.xyz")
                }
            }
        }
    }
}
