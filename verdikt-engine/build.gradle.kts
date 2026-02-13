plugins {
    id("verdikt-kmp-library")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":verdikt-core"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Verdikt Engine")
            description.set("A forward-chaining production rules engine for Kotlin Multiplatform")
        }
    }
}
