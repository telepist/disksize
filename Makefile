# Makefile for DiskSize - Disk Space Analyzer TUI

.PHONY: help build run clean build-all build-release run-release test test-coverage

# Detect OS and architecture
# Check for Windows CMD/PowerShell vs Unix-like shells
# We use uname to detect the environment - if uname exists, we're in a Unix-like shell
UNAME_S := $(shell uname -s 2>NUL)
UNAME_M := $(shell uname -m 2>NUL)

ifeq ($(UNAME_S),)
    # uname doesn't exist - we're in Windows CMD or PowerShell
    PLATFORM := MingwX64
    PLATFORM_DIR := mingwX64
    EXECUTABLE := disksize.exe
    GRADLE := gradlew.bat
    GRADLE_END :=
    OS_NAME := Windows
else
    # Unix-like systems (macOS, Linux, Git Bash)

    ifeq ($(findstring MINGW,$(UNAME_S)),MINGW)
        # Windows (Git Bash/MSYS)
        PLATFORM := MingwX64
        PLATFORM_DIR := mingwX64
        EXECUTABLE := disksize.exe
        GRADLE := bash -c "./gradlew
        GRADLE_END := "
        OS_NAME := Windows (Git Bash)
    else ifeq ($(findstring MSYS,$(UNAME_S)),MSYS)
        # Windows (MSYS2)
        PLATFORM := MingwX64
        PLATFORM_DIR := mingwX64
        EXECUTABLE := disksize.exe
        GRADLE := bash -c "./gradlew
        GRADLE_END := "
        OS_NAME := Windows (MSYS2)
    else ifeq ($(UNAME_S),Darwin)
        # macOS
        ifeq ($(UNAME_M),arm64)
            PLATFORM := MacosArm64
            PLATFORM_DIR := macosArm64
        else
            PLATFORM := MacosX64
            PLATFORM_DIR := macosX64
        endif
        EXECUTABLE := disksize.kexe
        GRADLE := ./gradlew
        GRADLE_END :=
        OS_NAME := macOS ($(UNAME_M))
    else ifeq ($(UNAME_S),Linux)
        # Linux
        ifeq ($(UNAME_M),aarch64)
            PLATFORM := LinuxArm64
            PLATFORM_DIR := linuxArm64
        else
            PLATFORM := LinuxX64
            PLATFORM_DIR := linuxX64
        endif
        EXECUTABLE := disksize.kexe
        GRADLE := ./gradlew
        GRADLE_END :=
        OS_NAME := Linux ($(UNAME_M))
    else
        $(error Unsupported operating system: $(UNAME_S))
    endif
endif

# Build paths
DEBUG_EXECUTABLE := ./build/bin/$(PLATFORM_DIR)/debugExecutable/$(EXECUTABLE)
RELEASE_EXECUTABLE := ./build/bin/$(PLATFORM_DIR)/releaseExecutable/$(EXECUTABLE)

# Default target
help:
	@echo "DiskSize - Available targets:"
	@echo "  make run             - Build and run the TUI application (recommended)"
	@echo "  make build           - Build debug executable for current platform ($(PLATFORM))"
	@echo "  make build-all       - Build debug executables for all platforms"
	@echo "  make build-release   - Build release executable for current platform"
	@echo "  make run-release     - Build and run release version"
	@echo "  make test            - Run all tests"
	@echo "  make test-coverage   - Run tests and generate coverage report"
	@echo "  make clean           - Clean build artifacts"
	@echo ""
	@echo "Detected platform: $(PLATFORM) ($(OS_NAME))"
	@echo "Note: TUI apps must be run in a real terminal, not through Gradle tasks."

# Build debug executable for current platform
build:
	$(GRADLE) linkDebugExecutable$(PLATFORM)$(GRADLE_END)

# Build and run the TUI application
# Note: We must run the executable directly because Mosaic requires a real TTY
run: build
	@echo "Running DiskSize on $(PLATFORM)..."
	@$(DEBUG_EXECUTABLE)

# Build debug executables for all platforms
build-all:
	$(GRADLE) linkDebugExecutableMacosArm64$(GRADLE_END)
	$(GRADLE) linkDebugExecutableMacosX64$(GRADLE_END)
	$(GRADLE) linkDebugExecutableLinuxX64$(GRADLE_END)
	$(GRADLE) linkDebugExecutableLinuxArm64$(GRADLE_END)
	$(GRADLE) linkDebugExecutableMingwX64$(GRADLE_END)

# Build release executable for current platform
build-release:
	$(GRADLE) linkReleaseExecutable$(PLATFORM)$(GRADLE_END)

# Build and run release version
run-release: build-release
	@echo "Running DiskSize (release mode) on $(PLATFORM)..."
	@$(RELEASE_EXECUTABLE)

# Run all tests for current platform
test:
	$(GRADLE) $(PLATFORM_DIR)Test$(GRADLE_END)

# Run tests and generate coverage report
test-coverage:
	$(GRADLE) $(PLATFORM_DIR)Test koverHtmlReport$(GRADLE_END)
	@echo "Coverage report generated at build/reports/kover/html/index.html"

# Clean build artifacts
clean:
	$(GRADLE) clean$(GRADLE_END)
	@echo "Build artifacts cleaned"
