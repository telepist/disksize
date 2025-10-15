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
    commonTest {
        dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlin:kotlin-test-junit5")
            implementation("io.mockk:mockk:1.13.8")
            implementation("com.google.truth:truth:1.1.5")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
        }
    }
}
```

### Test Structure
```
src/
└── commonTest/
    └── kotlin/
        └── disksize/
            ├── domain/
            │   ├── model/
            │   │   └── FileNodeTest.kt
            │   └── usecase/
            │       ├── ScanDirectoryUseCaseTest.kt
            │       └── CalculateSizesUseCaseTest.kt
            ├── data/
            │   └── repository/
            │       └── FileSystemRepositoryTest.kt
            ├── presentation/
            │   └── MainViewModelTest.kt
            └── testutil/
                ├── TestData.kt
                └── FakeFileSystem.kt
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

        val totalSize = parent.totalSize()

        assertThat(totalSize).isEqualTo(300)
    }

    @Test
    fun `should handle empty directory`() {
        val emptyDir = FileNode(
            path = "/empty",
            size = 0,
            isDirectory = true,
            children = emptyList()
        )

        assertThat(emptyDir.totalSize()).isEqualTo(0)
        assertThat(emptyDir.isEmpty()).isTrue()
    }
}
```

### Use Case Tests
Test business logic with mocked dependencies:

```kotlin
class ScanDirectoryUseCaseTest {
    private lateinit var repository: FileSystemRepository
    private lateinit var useCase: ScanDirectoryUseCase

    @BeforeEach
    fun setup() {
        repository = mockk<FileSystemRepository>()
        useCase = ScanDirectoryUseCaseImpl(repository)
    }

    @Test
    fun `should scan directory and return result`() = runTest {
        // Given
        val testPath = "/test/path"
        val expectedNode = TestData.createFileNode(testPath)
        every { repository.scanDirectory(testPath) } returns flowOf(
            DirectoryScanUpdate.Progress(ScanProgress(0, 10, 0, 2)),
            DirectoryScanUpdate.Complete(
                FileSystemRepository.DirectoryScanResult(
                    root = expectedNode,
                    errors = emptyList()
                )
            )
        )

        // When
        val updates = useCase.scan(testPath).toList()

        // Then
        val completed = updates.filterIsInstance<ScanStatus.Completed>().single()
        assertThat(completed.result.rootNode).isEqualTo(expectedNode)
        verify(exactly = 1) { repository.scanDirectory(testPath) }
    }

    @Test
    fun `should handle permission denied error`() = runTest {
        // Given
        val testPath = "/restricted"
        every { repository.scanDirectory(testPath) } returns flow {
            throw PermissionDeniedException(testPath)
        }

        // When
        val result = runCatching { useCase.scan(testPath).toList() }

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .isInstanceOf(PermissionDeniedException::class.java)
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
./gradlew koverHtmlReport

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

## Assertion Library - Truth

Use Google Truth for readable assertions:
```kotlin
// Truth assertions
assertThat(list).hasSize(5)
assertThat(list).contains(item)
assertThat(list).containsExactly(item1, item2, item3).inOrder()
assertThat(value).isEqualTo(expected)
assertThat(value).isGreaterThan(100)
assertThat(result.isSuccess).isTrue()
assertThat(exception).hasMessageThat().contains("error")
```

## Test Execution

### Running Tests
```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "FileNodeTest"

# Run tests for specific platform
./gradlew macosTest
./gradlew linuxTest
./gradlew mingwTest

# Run tests with output
./gradlew test --info
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
