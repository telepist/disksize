# Makefile for Kotlin Mosaic TUI application

.PHONY: help build run clean build-all build-release run-release

# Default target
help:
	@echo "Kotlin Mosaic TUI - Available targets:"
	@echo "  make build         - Build debug executable for current platform (macOS ARM64)"
	@echo "  make run           - Build and run the TUI application"
	@echo "  make build-all     - Build debug executables for all platforms"
	@echo "  make build-release - Build release executable for current platform"
	@echo "  make run-release   - Build and run release version"
	@echo "  make clean         - Clean build artifacts"

# Build debug executable for macOS ARM64 (default platform)
build:
	./gradlew linkDebugExecutableMacosArm64

# Build and run the TUI application
run: build
	./build/bin/macosArm64/debugExecutable/kotlinHello.kexe

# Build debug executables for all platforms
build-all:
	./gradlew linkDebugExecutableMacosArm64
	./gradlew linkDebugExecutableMacosX64
	./gradlew linkDebugExecutableLinuxX64
	./gradlew linkDebugExecutableLinuxArm64
	./gradlew linkDebugExecutableMingwX64

# Build release executable for macOS ARM64
build-release:
	./gradlew linkReleaseExecutableMacosArm64

# Build and run release version
run-release: build-release
	./build/bin/macosArm64/releaseExecutable/kotlinHello.kexe

# Clean build artifacts
clean:
	./gradlew clean
	@echo "Build artifacts cleaned"
