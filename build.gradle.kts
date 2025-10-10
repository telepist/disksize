plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
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

        val macosArm64Main by getting
        val macosX64Main by getting
        val linuxX64Main by getting
        val linuxArm64Main by getting

        val macosArm64Test by getting
        val macosX64Test by getting
        val linuxX64Test by getting
        val linuxArm64Test by getting
    }
}

// Share POSIX-specific native code between macOS and Linux targets without duplicating files.
val posixSharedDir = "${project.projectDir}/src/posixMain/kotlin"
kotlin.sourceSets.named("macosArm64Main") { kotlin.srcDir(posixSharedDir) }
kotlin.sourceSets.named("macosX64Main") { kotlin.srcDir(posixSharedDir) }
kotlin.sourceSets.named("linuxX64Main") { kotlin.srcDir(posixSharedDir) }
kotlin.sourceSets.named("linuxArm64Main") { kotlin.srcDir(posixSharedDir) }
