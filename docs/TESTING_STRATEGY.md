# DiskSize - Testing Strategy

## Testing Philosophy
- **Test-Driven Development**: Write tests before implementation
- **High Coverage**: Target 80%+ code coverage
- **Fast Tests**: Unit tests should run in milliseconds
- **Reliable Tests**: No flaky tests, deterministic results
- **Clear Intent**: Tests serve as documentation

## Testing Pyramid

```
                 ┌─────────┐
                 │   E2E   │  5% - Full application tests
                 │  Tests  │
                ┌┴─────────┴┐
                │Integration│  15% - Component integration
                │   Tests   │
              ┌─┴───────────┴─┐
              │  Unit Tests   │  80% - Individual units
              │               │
              └───────────────┘
```

## Test Framework Setup

### Dependencies (build.gradle.kts)
```kotlin
sourceSets {
    val commonTest by getting {
        dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

The project uses `kotlin.test` (multiplatform test framework with built-in assertions) and `kotlinx-coroutines-test` 1.9.0 for async testing. No JUnit 5, MockK, or Truth dependencies are used.

### Test Structure
```
src/
└── commonTest/
    └── kotlin/
        └── disksize/
            ├── data/
            │   ├── FileSystemRepositoryErrorClassificationTest.kt
            │   └── fake/
            │       ├── FakeFileSystemRepository.kt
            │       └── FakeFileSystemRepositoryTest.kt
            ├── domain/
            │   ├── FileTreeStoreTest.kt
            │   ├── model/
            │   │   ├── FileNodeTest.kt
            │   │   ├── FileNodeTestHelper.kt
            │   │   └── ScanResultTest.kt
            │   └── usecase/
            │       ├── DeleteFileUseCaseTest.kt
            │       └── ScanDirectoryUseCaseTest.kt
            ├── presentation/
            │   ├── CrossPlatformPathTest.kt
            │   ├── DeriveExplorerStateTest.kt
            │   ├── ExplorerStateTest.kt
            │   ├── ExplorerStateTestHelpers.kt
            │   └── ExplorerViewModelTest.kt
            ├── ui/
            │   ├── FrameFormattingTest.kt
            │   └── FrameTextTest.kt
            └── util/
                ├── PathUtilsTest.kt
                └── SizeFormatterTest.kt
```

## Unit Testing

### Domain Model Tests
Test pure data classes and business logic:

```kotlin
class FileNodeTest {
    @Test
    fun `should calculate total size including children`() {
        val child1 = FileNode(path = "/test/file1.txt", size = 100, isDirectory = false)
        val child2 = FileNode(path = "/test/file2.txt", size = 200, isDirectory = false)
        val parent = FileNode(
            path = "/test",
            size = 0,
            isDirectory = true,
            children = listOf(child1, child2)
        )

        assertEquals(300, parent.totalSize())
    }

    @Test
    fun `should handle empty directory`() {
        val emptyDir = FileNode(
            path = "/empty",
            size = 0,
            isDirectory = true,
            children = emptyList()
        )

        assertEquals(0, emptyDir.totalSize())
        assertTrue(emptyDir.isEmpty())
    }
}
```

### Use Case Tests
Test business logic with fake dependencies:

```kotlin
class ScanDirectoryUseCaseTest {
    private lateinit var repository: FakeFileSystemRepository
    private lateinit var useCase: ScanDirectoryUseCase

    @BeforeTest
    fun setup() {
        repository = FakeFileSystemRepository()
        useCase = ScanDirectoryUseCase(repository)
    }

    @Test
    fun `should scan directory and return result`() = runTest {
        val testNode = createFileNode("/test", "test", isDirectory = true,
            children = listOf(
                createFileNode("/test/file1.txt", "file1.txt", size = 1024),
                createFileNode("/test/file2.txt", "file2.txt", size = 2048)
            ))
        repository.addFile(testNode)

        val updates = useCase.scan("/test").toList()

        val completed = updates.filterIsInstance<ScanStatus.Completed>().single()
        assertEquals("/test", completed.result.rootPath)
        assertEquals(2, completed.result.fileCount)
    }
}
```

### ViewModel Tests
Test presentation logic with `UnconfinedTestDispatcher` for synchronous execution:

```kotlin
class ExplorerViewModelTest {
    private lateinit var repository: FakeFileSystemRepository
    private lateinit var store: FileTreeStore
    private lateinit var viewModel: ExplorerViewModel
    private lateinit var vmScope: CoroutineScope

    @BeforeTest
    fun setup() {
        repository = FakeFileSystemRepository()
        store = FileTreeStore()
        val dispatcher = UnconfinedTestDispatcher()
        val scanUseCase = ScanDirectoryUseCase(repository, dispatcher)
        val deleteUseCase = DeleteFileUseCase(repository)
        vmScope = CoroutineScope(dispatcher)
        viewModel = ExplorerViewModel(scanUseCase, deleteUseCase, store, vmScope)
    }

    @AfterTest
    fun teardown() {
        vmScope.cancel()
    }

    @Test
    fun `confirmDelete removes item from tree`() {
        // setup tree, scan, select item...
        viewModel.requestDelete()
        viewModel.confirmDelete()

        assertFalse(viewModel.state.value.browserItems.any { it.node.name == "target.txt" })
    }
}
```

## Integration Testing

### Repository Tests with Fake File System
Create a fake file system for testing:

```kotlin
class FakeFileSystem : FileSystemRepository {
    private val files = mutableMapOf<String, FakeFile>()

    fun addFile(path: String, size: Long, isDirectory: Boolean = false) {
        files[path] = FakeFile(path, size, isDirectory)
    }

    override fun scanDirectory(path: String): Flow<DirectoryScanUpdate> = flow {
        val file = files[path] ?: throw FileNotFoundException(path)
        // Emit simple progress update for documentation purposes
        emit(DirectoryScanUpdate.Progress(ScanProgress(0, 1, 0, 0, currentFile = path)))
        emit(
            DirectoryScanUpdate.Complete(
                FileSystemRepository.DirectoryScanResult(
                    root = file.toFileNode(),
                    errors = emptyList()
                )
            )
        )
    }
}

class FileSystemRepositoryIntegrationTest {
    private lateinit var fakeFileSystem: FakeFileSystem

    @BeforeEach
    fun setup() {
        fakeFileSystem = FakeFileSystem().apply {
            addFile("/root", 0, isDirectory = true)
            addFile("/root/file1.txt", 1024)
            addFile("/root/file2.txt", 2048)
            addFile("/root/subdir", 0, isDirectory = true)
            addFile("/root/subdir/file3.txt", 512)
        }
    }

    @Test
    fun `should scan directory with nested structure`() = runTest {
        val result = fakeFileSystem.scanDirectory("/root")

        assertThat(result.isSuccess).isTrue()
        val node = result.getOrNull()!!
        assertThat(node.children).hasSize(3)
        assertThat(node.totalSize()).isEqualTo(3584)
    }
}
```

## UI Component Testing

### Composable Tests
Test UI components with test state:

```kotlin
class DirectoryListTest {
    @Test
    fun `should display directories sorted by size`() {
        val testState = MainScreenState(
            currentPath = "/test",
            currentNode = TestData.createFileNodeWithChildren(),
            isScanning = false,
            sortOrder = SortOrder.SIZE_DESC
        )

        val renderedOutput = renderMosaicComposable {
            DirectoryList(state = testState)
        }

        // Verify rendered output contains expected items in order
        assertThat(renderedOutput).contains("large-dir")
        assertThat(renderedOutput).contains("small-dir")
        // Verify large-dir appears before small-dir
    }
}
```

## Platform-Specific Tests

### macOS/Linux Tests
```kotlin
// src/macosTest/kotlin/disksize/data/PosixFileSystemTest.kt
class PosixFileSystemTest {
    private lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("disksize-test")
    }

    @AfterEach
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should read actual file sizes on macOS`() = runTest {
        // Create real test files
        val testFile = tempDir.resolve("test.txt").toFile()
        testFile.writeText("Hello, World!")

        val fileSystem = PosixFileSystem()
        val size = fileSystem.getSize(testFile.absolutePath)

        assertThat(size).isEqualTo(13)
    }
}
```

## Test Data Builders

### Create reusable test data:
```kotlin
object TestData {
    fun createFileNode(
        path: String = "/test",
        name: String = "test",
        size: Long = 0,
        isDirectory: Boolean = true,
        children: List<FileNode> = emptyList()
    ) = FileNode(
        path = path,
        name = name,
        size = size,
        isDirectory = isDirectory,
        children = children,
        lastModified = 0L,
        permissions = null
    )

    fun createLargeDirectoryTree(depth: Int = 3, filesPerDir: Int = 5): FileNode {
        // Generate test tree structure
    }
}
```

## Coroutine Testing

### Test async code:
```kotlin
@Test
fun `should cancel scanning when requested`() = runTest {
    val repository = FakeFileSystemRepository(delayMs = 1000)
    val useCase = ScanDirectoryUseCase(repository)

    val job = launch {
        useCase.execute("/large-directory")
    }

    delay(100) // Let it start
    job.cancel()

    assertThat(job.isCancelled).isTrue()
}
```

## Performance Testing

### Benchmark tests:
```kotlin
@Test
fun `should scan 1000 files in under 1 second`() = runTest {
    val fileSystem = FakeFileSystem().apply {
        repeat(1000) { i ->
            addFile("/test/file$i.txt", 1024)
        }
    }

    val startTime = TimeSource.Monotonic.markNow()
    val result = fileSystem.scanDirectory("/test")
    val duration = startTime.elapsedNow()

    assertThat(result.isSuccess).isTrue()
    assertThat(duration).isLessThan(1.seconds)
}
```

## Test Coverage

### Measuring Coverage
```bash
# Run tests with coverage (using Kover plugin)
make test-coverage

# View coverage report
open build/reports/kover/html/index.html
```

### Coverage Goals
- **Overall**: 80% minimum
- **Domain Layer**: 90%+ (pure logic)
- **Data Layer**: 70%+ (platform-specific code)
- **Presentation Layer**: 85%+
- **UI Layer**: 60%+ (harder to test)

## Continuous Integration

### CI Pipeline
```yaml
# .github/workflows/test.yml
name: Test

on: [push, pull_request]

jobs:
  test:
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [macos-latest, ubuntu-latest, windows-latest]

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Run tests
        run: ./gradlew test

      - name: Generate coverage report
        run: ./gradlew koverXmlReport

      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

## Test Naming Convention

Use descriptive test names that explain behavior:
```kotlin
// Good
@Test
fun `should return error when directory does not exist`()

@Test
fun `should calculate size as sum of all child file sizes`()

// Bad
@Test
fun testScan()

@Test
fun test1()
```

## Assertions - kotlin.test

Use `kotlin.test` built-in assertions:
```kotlin
// kotlin.test assertions
assertEquals(expected, actual)
assertTrue(condition)
assertFalse(condition)
assertNull(value)
assertFailsWith<SomeException> { riskyOperation() }
```

## Test Execution

### Running Tests
```bash
# Run tests for current platform
make test

# Run tests for specific platform via Gradle
./gradlew macosArm64Test
./gradlew linuxX64Test
./gradlew mingwX64Test

# Run tests with output
./gradlew macosArm64Test --info
```

## Test Quality Checklist
- [ ] Tests are independent (no shared mutable state)
- [ ] Tests are deterministic (no random values, no current time)
- [ ] Tests are fast (<100ms for unit tests)
- [ ] Tests have clear Given-When-Then structure
- [ ] Tests test one thing at a time
- [ ] Tests use descriptive names
- [ ] Edge cases are covered (null, empty, large values)
- [ ] Error cases are tested
- [ ] Mock interactions are verified
- [ ] Test data is isolated and disposable
