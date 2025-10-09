# Kotlin Multiplatform TUI Demo with Mosaic

A Kotlin Multiplatform project that demonstrates building native command line executables with Terminal User Interface (TUI) capabilities using JakeWharton's Mosaic library.

## Features

- Cross-platform native executables (macOS, Linux, Windows)
- Terminal UI with Mosaic (Jetpack Compose for terminal)
- Live-updating counter with progress bar
- Box-drawing characters for UI borders

## Requirements
- JDK 11+ (required by Mosaic 0.18.0)
- Gradle 8.10+ (wrapper included)
- Kotlin `2.2.20` with Compose plugin `1.8.0`

## Quick Start

### Using Make (Recommended)

```shell
# Build and run the TUI application
make run

# Or just build
make build

# See all available targets
make help
```

### Using Gradle Directly

```shell
# Build for macOS Apple Silicon
./gradlew linkDebugExecutableMacosArm64

# Run the executable directly (requires interactive terminal)
./build/bin/macosArm64/debugExecutable/kotlinHello.kexe
```

**Note:** Mosaic TUI apps require an interactive terminal (TTY) and cannot run through Gradle's task runner. Build the executable and run it directly in your terminal.

### Build Commands for Other Platforms

```shell
# macOS Intel
./gradlew linkDebugExecutableMacosX64

# Linux x64
./gradlew linkDebugExecutableLinuxX64

# Linux ARM64
./gradlew linkDebugExecutableLinuxArm64

# Windows x64
./gradlew linkDebugExecutableMingwX64
```

## Building Release Binaries

Build optimized native binaries (outputs to `build/bin/<target>/releaseExecutable/`):

```shell
# macOS Apple Silicon
./gradlew linkReleaseExecutableMacosArm64

# macOS Intel
./gradlew linkReleaseExecutableMacosX64

# Linux x64
./gradlew linkReleaseExecutableLinuxX64

# Linux ARM64
./gradlew linkReleaseExecutableLinuxArm64

# Windows x64
./gradlew linkReleaseExecutableMingwX64
```

## What It Does

The TUI application displays:
- A **cyan** bordered header with box-drawing characters (╔═╗║╚╝)
- A live-updating counter that increments every 500ms (green → yellow at 10)
- A **blue/green** status indicator (Running → Completed)
- A **magenta** progress bar that fills from 0% to 100%
- A **green** completion message when finished
- Full ANSI color support for terminal output

## Project Structure
- `src/commonMain/kotlin/helloworld/main.kt` - TUI application with Mosaic composables
- `build.gradle.kts` - Kotlin Multiplatform configuration with Compose and Mosaic dependencies
- `settings.gradle.kts` - Repository configuration for Compose Maven repos
- `Makefile` - Convenient build and run commands

## Makefile Targets

| Command | Description |
|---------|-------------|
| `make help` | Show all available targets |
| `make build` | Build debug executable for macOS ARM64 |
| `make run` | Build and run the TUI application |
| `make build-all` | Build debug executables for all platforms |
| `make build-release` | Build optimized release executable |
| `make run-release` | Build and run release version |
| `make clean` | Clean build artifacts |

## Technologies Used
- **Kotlin Multiplatform** `2.2.20` - Cross-platform Kotlin
- **Jetpack Compose for Kotlin** `1.8.0` - Declarative UI framework
- **Mosaic** `0.18.0` - Terminal UI library by Jake Wharton

## Supported Targets
- macOS ARM64 (Apple Silicon)
- macOS x64 (Intel)
- Linux x64
- Linux ARM64
- Windows x64 (MinGW)
