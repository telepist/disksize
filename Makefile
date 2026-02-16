# Makefile for DiskSize - Disk Space Analyzer TUI

.PHONY: help build run clean build-all build-release run-release test test-coverage install uninstall

# Detect OS and architecture
# On Windows, rely on the OS / MSYSTEM environment variables so we don't need uname.
# On Unix-like systems, use uname to differentiate macOS vs Linux and architecture.
ifeq ($(OS),Windows_NT)
    # Windows family (CMD, PowerShell, Git Bash, MSYS2)

    ifneq ($(MSYSTEM),)
        # Windows (Git Bash / MSYS shells)
        PLATFORM := MingwX64
        PLATFORM_DIR := mingwX64
        EXECUTABLE := disksize.exe
        GRADLE := bash -c "./gradlew
        GRADLE_END := "
        OS_NAME := Windows ($(MSYSTEM))
    else
        # Windows CMD or PowerShell
        PLATFORM := MingwX64
        PLATFORM_DIR := mingwX64
        EXECUTABLE := disksize.exe
        GRADLE := gradlew.bat
        GRADLE_END :=
        OS_NAME := Windows
    endif
else
    # Unix-like systems (macOS, Linux)
    UNAME_S := $(shell uname -s 2>/dev/null)
    UNAME_M := $(shell uname -m 2>/dev/null)

    ifeq ($(UNAME_S),Darwin)
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

# Local overrides (optional — create Makefile.local to set INSTALL_DIR etc.)
-include Makefile.local

# Install paths
INSTALL_DIR ?= /usr/local/bin
INSTALL_NAME := disksize

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
	@echo "  make install         - Install release binary to $(INSTALL_DIR)"
	@echo "  make uninstall       - Remove installed binary from $(INSTALL_DIR)"
	@echo ""
	@echo "Detected platform: $(PLATFORM) ($(OS_NAME))"
	@echo "Note: TUI apps must be run in a real terminal, not through Gradle tasks."
	@echo ""
	@echo "To customize INSTALL_DIR, create Makefile.local with:"
	@echo "  INSTALL_DIR = /your/preferred/path"

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

# Install binary to system
install: build-release
ifeq ($(OS),Windows_NT)
  ifeq ($(INSTALL_DIR),/usr/local/bin)
	@echo "Error: INSTALL_DIR must be set on Windows."
	@echo "Create Makefile.local with:"
	@echo "  INSTALL_DIR = C:/Users/yourname/bin"
	@exit 1
  endif
	@echo "Installing $(INSTALL_NAME) to $(INSTALL_DIR)..."
	@if [ ! -d "$(INSTALL_DIR)" ]; then \
		echo "Error: $(INSTALL_DIR) does not exist"; \
		exit 1; \
	fi
	@cp $(RELEASE_EXECUTABLE) $(INSTALL_DIR)/$(INSTALL_NAME).exe
	@echo "Installed $(INSTALL_NAME) to $(INSTALL_DIR)/$(INSTALL_NAME).exe"
	@echo "  Make sure $(INSTALL_DIR) is in your PATH."
else
	@echo "Installing $(INSTALL_NAME) to $(INSTALL_DIR)..."
	@if [ ! -d "$(INSTALL_DIR)" ]; then \
		echo "Error: $(INSTALL_DIR) does not exist"; \
		exit 1; \
	fi
	@if [ ! -w "$(INSTALL_DIR)" ]; then \
		echo "Error: $(INSTALL_DIR) is not writable. Try: sudo make install"; \
		exit 1; \
	fi
	@install -m 755 $(RELEASE_EXECUTABLE) $(INSTALL_DIR)/$(INSTALL_NAME)
	@echo "Installed $(INSTALL_NAME) to $(INSTALL_DIR)/$(INSTALL_NAME)"
	@echo "  You can now run: $(INSTALL_NAME)"
endif

# Uninstall binary from system
uninstall:
ifeq ($(OS),Windows_NT)
  ifeq ($(INSTALL_DIR),/usr/local/bin)
	@echo "Error: INSTALL_DIR must be set on Windows."
	@echo "Create Makefile.local with:"
	@echo "  INSTALL_DIR = C:/Users/yourname/bin"
	@exit 1
  endif
	@echo "Uninstalling $(INSTALL_NAME) from $(INSTALL_DIR)..."
	@if [ ! -f "$(INSTALL_DIR)/$(INSTALL_NAME).exe" ]; then \
		echo "Error: $(INSTALL_NAME) is not installed in $(INSTALL_DIR)"; \
		exit 1; \
	fi
	@rm -f $(INSTALL_DIR)/$(INSTALL_NAME).exe
	@echo "Uninstalled $(INSTALL_NAME) from $(INSTALL_DIR)"
else
	@echo "Uninstalling $(INSTALL_NAME) from $(INSTALL_DIR)..."
	@if [ ! -f "$(INSTALL_DIR)/$(INSTALL_NAME)" ]; then \
		echo "Error: $(INSTALL_NAME) is not installed in $(INSTALL_DIR)"; \
		exit 1; \
	fi
	@if [ ! -w "$(INSTALL_DIR)/$(INSTALL_NAME)" ]; then \
		echo "Error: Cannot remove $(INSTALL_DIR)/$(INSTALL_NAME). Try: sudo make uninstall"; \
		exit 1; \
	fi
	@rm -f $(INSTALL_DIR)/$(INSTALL_NAME)
	@echo "Uninstalled $(INSTALL_NAME) from $(INSTALL_DIR)"
endif
