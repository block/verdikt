plugins {
    kotlin("multiplatform")
    `maven-publish`
    signing
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

    // All code lives in commonMain - no platform-specific implementations
    sourceSets {
        val commonMain by getting {
            dependencies {
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
            name.set("Verdikt Core")
            description.set("A Kotlin Multiplatform library for type-safe rule evaluation")
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

            scm {
                url.set("https://github.com/block/verdikt")
                connection.set("scm:git:git://github.com/block/verdikt.git")
                developerConnection.set("scm:git:ssh://git@github.com/block/verdikt.git")
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            url = uri("https://central.sonatype.com/api/v1/publisher/upload")
            credentials {
                username = System.getenv("SONATYPE_CENTRAL_USERNAME") ?: ""
                password = System.getenv("SONATYPE_CENTRAL_PASSWORD") ?: ""
            }
        }
    }
}

signing {
    val signingKey = System.getenv("GPG_SECRET_KEY")
    val signingPassword = System.getenv("GPG_SECRET_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
