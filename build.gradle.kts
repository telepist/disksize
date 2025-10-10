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
        commonMain {
            dependencies {
                implementation(libs.mosaic.runtime)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// Build-only tasks for TUI app
// Note: Mosaic requires a real TTY, which Gradle cannot provide.
// Use the Makefile or run the executable directly in your terminal.

tasks.register("buildTui") {
    group = "application"
    description = "Build the TUI app (debug mode) for macOS ARM64"
    dependsOn("linkDebugExecutableMacosArm64")
}

tasks.register("buildTuiRelease") {
    group = "application"
    description = "Build the TUI app (release mode) for macOS ARM64"
    dependsOn("linkReleaseExecutableMacosArm64")
}

// Helper task to show the executable path for manual running
tasks.register("showExePath") {
    group = "application"
    description = "Show the path to the built executable"

    doLast {
        println("\nExecutable locations:")
        println("  Debug:   ${project.layout.buildDirectory.get()}/bin/macosArm64/debugExecutable/disksize.kexe")
        println("  Release: ${project.layout.buildDirectory.get()}/bin/macosArm64/releaseExecutable/disksize.kexe")
        println("\nTo run manually:")
        println("  ./build/bin/macosArm64/debugExecutable/disksize.kexe")
        println("  ./build/bin/macosArm64/releaseExecutable/disksize.kexe")
    }
}
