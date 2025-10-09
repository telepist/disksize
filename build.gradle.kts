plugins {
    kotlin("multiplatform") version "2.2.20"
    id("org.jetbrains.compose") version "1.8.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
}

kotlin {
    macosArm64 {
        binaries {
            executable {
                baseName = "kotlinHello"
                entryPoint = "helloworld.main"
            }
        }
    }
    macosX64 {
        binaries {
            executable {
                baseName = "kotlinHello"
                entryPoint = "helloworld.main"
            }
        }
    }
    linuxX64 {
        binaries {
            executable {
                baseName = "kotlinHello"
                entryPoint = "helloworld.main"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                baseName = "kotlinHello"
                entryPoint = "helloworld.main"
            }
        }
    }
    mingwX64 {
        binaries {
            executable {
                baseName = "kotlinHello"
                entryPoint = "helloworld.main"
            }
        }
    }

    // The Default Kotlin Hierarchy Template automatically creates intermediate source sets
    // (nativeMain, appleMain, linuxMain, etc.) and sets up the dependency hierarchy.
    // No manual sourceSets configuration needed!

    sourceSets {
        commonMain {
            dependencies {
                implementation("com.jakewharton.mosaic:mosaic-runtime:0.18.0")
            }
        }
    }
}

// Convenience alias tasks for executables.

// macOS
tasks.register("runDebugMacosArm64") {
    dependsOn("runDebugExecutableMacosArm64")
}

tasks.register("runDebugMacosX64") {
    dependsOn("runDebugExecutableMacosX64")
}

// Linux
tasks.register("runDebugLinuxX64") {
    dependsOn("runDebugExecutableLinuxX64")
}

tasks.register("runDebugLinuxArm64") {
    dependsOn("runDebugExecutableLinuxArm64")
}

// Windows
tasks.register("runDebugWindows") {
    dependsOn("runDebugExecutableMingwX64")
}
