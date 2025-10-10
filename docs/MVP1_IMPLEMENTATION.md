# MVP 1 Implementation Plan

## Goal
Create a basic disk space analyzer that scans a directory and displays size information in a static TUI.

## User Story
As a user, I want to specify a directory path and see the total size, file count, and a list of subdirectories sorted by size, so I can identify which subdirectories are consuming the most space.

## Acceptance Criteria
- [ ] User can run the application with a directory path argument (or use current directory by default)
- [ ] Application scans the directory recursively
- [ ] Application calculates total size, file count, and directory count
- [ ] Application displays subdirectories sorted by size (largest first)
- [ ] Each subdirectory shows:
  - Name
  - Size in human-readable format (B, KB, MB, GB, TB)
  - Percentage of parent directory
  - Visual percentage bar
- [ ] Application displays results in a clean TUI with box-drawing characters
- [ ] User can quit with 'q' key
- [ ] Errors (permission denied, etc.) are handled gracefully
- [ ] Application works on macOS
- [ ] Test coverage is 80%+

## Technical Tasks

### 1. Domain Layer (TDD)

#### 1.1 Create Domain Models
- [ ] `FileNode` data class - represents a file or directory
  - path: String
  - name: String
  - size: Long
  - isDirectory: Boolean
  - children: List<FileNode>
  - lastModified: Long
- [ ] `ScanResult` data class - represents scan output
  - rootPath: String
  - totalSize: Long
  - fileCount: Int
  - directoryCount: Int
  - rootNode: FileNode
  - scanDurationMs: Long
  - errors: List<ScanError>
- [ ] `ScanError` data class
  - path: String
  - message: String
  - type: ErrorType enum
- [ ] Write unit tests for domain models

#### 1.2 Create Use Cases
- [ ] `ScanDirectoryUseCase` - orchestrates directory scanning
  - Input: directory path
  - Output: Result<ScanResult>
  - Write tests first, then implementation
- [ ] `CalculateSizesUseCase` - calculates directory sizes
  - Recursive size calculation
  - Write tests first, then implementation

### 2. Data Layer (Platform-Specific)

#### 2.1 Create Repository Interface
- [ ] `FileSystemRepository` interface in commonMain
  - scanDirectory(path: String): Result<FileNode>
  - getFileInfo(path: String): Result<FileInfo>
  - exists(path: String): Boolean
- [ ] Write tests with fake implementation

#### 2.2 Create Platform Implementation
- [ ] `PosixFileSystemRepository` in macosMain (and nativeMain for shared POSIX code)
  - Use Kotlin/Native POSIX APIs
  - Handle permission errors
  - Skip inaccessible directories
- [ ] Write integration tests with real file system

### 3. Presentation Layer

#### 3.1 Create View State
- [ ] `MainScreenState` data class
  - currentPath: String
  - scanResult: ScanResult?
  - isScanning: Boolean
  - error: String?

#### 3.2 Create ViewModel/Presenter
- [ ] `MainViewModel` class
  - Manages state
  - Calls use cases
  - Transforms domain models to UI models
- [ ] Write tests with mocked use cases

### 4. UI Layer (Mosaic)

#### 4.1 Create UI Components (Composables)
- [ ] `MainScreen` composable
  - Overall layout
  - Conditionally show scanning/results/error
- [ ] `HeaderBar` composable
  - Title and styling
- [ ] `ScanResultView` composable
  - Total size, file count, directory count
- [ ] `DirectoryList` composable
  - List of subdirectories with sizes
  - Percentage bars
  - Color coding by size
- [ ] `StatusBar` composable
  - Help text ("q: Quit")
  - Scan duration

#### 4.2 Create Utilities
- [ ] `SizeFormatter` - formats bytes to human-readable strings
  - formatSize(bytes: Long): String
  - Examples: "1.2 GB", "456 MB", "78 KB"
  - Write unit tests
- [ ] `PercentageBar` - creates visual percentage bars
  - createBar(percentage: Float, width: Int): String
  - Write unit tests

### 5. Main Application

#### 5.1 Command-Line Argument Parsing
- [ ] Parse command-line arguments
  - First argument is directory path
  - Default to current directory if not provided
  - Validate path exists

#### 5.2 Application Entry Point
- [ ] `main()` function
  - Parse arguments
  - Create dependencies (DI manually for MVP 1)
  - Run Mosaic TUI
  - Handle keyboard input (quit on 'q')

### 6. Testing

#### 6.1 Unit Tests
- [ ] Domain model tests (FileNode, ScanResult)
- [ ] Use case tests (with mocked repository)
- [ ] SizeFormatter tests
- [ ] PercentageBar tests

#### 6.2 Integration Tests
- [ ] FileSystemRepository tests (with temp directories)
- [ ] End-to-end scan tests

#### 6.3 Test Coverage
- [ ] Achieve 80%+ coverage
- [ ] Set up coverage reporting (Kover plugin)

### 7. Documentation

- [ ] Update README with MVP 1 features
- [ ] Add code comments for complex logic
- [ ] Create basic usage examples

## Implementation Order (TDD)

### Phase 1: Core Domain (Week 1, Days 1-2)
1. Write tests for `FileNode` model
2. Implement `FileNode`
3. Write tests for `ScanResult` and `ScanError`
4. Implement `ScanResult` and `ScanError`
5. Write tests for `SizeFormatter`
6. Implement `SizeFormatter`

### Phase 2: Repository Layer (Week 1, Days 3-4)
1. Define `FileSystemRepository` interface
2. Create `FakeFileSystemRepository` for testing
3. Write tests for `PosixFileSystemRepository`
4. Implement `PosixFileSystemRepository` using Kotlin/Native POSIX APIs
5. Test with real file system

### Phase 3: Use Cases (Week 1, Day 5)
1. Write tests for `ScanDirectoryUseCase`
2. Implement `ScanDirectoryUseCase`
3. Write tests for `CalculateSizesUseCase`
4. Implement `CalculateSizesUseCase`

### Phase 4: UI Components (Week 2, Days 1-2)
1. Create basic `MainScreen` composable
2. Create `HeaderBar` composable
3. Write tests for percentage bar rendering
4. Create `DirectoryList` composable with test data
5. Create `StatusBar` composable
6. Add color coding logic

### Phase 5: Integration (Week 2, Day 3)
1. Create `MainViewModel`
2. Wire up domain layer to UI layer
3. Implement command-line argument parsing
4. Create `main()` function
5. Test full application flow

### Phase 6: Polish & Testing (Week 2, Days 4-5)
1. Handle edge cases (empty directories, permission errors)
2. Improve error messages
3. Add loading states
4. Run full test suite
5. Check test coverage
6. Fix any bugs
7. Update documentation

## Test Data

Create reusable test data sets:

```kotlin
object TestData {
    fun smallDirectory() = FileNode(
        path = "/test/small",
        name = "small",
        size = 1024,
        isDirectory = true,
        children = listOf(
            FileNode("/test/small/file1.txt", "file1.txt", 512, false, emptyList(), 0),
            FileNode("/test/small/file2.txt", "file2.txt", 512, false, emptyList(), 0)
        ),
        lastModified = 0
    )

    fun largeDirectory() = FileNode(
        path = "/test/large",
        name = "large",
        size = 1_000_000_000,
        isDirectory = true,
        children = emptyList(),
        lastModified = 0
    )
}
```

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All tests passing
- [ ] 80%+ test coverage achieved
- [ ] Code reviewed (self-review using clean code principles)
- [ ] Documentation updated
- [ ] Application runs successfully on macOS
- [ ] No critical bugs
- [ ] Clean, readable code with appropriate comments

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Kotlin/Native POSIX API learning curve | Medium | Start with simple file operations, reference documentation |
| Permission errors on system directories | Low | Graceful error handling, skip inaccessible directories |
| Large directories causing performance issues | Medium | Add timeout/cancellation support, show progress |
| Mosaic learning curve | Low | Start with simple layouts, reference Mosaic examples |

## Success Metrics

- Application scans a 10GB directory in under 30 seconds
- User can understand the output without documentation
- Zero crashes on valid input
- Test coverage > 80%
- Code is clean and maintainable

## Next Steps After MVP 1

Once MVP 1 is complete and tested:
1. Gather feedback on UX
2. Plan MVP 2 (interactive navigation)
3. Consider performance optimizations if needed
4. Test on Linux to identify cross-platform issues early
