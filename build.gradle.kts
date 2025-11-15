plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    macosArm64 {
        binaries {
            executable {
                baseName = "disksize"
                entryPoint = "disksize.main"
            }
        }
    }
    macosX64 {
        binaries {
            executable {
                baseName = "disksize"
                entryPoint = "disksize.main"
            }
        }
    }
    linuxX64 {
        binaries {
            executable {
                baseName = "disksize"
                entryPoint = "disksize.main"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                baseName = "disksize"
                entryPoint = "disksize.main"
            }
        }
    }
    mingwX64 {
        binaries {
            executable {
                baseName = "disksize"
                entryPoint = "disksize.main"
            }
        }
    }

    // The Default Kotlin Hierarchy Template automatically creates intermediate source sets
    // (nativeMain, appleMain, linuxMain, etc.) and sets up the dependency hierarchy.
    // No manual sourceSets configuration needed!

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.mosaic.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val posixMain by creating {
            dependsOn(commonMain)
        }
        val posixTest by creating {
            dependsOn(commonTest)
        }

        val macosArm64Main by getting { dependsOn(posixMain) }
        val macosX64Main by getting { dependsOn(posixMain) }
        val linuxX64Main by getting { dependsOn(posixMain) }
        val linuxArm64Main by getting { dependsOn(posixMain) }

        val macosArm64Test by getting { dependsOn(posixTest) }
        val macosX64Test by getting { dependsOn(posixTest) }
        val linuxX64Test by getting { dependsOn(posixTest) }
        val linuxArm64Test by getting { dependsOn(posixTest) }
    }
}
