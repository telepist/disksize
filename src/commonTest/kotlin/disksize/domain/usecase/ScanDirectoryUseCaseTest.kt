package disksize.domain.usecase

import disksize.data.fake.FakeFileSystemRepository
import disksize.domain.model.FileNode
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanDirectoryUseCaseTest {
    private lateinit var repository: FakeFileSystemRepository
    private lateinit var useCase: ScanDirectoryUseCase

    @BeforeTest
    fun setup() {
        repository = FakeFileSystemRepository()
        useCase = ScanDirectoryUseCase(repository)
    }

    @Test
    fun `should scan directory and return scan result`() = runTest {
        // Given
        val child1 = FileNode("/test/file1.txt", "file1.txt", 100, false, emptyList(), 0L)
        val child2 = FileNode("/test/file2.txt", "file2.txt", 200, false, emptyList(), 0L)
        val testNode = FileNode(
            path = "/test",
            name = "test",
            size = 0,
            isDirectory = true,
            children = listOf(child1, child2),
            lastModified = 0L
        )
        repository.addFile(testNode)

        // When
        val result = useCase.execute("/test")

        // Then
        assertTrue(result.isSuccess)
        val scanResult = result.getOrNull()!!
        assertEquals("/test", scanResult.rootPath)
        assertEquals(300, scanResult.totalSize)
        assertEquals(2, scanResult.fileCount)
        assertEquals(0, scanResult.directoryCount)
        assertTrue(scanResult.isSuccessful())
    }

    @Test
    fun `should calculate directory counts correctly`() = runTest {
        // Given
        val subdir1 = FileNode("/test/subdir1", "subdir1", 0, true, emptyList(), 0L)
        val subdir2 = FileNode("/test/subdir2", "subdir2", 0, true, emptyList(), 0L)
        val testNode = FileNode(
            path = "/test",
            name = "test",
            size = 0,
            isDirectory = true,
            children = listOf(subdir1, subdir2),
            lastModified = 0L
        )
        repository.addFile(testNode)

        // When
        val result = useCase.execute("/test")

        // Then
        assertTrue(result.isSuccess)
        val scanResult = result.getOrNull()!!
        assertEquals(2, scanResult.directoryCount)
        assertEquals(0, scanResult.fileCount)
    }

    @Test
    fun `should return error when directory not found`() = runTest {
        // When
        val result = useCase.execute("/nonexistent")

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `should return empty errors list on successful scan`() = runTest {
        // Given
        val testNode = FileNode("/test", "test", 0, true, emptyList(), 0L)
        repository.addFile(testNode)

        // When
        val result = useCase.execute("/test")

        // Then
        assertTrue(result.isSuccess)
        val scanResult = result.getOrNull()!!
        assertTrue(scanResult.errors.isEmpty())
        assertTrue(scanResult.isSuccessful())
    }

    @Test
    fun `should record scan duration`() = runTest {
        // Given
        val testNode = FileNode("/test", "test", 0, true, emptyList(), 0L)
        repository.addFile(testNode)

        // When
        val result = useCase.execute("/test")

        // Then
        assertTrue(result.isSuccess)
        val scanResult = result.getOrNull()!!
        assertTrue(scanResult.scanDurationMs >= 0)
    }

    @Test
    fun `should handle nested directory structure`() = runTest {
        // Given
        val deepFile = FileNode("/test/sub1/sub2/file.txt", "file.txt", 500, false, emptyList(), 0L)
        val sub2 = FileNode("/test/sub1/sub2", "sub2", 0, true, listOf(deepFile), 0L)
        val sub1 = FileNode("/test/sub1", "sub1", 0, true, listOf(sub2), 0L)
        val testNode = FileNode("/test", "test", 0, true, listOf(sub1), 0L)
        repository.addFile(testNode)

        // When
        val result = useCase.execute("/test")

        // Then
        assertTrue(result.isSuccess)
        val scanResult = result.getOrNull()!!
        assertEquals(500, scanResult.totalSize)
        assertEquals(1, scanResult.fileCount)
        assertEquals(2, scanResult.directoryCount) // sub1 and sub2
    }
}
