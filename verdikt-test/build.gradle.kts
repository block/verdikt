plugins {
    id("verdikt-kmp-library")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":verdikt-core"))
                implementation(kotlin("test"))
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
            name.set("Verdikt Test")
            description.set("Testing utilities for Verdikt rule engine")
        }
    }
}
