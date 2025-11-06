# DiskSize - Architecture Design

## Overview
DiskSize follows a clean architecture approach with clear separation of concerns, designed for testability and cross-platform compatibility.

## Architectural Principles
1. **Platform Independence**: Core business logic is platform-agnostic
2. **Dependency Inversion**: High-level modules don't depend on low-level modules
3. **Single Responsibility**: Each class/module has one reason to change
4. **Testability**: All components are easily testable in isolation
5. **Immutability**: Prefer immutable data structures where possible

## Layer Architecture

```
┌─────────────────────────────────────────────┐
│           UI Layer (Mosaic TUI)             │
│  - Composable components                    │
│  - User input handling                      │
│  - View state management                    │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│          Presentation Layer                 │
│  - ViewModel / Presenter                    │
│  - UI state transformation                  │
│  - User action handling                     │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│            Domain Layer                     │
│  - Business logic                           │
│  - Use cases / Interactors                  │
│  - Domain models                            │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│            Data Layer                       │
│  - Repository implementations               │
│  - Platform-specific file system access     │
│  - Data transformation                      │
└─────────────────────────────────────────────┘
```

## Core Components

### Domain Layer

#### Models
```kotlin
// Core domain models
data class FileNode(
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
    val lastModified: Long,
    val permissions: FilePermissions?
)

data class ScanResult(
    val rootPath: String,
    val totalSize: Long,
    val fileCount: Int,
    val directoryCount: Int,
    val rootNode: FileNode,
    val scanDurationMs: Long,
    val errors: List<ScanError>
)

data class ScanError(
    val path: String,
    val message: String,
    val type: ErrorType
)
```

#### Use Cases
```kotlin
interface ScanDirectoryUseCase {
    fun scan(path: String): Flow<ScanStatus>
}

The flow emits `ScanStatus.Progress` events with `ScanProgress` payloads as the traversal advances, followed by a terminal `ScanStatus.Completed` containing the `ScanResult`.

interface GetDirectoryChildrenUseCase {
    suspend fun execute(node: FileNode): Result<List<FileNode>>
}

interface CalculateSizesUseCase {
    suspend fun execute(node: FileNode): Long
}
```

### Data Layer

#### Repository
```kotlin
interface FileSystemRepository {
    fun scanDirectory(path: String): Flow<DirectoryScanUpdate>
    suspend fun getFileInfo(path: String): Result<FileNode>
    suspend fun exists(path: String): Boolean
    suspend fun isAccessible(path: String): Boolean
}

`scanDirectory` streams indeterminate progress updates during the scan. Progress reports include files scanned, directories scanned, total bytes, and throughput (bytes/sec). This approach avoids the complexity and inaccuracy of pre-scan estimation while providing meaningful real-time feedback.
```

#### Platform-Specific Implementation
Each platform (macOS, Linux, Windows) will provide its own `FileSystemRepository` implementation using platform-specific APIs:
- macOS/Linux: POSIX APIs
- Windows: Windows APIs via MinGW

### Presentation Layer

#### State Management
```kotlin
data class MainScreenState(
    val currentPath: String,
    val currentNode: FileNode?,
    val isScanning: Boolean,
    val scanProgress: Float,
    val sortOrder: SortOrder,
    val error: String?
)

sealed class MainScreenEvent {
    data class DirectorySelected(val path: String) : MainScreenEvent()
    object NavigateUp : MainScreenEvent()
    object NavigateDown : MainScreenEvent()
    data class SortOrderChanged(val order: SortOrder) : MainScreenEvent()
}
```

### UI Layer

#### Composable Components
- `MainScreen`: Root composable, orchestrates entire UI
- `HeaderBar`: Title and current path display
- `DirectoryList`: Scrollable list of directories/files
- `StatusBar`: Statistics and help text
- `ProgressIndicator`: Scanning progress
- `ErrorDialog`: Error display

## Cross-Platform Strategy

### Source Sets Structure
```
src/
├── commonMain/           # Platform-independent code (90%)
│   └── kotlin/disksize/
│       ├── domain/       # All domain logic
│       ├── ui/           # All UI code
│       └── presentation/ # Presentation logic
├── commonTest/           # Common tests
├── nativeMain/           # Shared native code (POSIX)
│   └── kotlin/disksize/data/
├── appleMain/            # macOS/iOS specific
│   └── kotlin/disksize/data/
├── linuxMain/            # Linux specific
│   └── kotlin/disksize/data/
└── mingwMain/            # Windows specific
    └── kotlin/disksize/data/
```

### Platform Abstraction
Use `expect`/`actual` mechanism for platform-specific implementations:

```kotlin
// commonMain
expect class PlatformFileSystem() {
    suspend fun listFiles(path: String): List<FileInfo>
    suspend fun getSize(path: String): Long
    suspend fun getPermissions(path: String): FilePermissions
}

// macosMain / linuxMain (POSIX)
actual class PlatformFileSystem {
    actual suspend fun listFiles(path: String): List<FileInfo> {
        // POSIX implementation
    }
}

// mingwMain (Windows)
actual class PlatformFileSystem {
    actual suspend fun listFiles(path: String): List<FileInfo> {
        // Windows implementation
    }
}
```

## Concurrency Model
- Use Kotlin Coroutines for async operations
- File scanning runs in background coroutine
- UI updates on main thread
- Support cancellation of long-running scans

## Error Handling Strategy
1. **Domain Layer**: Return `Result<T>` for operations that can fail
2. **Presentation Layer**: Transform errors to user-friendly messages
3. **UI Layer**: Display errors in status bar or modal
4. **Graceful Degradation**: Skip inaccessible files, continue scanning

## Testing Strategy
- **Unit Tests**: Domain logic, use cases (pure functions)
- **Integration Tests**: Repository with mock file system
- **Component Tests**: UI components with test data
- **Platform Tests**: Platform-specific code on actual platforms

## Performance Considerations
1. **Lazy Loading**: Don't load entire tree into memory
2. **Streaming**: Process files as they're discovered
3. **Caching**: Cache calculated sizes
4. **Parallel Scanning**: Use multiple coroutines for I/O
5. **Progress Reporting**: Yield periodically to update UI

## Dependencies
- **Mosaic**: TUI framework
- **Kotlinx.coroutines**: Async/concurrency
- **JUnit 5**: Test framework
- **MockK**: Mocking framework
- **Truth**: Assertion library
- **Kotlinx.datetime**: Cross-platform date/time (future)

## Build Configuration
- Separate targets for each platform
- Shared build logic in common gradle scripts
- Platform-specific dependencies only where needed
- Debug and release configurations

## Future Considerations
- Plugin system for custom analyzers
- Remote directory scanning (SSH/SFTP)
- Database caching for large scans
- Configuration persistence
