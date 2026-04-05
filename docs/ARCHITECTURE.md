# DiskSize - Architecture Design

## Overview
DiskSize follows a clean architecture approach with clear separation of concerns, designed for testability and cross-platform compatibility. The system uses a reactive data flow: a single `FileTreeStore` owns the file tree model, mutations (scanning, deletion) go through the store, and the UI derives its state automatically.

## Architectural Principles
1. **Platform Independence**: Core business logic is platform-agnostic
2. **Reactive Single Source of Truth**: `FileTreeStore` owns the file tree; UI derives state from it
3. **Dependency Inversion**: High-level modules don't depend on low-level modules
4. **Single Responsibility**: Each class/module has one reason to change
5. **Testability**: All components are easily testable in isolation — presentation logic lives in `ExplorerViewModel`, not in Compose
6. **Immutability**: Prefer immutable data structures where possible

## Layer Architecture

```
┌─────────────────────────────────────────────┐
│           UI Layer (Mosaic TUI)             │
│  - Thin Compose wrappers                    │
│  - Collects StateFlow, delegates events     │
│  - Pure rendering functions                 │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│          Presentation Layer                 │
│  - ExplorerViewModel (all user actions)     │
│  - deriveExplorerState() (state derivation) │
│  - ExplorerState, UiSelections, BrowserItem │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│            Domain Layer                     │
│  - FileTreeStore (reactive tree model)      │
│  - Use cases (scan, delete)                 │
│  - Domain models (FileNode, FileTreeState)  │
└─────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────┐
│            Data Layer                       │
│  - Repository implementations               │
│  - Platform-specific file system access     │
└─────────────────────────────────────────────┘
```

## Data Flow

```
FileSystemRepository.scanDirectory()
        │ Flow<DirectoryScanUpdate>
        ▼
ScanDirectoryUseCase.scanInto(store)
        │ pushes updates
        ▼
FileTreeStore (StateFlow<FileTreeState>)
        │ collected by ViewModel
        ▼
ExplorerViewModel
        │ derives via deriveExplorerState(tree, ui)
        ▼
ExplorerState (StateFlow) → UI
```

Both scanning and deletion mutate the `FileTreeStore`. The store filters out paths deleted during an active scan, so deleted items never reappear from incoming scan snapshots.

## Core Components

### Domain Layer

#### Models
```kotlin
data class FileNode(
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val children: List<FileNode>,
    val lastModified: Long,
    val cachedTotalSize: Long,   // O(1) aggregate lookups
    val cachedFileCount: Int,
    val cachedDirectoryCount: Int
)

data class FileTreeState(
    val rootPath: String,
    val rootNode: FileNode?,
    val scanPhase: ScanPhase,        // IDLE, LOADING, SCANNING, COMPLETED, ERROR
    val scannedPaths: Set<String>,
    val scanProgress: ScanProgress?,
    val scanDurationMs: Long,
    val errors: List<ScanError>,
    val errorMessage: String?,
    val scanStartTimeMark: TimeSource.Monotonic.ValueTimeMark?,
    val lastPartialScannedBytes: Long
)

data class ScanResult(...)   // derived from FileTreeState for display
data class ScanError(...)
```

#### FileTreeStore
Reactive owner of the file tree. All mutations go through its methods:
- `reset(path)` — start a new scan
- `updateProgress(progress)` — update scan progress
- `applyPartialTree(root, scannedPaths, errors)` — apply partial scan snapshot
- `applyComplete(root, errors, durationMs)` — apply final scan result
- `removeNode(path)` — remove a deleted item
- `setError(message)` — set scan error state
- `restore(savedState)` — restore from navigation history

#### Use Cases
```kotlin
class ScanDirectoryUseCase(
    repository: FileSystemRepository,
    scanDispatcher: CoroutineDispatcher = Dispatchers.Default  // injectable for tests
) {
    suspend fun scanInto(path: String, store: FileTreeStore)   // pushes into store
    fun scan(path: String): Flow<ScanStatus>                   // standalone flow
}

class DeleteFileUseCase(repository: FileSystemRepository) {
    suspend fun delete(path: String): DeletionResult
}
```

### Data Layer

#### Repository
```kotlin
abstract class FileSystemRepository {
    fun scanDirectory(path: String): Flow<DirectoryScanUpdate>
    suspend fun getFileInfo(path: String): Result<FileNode>
    suspend fun delete(path: String): Result<DeletionStats>
    suspend fun exists(path: String): Boolean
    suspend fun isAccessible(path: String): Boolean
    open fun classifyError(error: Throwable): ErrorType
}
```

#### Platform-Specific Implementations
- `PosixFileSystemRepository` — macOS/Linux via POSIX APIs
- `WindowsFileSystemRepository` — Windows via Win32 APIs

### Presentation Layer

#### ExplorerViewModel
Owns all presentation logic — testable without Compose:
- Navigation: `moveSelection()`, `toggleExpand()`, `expandOrEnter()`, `collapseOrParent()`, `navigateUp()`
- Actions: `startScan()`, `refresh()`, `cycleSort()`, `requestDelete()`, `confirmDelete()`, `cancelDelete()`
- Key handling: `handleKey(key, pageSize, onQuit)` — maps keys to actions with state guards
- Spinner animation: `startSpinnerIfNeeded()`
- Exposes `state: StateFlow<ExplorerState>` derived from `FileTreeStore.state` + `UiSelections`

#### State Derivation
```kotlin
// UI-local selections (not in the store)
data class UiSelections(
    selectedIndex, sortOrder, expandedPaths,
    confirmDeleteItem, isDeletingInProgress, errorMessage, spinnerIndex
)

// Derives the full UI state from tree model + UI selections
fun deriveExplorerState(tree: FileTreeState, ui: UiSelections): ExplorerState
```

`ExplorerState` is consumed by all rendering functions unchanged — the derivation is transparent to the UI layer.

### UI Layer

Thin Compose wrappers with no business logic:
- `DiskSizeApp` — collects `viewModel.state`, triggers initial scan, starts spinner, delegates key events
- `MainScreen` — receives `ExplorerState` + key handler, renders frame

Pure rendering functions (take `ExplorerState`, produce `FrameLine` lists):
- `ScreenBuilder` — composes layout: header → stats → rule → content → rule → status
- `DirectorySection` — entry list with selector, bars, tree connectors, size-coded colors
- `StatusSection` — status left, key hints right
- `HeaderSection`, `StatsSection` — compact header and stats lines
- `ConfirmationDialog` — centered dialogs with rounded corners

## Cross-Platform Strategy

### Source Sets Structure
```
src/
├── commonMain/           # Platform-independent code (~90%)
│   └── kotlin/disksize/
│       ├── data/         # Repository base class, progress tracker
│       ├── domain/       # FileTreeStore, use cases, models
│       ├── presentation/ # ExplorerViewModel, ExplorerState, derivation
│       ├── ui/           # TUI composable components
│       └── util/         # Formatting utilities
├── commonTest/           # Common tests
├── posixMain/            # Shared macOS/Linux code (entry point, POSIX repository)
├── macosArm64Main/       # macOS ARM64 platform glue
├── macosX64Main/         # macOS x64 platform glue
├── linuxX64Main/         # Linux x64 platform glue
├── linuxArm64Main/       # Linux ARM64 platform glue
└── mingwX64Main/         # Windows-specific (entry point, Win API repository)
```

## Concurrency Model
- Kotlin Coroutines for all async operations
- File scanning runs via injectable dispatcher (defaults to `Dispatchers.Default`)
- `FileTreeStore` uses `MutableStateFlow` with atomic `update {}` for thread safety
- `ExplorerViewModel` observes the store via `collect` and re-derives state
- Cancellation-safe: cancelling a scan leaves the store with the last partial state

## Error Handling Strategy
1. **Domain Layer**: Return `Result<T>` for operations that can fail
2. **FileTreeStore**: `setError()` for scan failures (persists in `FileTreeState`)
3. **Presentation Layer**: Transient UI errors (e.g., delete failure) in `UiSelections.errorMessage`; cleared on next key press
4. **UI Layer**: Error shown in status bar (transient) or directory section (fatal, no scan data)
5. **Graceful Degradation**: Skip inaccessible files during scan, accumulate warnings

## Testing Strategy
- **Unit Tests**: Domain logic, use cases, FileTreeStore, ExplorerViewModel, state derivation
- **Integration Tests**: Repository with FakeFileSystemRepository
- **Component Tests**: UI rendering functions with test data
- **Platform Tests**: Platform-specific code on actual platforms
- ExplorerViewModel tested with `UnconfinedTestDispatcher` for synchronous execution

## Dependencies
- **JetBrains Compose** 1.8.0: Compose Multiplatform framework
- **Mosaic** 0.18.0: TUI rendering on top of Compose
- **Kotlinx Coroutines** 1.9.0: Async/concurrency
- **kotlin.test**: Test framework and assertions
- **kotlinx-coroutines-test**: Coroutine test utilities

## Build Configuration
- Separate targets for each platform
- Shared build logic in common gradle scripts
- Platform-specific dependencies only where needed
- Debug and release configurations
