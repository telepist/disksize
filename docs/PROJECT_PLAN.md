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

## MVP Roadmap

### MVP 1: Basic Directory Scanning & Display (Complete)
**Goal**: Scan a directory and display basic size information in TUI

**Features**:
- Command-line argument to specify directory path (default: current directory)
- Recursive directory scanning
- Calculate and display:
  - Total size of directory
  - Count of files and subdirectories
  - List of immediate subdirectories with sizes
- Basic TUI with static display
- Cross-platform file system access

**Success Criteria**:
- Accurately calculates directory sizes
- Displays results in a readable TUI format
- Works on macOS (primary development platform)
- 80%+ test coverage

### MVP 2: Interactive Navigation (Complete)
**Goal**: Navigate through directory hierarchy interactively

**Features**:
- [x] Keyboard navigation (arrow keys, Enter to drill down, Backspace to go up)
- [x] Highlight selected directory with contextual status bar hints
- [x] Sort options (by size, name, date)
- [x] Enhanced status bar showing aggregated totals for selection
- [x] Color-coded size indicators
- [x] Display both files and directories
- [x] Refresh functionality (r key)
- [x] File deletion with confirmation (Delete key)
- [x] Intelligent cache preservation during navigation
- [x] Symlink handling without following
- [x] Streaming progress updates during scan

### MVP 3: Tree Visualization & Percentages
**Goal**: Visual representation of space usage

**Features**:
- Tree view with indentation
- Percentage bars showing relative size
- File type detection and icons
- Multiple view modes (tree, list, details)
- Filter by file type

### MVP 4: Advanced Features
**Goal**: Power user features and performance optimization

**Features**:
- Search/filter functionality
- Export results (CSV, JSON, text)
- Comparison mode (compare two directories)
- Ignore patterns (.gitignore support)
- Incremental scanning with progress indicator
- Configuration file support

### MVP 5: Performance & Polish
**Goal**: Production-ready application

**Features**:
- Performance optimization (caching, parallel scanning)
- Linux and Windows testing and fixes
- Comprehensive error handling
- User manual and help system
- Binary releases for all platforms
- Installation scripts

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
- **Phase**: MVP 2 Complete - Ready for MVP 3
- **Recent Progress**:
  - ✅ Completed all MVP 2 interactive navigation features
  - ✅ Added directory refresh with intelligent cache preservation
  - ✅ Implemented file deletion with confirmation dialog
  - ✅ Added streaming progress updates with Flow-based scanning
  - ✅ Proper symlink handling without following
  - ✅ Show both files and directories with sorting
  - ✅ Enhanced status bar with contextual hints
- **Next Steps**:
  1. Add tree visualization with indentation
  2. Implement percentage bars in tree view
  3. Add file type detection and icons
  4. Create multiple view modes (tree, list, details)
  5. Add filter by file type functionality

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
