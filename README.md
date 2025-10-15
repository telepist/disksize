# DiskSize

A cross-platform Terminal User Interface (TUI) disk space analyzer inspired by TreeSize, built with Kotlin Multiplatform and Mosaic.

[![Build Status](https://github.com/username/disksize/workflows/Test/badge.svg)](https://github.com/username/disksize/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Overview

DiskSize helps you quickly identify which directories and files are consuming the most disk space. It provides a fast, intuitive terminal interface for navigating your filesystem and understanding space usage patterns.

**Features:**
- 🚀 Fast directory scanning
- 📊 Visual size representation with percentage bars
- 🎨 Color-coded size indicators
- ⌨️  Keyboard-driven interface
- 🔄 Cross-platform (macOS, Linux, Windows)
- 📦 Single native binary, no dependencies

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
./gradlew buildTuiRelease
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
╔═════════════════════════════════════════════════════════════╗
║ DiskSize - Disk Space Analyzer                              ║
╠═════════════════════════════════════════════════════════════╣
║ Path: /Users/username/Documents                             ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  Total Size: 15.2 GB                                        ║
║  Files: 1,234                                               ║
║  Directories: 156                                           ║
║                                                             ║
║  Subdirectories:                                            ║
║  ┌────────────────────────────────────────────────────────┐ ║
║  │ Projects/          8.5 GB  (55.9%) ████████████        │ ║
║  │ Photos/            4.2 GB  (27.6%) ██████              │ ║
║  │ Documents/         1.8 GB  (11.8%) ███                 │ ║
║  │ Downloads/         650 MB  ( 4.2%) █                   │ ║
║  │ Music/              70 MB  ( 0.5%)                     │ ║
║  └────────────────────────────────────────────────────────┘ ║
║                                                             ║
╠═════════════════════════════════════════════════════════════╣
║ [Scanning completed in 2.3s]                      q: Quit   ║
╚═════════════════════════════════════════════════════════════╝
```

## Development Roadmap

### MVP 1: Basic Directory Scanning (Current)
- ✅ Command-line directory path support
- ✅ Recursive directory scanning
- ✅ Size calculation and statistics
- ✅ Basic TUI display
- 🚧 In progress...

### MVP 2: Interactive Navigation
- Keyboard navigation (arrow keys)
- Drill down into directories
- Sort by size, name, date
- Status bar and color coding

### MVP 3: Tree Visualization
- Tree view with indentation
- Percentage bars
- File type detection
- Multiple view modes

### MVP 4: Advanced Features
- Search and filter
- Export results
- Comparison mode
- Configuration file

### MVP 5: Production Release
- Performance optimization
- All platforms tested
- Binary releases
- Installation packages

See [docs/PROJECT_PLAN.md](docs/PROJECT_PLAN.md) for detailed roadmap.

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
│   │       ├── domain/      # Business logic
│   │       ├── ui/          # TUI components
│   │       └── presentation/
│   ├── commonTest/         # Common tests
│   ├── macosMain/          # macOS-specific code
│   ├── linuxMain/          # Linux-specific code
│   └── mingwMain/          # Windows-specific code
├── build.gradle.kts        # Build configuration
└── README.md               # This file
```

### Building

```bash
# Build debug version (macOS ARM64)
make build
# or: ./gradlew buildTui

# Build release version
make build-release
# or: ./gradlew buildTuiRelease

# Build for all platforms
make build-all
```

### Running

**Important:** Mosaic TUI apps require a real terminal (TTY) and cannot be run through Gradle.

```bash
# Recommended: Use Make (builds and runs)
make run

# Or build then run manually
./gradlew buildTui
./build/bin/macosArm64/debugExecutable/disksize.kexe

# Run release version
make run-release
```

### Testing

We follow Test-Driven Development (TDD) with high test coverage:

```bash
# Run all tests
./gradlew test

# Run tests with coverage
./gradlew test koverHtmlReport

# View coverage report
open build/reports/kover/html/index.html
```

**Testing stack:**
- JUnit 5 for test framework
- MockK for mocking
- Google Truth for assertions
- Kotlinx.coroutines-test for async testing

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

## Technology Stack

- **Kotlin Multiplatform** 2.2.20 - Cross-platform development
- **Jetpack Compose** 1.8.0 - Declarative UI framework
- **Mosaic** 0.18.0 - Terminal UI library by Jake Wharton
- **Kotlinx Coroutines** - Async/concurrency
- **JUnit 5** - Testing framework
- **MockK** - Mocking library
- **Truth** - Assertion library

## Supported Platforms

- macOS (Apple Silicon & Intel)
- Linux (x64 & ARM64)
- Windows (x64 via MinGW)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Write tests for your changes
4. Ensure all tests pass (`./gradlew test`)
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

**Status:** MVP 1 in development 🚧

**Next milestone:** Basic directory scanning with static TUI display
