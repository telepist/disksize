# Makefile for DiskSize - Disk Space Analyzer TUI

.PHONY: help build run clean build-all build-release run-release test test-coverage

# Default target
help:
	@echo "DiskSize - Available targets:"
	@echo "  make run             - Build and run the TUI application (recommended)"
	@echo "  make build           - Build debug executable for current platform (macOS ARM64)"
	@echo "  make build-all       - Build debug executables for all platforms"
	@echo "  make build-release   - Build release executable for current platform"
	@echo "  make run-release     - Build and run release version"
	@echo "  make test            - Run all tests"
	@echo "  make test-coverage   - Run tests and generate coverage report"
	@echo "  make clean           - Clean build artifacts"
	@echo ""
	@echo "Note: TUI apps must be run in a real terminal, not through Gradle tasks."

# Build debug executable for macOS ARM64 (default platform)
build:
	./gradlew linkDebugExecutableMacosArm64

# Build and run the TUI application
# Note: We must run the executable directly because Mosaic requires a real TTY
run: build
	@echo "Running DiskSize..."
	@./build/bin/macosArm64/debugExecutable/disksize.kexe

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
	@echo "Running DiskSize (release mode)..."
	@./build/bin/macosArm64/releaseExecutable/disksize.kexe

# Run all tests
test:
	./gradlew test

# Run tests and generate coverage report
test-coverage:
	./gradlew test koverHtmlReport
	@echo "Coverage report generated at build/reports/kover/html/index.html"

# Clean build artifacts
clean:
	./gradlew clean
	@echo "Build artifacts cleaned"
