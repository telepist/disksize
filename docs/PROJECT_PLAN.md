# DiskSize - Project Plan

## Vision
Build a TreeSize-inspired Terminal User Interface (TUI) application for scanning and analyzing disk space usage. The application will be cross-platform (macOS, Linux, Windows) and provide an intuitive, interactive way to visualize and explore disk usage.

## Goals
- **Primary**: Create a fast, accurate disk space analyzer with an intuitive TUI
- **Quality**: Clean code, Test-Driven Development (TDD), comprehensive test coverage
- **Platform**: True multiplatform support using Kotlin/Native
- **User Experience**: Responsive, keyboard-driven interface inspired by TreeSize

## Development Approach
- **Methodology**: Agile, iterative development
- **Release Strategy**: MVP-based incremental releases
- **Quality Assurance**: TDD with comprehensive unit and integration tests
- **Documentation**: Living documentation maintained alongside code

## What's Done

- Recursive directory scanning with streaming progress and elapsed time
- Interactive keyboard navigation (arrow keys, vim-style hjkl)
- Sort by size, name, or date with sort key hints
- Color-coded sizes with percentage bars
- File and directory listing with symlink detection
- Refresh current directory with intelligent cache preservation
- Delete files/directories with confirmation dialog and progress spinner
- Comprehensive error handling and classification (permission denied, IO errors, not found)
- Cross-platform: macOS (ARM64/x64), Linux (x64/ARM64), Windows (x64 via MinGW)
- Adaptive progress tracking with throughput display
- macOS install/uninstall via Makefile

## TODO

- Tree view with indentation and expandable nodes
- File type detection and icons
- Multiple view modes (tree, list, details)
- Search and filter
- Export results (CSV, JSON)
- Ignore patterns (.gitignore support)
- Configuration file
- Parallel scanning
- In-app help system
- Binary releases and installation packages for Linux/Windows

## Technical Stack
- **Language**: Kotlin 2.2.20
- **UI Framework**: Mosaic 0.18.0 (Compose for Terminal)
- **Build System**: Gradle with Kotlin Multiplatform
- **Testing**: JUnit 5, MockK, Truth (Google's assertion library)
- **Target Platforms**: macOS (ARM64/x64), Linux (x64/ARM64), Windows (x64)

## Project Structure
```
disksize/
├── docs/                    # Project documentation
│   ├── PROJECT_PLAN.md     # This file
│   ├── ARCHITECTURE.md     # Technical architecture
│   ├── UX_DESIGN.md        # User experience design
│   ├── USER_MANUAL.md      # End-user documentation
│   └── TESTING_STRATEGY.md # Testing approach
├── src/
│   ├── commonMain/         # Platform-independent code
│   │   └── kotlin/
│   │       └── disksize/
│   │           ├── domain/      # Business logic
│   │           ├── ui/          # TUI components
│   │           └── utils/       # Utilities
│   ├── commonTest/         # Common tests
│   ├── macosMain/          # macOS-specific code
│   ├── linuxMain/          # Linux-specific code
│   └── mingwMain/          # Windows-specific code
└── README.md               # Project overview

```

## Current Status

Core features complete. Next focus: tree visualization and advanced features.

## Success Metrics
- **Performance**: Scan 100GB in under 30 seconds
- **Test Coverage**: Maintain 80%+ coverage
- **Code Quality**: Zero critical static analysis issues
- **User Satisfaction**: Clear, intuitive interface requiring minimal learning

## Risks & Mitigations
| Risk | Impact | Mitigation |
|------|--------|-----------|
| Platform-specific file system quirks | High | Abstract file system access, extensive platform testing |
| Performance on large directories | High | Implement streaming, parallel scanning, progress indicators |
| Permission denied errors | Medium | Graceful error handling, skip inaccessible directories |
| Different path separators across platforms | Medium | Use Kotlin's platform-agnostic path handling |
