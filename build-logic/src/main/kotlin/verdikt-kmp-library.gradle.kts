import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.dokka")
    `maven-publish`
    signing
}

kotlin {
    explicitApi()

    // JVM
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // JS
    js {
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
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaGeneratePublicationHtml"))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(javadocJar)
        pom {
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
