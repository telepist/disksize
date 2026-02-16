# DiskSize

A cross-platform Terminal User Interface (TUI) disk space analyzer inspired by TreeSize, built with Kotlin Multiplatform and Mosaic.

[![Build Status](https://github.com/username/disksize/workflows/Test/badge.svg)](https://github.com/username/disksize/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Overview

DiskSize helps you quickly identify which directories and files are consuming the most disk space. It provides a fast, intuitive terminal interface for navigating your filesystem and understanding space usage patterns.

**Features:**
- Fast directory scanning with real-time progress
- Tree view with expandable/collapsible directory nodes
- Visual size representation with percentage bars
- Color-coded size indicators
- Keyboard-driven interface with vim-style shortcuts
- Page Up/Down, Home/End navigation
- Cross-platform (macOS, Linux, Windows)
- Single native binary, no dependencies
- Interactive navigation through directory hierarchy
- Refresh current directory with intelligent caching
- Delete files/directories with confirmation
- View both files and directories
- Multiple sort modes (size, name, date)

## Quick Start

### Installation

**macOS (Homebrew - coming soon):**
```bash
brew install disksize
```

**From source:**
```bash
git clone https://github.com/username/disksize.git
cd disksize
make build-release
# Binary will be in build/bin/<platform>/releaseExecutable/
```

### Basic Usage

Analyze the current directory:
```bash
disksize
```

Analyze a specific directory:
```bash
disksize /path/to/directory
disksize ~/Documents
```

### Example Output
```
╔═══════════════════════════════════════════════════════════════════════════════════╗
║                    DiskSize - Disk Space Analyzer                                 ║
╠═══════════════════════════════════════════════════════════════════════════════════╣
║Path: /Users/username/Documents                                                    ║
║                                                                                   ║
║Total Size: 15.2 GB                                                                ║
║Files: 1,234                                                                       ║
║Directories: 156                                                                   ║
║                                                                                   ║
║Entries (Sort: Size ↓)                                                             ║
║> ▾ Projects/                               8.5 GB (55.9%) ▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░║
║  ├── ▸ my-app/                             3.2 GB (37.6%) ▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░║
║  ├── ▸ website/                            2.1 GB (24.7%) ▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░║
║  └── ▸ scripts/                            890 MB (10.5%) ▓▓▓░░░░░░░░░░░░░░░░░░░░░║
║  ▸ Photos/                                 4.2 GB (27.6%) ▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░║
║  ▸ Documents/                              1.8 GB (11.8%) ▓▓▓░░░░░░░░░░░░░░░░░░░░░║
║  ▸ Downloads/                              650 MB (4.3%)  ▓░░░░░░░░░░░░░░░░░░░░░░░║
║    Music/                                  70 MB (0.5%)                           ║
║    README.md                               12 KB                                  ║
║                                                                                   ║
╠═══════════════════════════════════════════════════════════════════════════════════╣
║Scan completed in 2.3s     Enter: Expand  s: Sort  r: Refresh  Del: Delete  q: Quit║
╚═══════════════════════════════════════════════════════════════════════════════════╝
```

- `>` marks the selected entry (highlighted with a green background)
- `▾` expanded directory, `▸` collapsed directory (press Enter to toggle)
- `├──` / `└──` tree lines show nested items under expanded directories
- Usage bars show relative size; `▓` filled, `░` empty

## What's Done

- Recursive directory scanning with streaming progress and elapsed time
- Tree view with expandable/collapsible directory nodes and tree-line prefixes
- Interactive keyboard navigation (arrow keys, vim-style `hjkl`)
- Page Up/Down and Home/End navigation
- Sort by size, name, or date (`s` key)
- Color-coded sizes with percentage bars
- Green background highlight for selected entry
- File and directory listing with symlink detection
- Refresh current directory with cache preservation (`r` key)
- Delete files/directories with confirmation dialog (Delete key)
- Deletion progress with spinner animation
- Comprehensive error handling (permission denied, IO errors, etc.)
- Cross-platform: macOS (ARM64/x64), Linux (x64/ARM64), Windows (x64)
- Cross-platform `make install` / `make uninstall` with `Makefile.local` support

## TODO

- File type detection and icons
- Multiple view modes (tree, list, details)
- Search and filter
- Export results (CSV, JSON)
- Ignore patterns (.gitignore support)
- Configuration file
- Command-line options (--help, --version, --hidden, --max-depth, etc.)
- Parallel scanning
- In-app help system
- Binary releases and installation packages

## Documentation

- **[Project Plan](docs/PROJECT_PLAN.md)** - Vision, roadmap, and development approach
- **[Architecture](docs/ARCHITECTURE.md)** - Technical design and structure
- **[UX Design](docs/UX_DESIGN.md)** - User interface and interaction design
- **[Testing Strategy](docs/TESTING_STRATEGY.md)** - Testing approach and guidelines
- **[User Manual](docs/USER_MANUAL.md)** - End-user documentation

## Development

### Prerequisites
- JDK 11+ (required by Mosaic 0.18.0)
- Gradle 8.10+ (wrapper included)
- Kotlin 2.2.20

### Project Structure
```
disksize/
├── docs/                    # Project documentation
├── src/
│   ├── commonMain/         # Platform-independent code
│   │   └── kotlin/disksize/
│   │       ├── data/        # Repository interfaces
│   │       ├── domain/      # Business logic & models
│   │       ├── presentation/# UI state management
│   │       ├── ui/          # TUI composable components
│   │       └── util/        # Formatting utilities
│   ├── commonTest/         # Common tests
│   ├── posixMain/          # Shared macOS/Linux code (entry point, POSIX repo)
│   ├── macosArm64Main/     # macOS ARM64 platform glue
│   ├── macosX64Main/       # macOS x64 platform glue
│   ├── linuxX64Main/       # Linux x64 platform glue
│   ├── linuxArm64Main/     # Linux ARM64 platform glue
│   └── mingwX64Main/       # Windows-specific code (entry point, Win API repo)
├── build.gradle.kts        # Build configuration
└── README.md               # This file
```

### Building

```bash
# Build debug version for current platform
make build
# or: ./gradlew linkDebugExecutable<Platform>

# Build release version
make build-release
# or: ./gradlew linkReleaseExecutable<Platform>

# Build for all platforms
make build-all
```

### Running

**Important:** Mosaic TUI apps require a real terminal (TTY) and cannot be run through Gradle.

```bash
# Recommended: Use Make (builds and runs)
make run

# Or build then run manually
make build
./build/bin/<platform>/debugExecutable/disksize.kexe  # macOS/Linux
./build/bin/mingwX64/debugExecutable/disksize.exe     # Windows

# Run release version
make run-release
```

### Testing

We follow Test-Driven Development (TDD) with high test coverage:

```bash
# Run all tests for current platform
make test

# Run tests with coverage
make test-coverage

# View coverage report
open build/reports/kover/html/index.html
```

**Testing stack:**
- `kotlin.test` for test framework and assertions
- `kotlinx-coroutines-test` for async testing

**Coverage goals:**
- Overall: 80%+
- Domain layer: 90%+
- See [docs/TESTING_STRATEGY.md](docs/TESTING_STRATEGY.md) for details

### Code Quality

We maintain high code quality standards:
- Clean code principles
- TDD approach
- Comprehensive test coverage
- Clear documentation
- Regular refactoring

### Performance Testing

Measure and track scanning performance:

```bash
# Run quick benchmark
./perf-test.sh -s small -r 3

# Create baseline
./perf-test.sh -s medium -r 5 -o perf-results/baseline.txt

# Compare after optimization
./perf-test.sh -s medium -r 5 -b perf-results/baseline.txt
```

See [docs/PERFORMANCE_TESTING.md](docs/PERFORMANCE_TESTING.md) for detailed guide.

## Technology Stack

- **Kotlin Multiplatform** 2.2.20 - Cross-platform development
- **JetBrains Compose** 1.8.0 - Declarative UI framework (Compose Multiplatform)
- **Mosaic** 0.18.0 - Terminal UI library by Jake Wharton
- **Kotlinx Coroutines** 1.9.0 - Async/concurrency
- **kotlin.test** - Test framework and assertions
- **kotlinx-coroutines-test** - Coroutine test utilities

## Supported Platforms

- macOS (Apple Silicon & Intel)
- Linux (x64 & ARM64)
- Windows (x64 via MinGW)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Write tests for your changes
4. Ensure all tests pass (`make test`)
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [Mosaic](https://github.com/JakeWharton/mosaic) by Jake Wharton - Terminal UI framework
- Inspired by [TreeSize](https://www.jam-software.com/treesize) and [WinDirStat](https://windirstat.net/)

## Contact

- Report issues: [GitHub Issues](https://github.com/username/disksize/issues)
- Project documentation: [docs/](docs/)

---

**Status:** Core features and tree navigation complete. Working on advanced features.
