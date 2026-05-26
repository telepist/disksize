package disksize.domain.usecase

import disksize.data.fake.FakeFileSystemRepository
import disksize.domain.model.FileNode
import disksize.domain.model.ScanProgress
import disksize.domain.model.ScanStatus
import disksize.domain.model.createFileNode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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
        val child1 = createFileNode("/test/file1.txt", "file1.txt", 100, false, false, emptyList(), 0L)
        val child2 = createFileNode("/test/file2.txt", "file2.txt", 200, false, false, emptyList(), 0L)
        val testNode = createFileNode(
            path = "/test",
            name = "test",
            size = 0,
            isDirectory = true,
            children = listOf(child1, child2),
            lastModified = 0L
        )
        repository.addFile(testNode)

        // When
        val updates = useCase.scan("/test").toList()

        val scanResult = updates.filterIsInstance<ScanStatus.Completed>().single().result
        assertEquals("/test", scanResult.rootPath)
        assertEquals(300, scanResult.totalSize)
        assertEquals(2, scanResult.fileCount)
        assertEquals(0, scanResult.directoryCount)
        assertTrue(scanResult.isSuccessful())
    }

    @Test
    fun `should calculate directory counts correctly`() = runTest {
        // Given
        val subdir1 = createFileNode("/test/subdir1", "subdir1", 0, true, false, emptyList(), 0L)
        val subdir2 = createFileNode("/test/subdir2", "subdir2", 0, true, false, emptyList(), 0L)
        val testNode = createFileNode(
            path = "/test",
            name = "test",
            size = 0,
            isDirectory = true,
            children = listOf(subdir1, subdir2),
            lastModified = 0L
        )
        repository.addFile(testNode)

        // When
        val scanResult = useCase.scan("/test")
            .toList()
            .filterIsInstance<ScanStatus.Completed>()
            .single()
            .result
        assertEquals(2, scanResult.directoryCount)
        assertEquals(0, scanResult.fileCount)
    }

    @Test
    fun `should return error when directory not found`() = runTest {
        // When
        val exception = assertFailsWith<Exception> {
            useCase.scan("/nonexistent").toList()
        }
        assertTrue(exception.message?.contains("not found") == true)
    }

    @Test
    fun `should return empty errors list on successful scan`() = runTest {
        // Given
        val testNode = createFileNode("/test", "test", 0, true, false, emptyList(), 0L)
        repository.addFile(testNode)

        // When
        val scanResult = useCase.scan("/test")
            .toList()
            .filterIsInstance<ScanStatus.Completed>()
            .single()
            .result
        assertTrue(scanResult.errors.isEmpty())
        assertTrue(scanResult.isSuccessful())
    }

    @Test
    fun `should surface repository warnings`() = runTest {
        val child = createFileNode("/test/secret", "secret", 0, true, false, emptyList(), 0L)
        val testNode = createFileNode(
            path = "/test",
            name = "test",
            size = 0,
            isDirectory = true,
            isSymlink = false,
            children = listOf(child),
            lastModified = 0L
        )
        repository.addFile(child)
        repository.addFile(testNode)
        repository.markInaccessible(child.path)

        val scanResult = useCase.scan("/test")
            .toList()
            .filterIsInstance<ScanStatus.Completed>()
            .single()
            .result
        assertEquals(1, scanResult.errors.size)
        assertFalse(scanResult.isSuccessful())
    }

    @Test
    fun `should record scan duration`() = runTest {
        // Given
        val testNode = createFileNode("/test", "test", 0, true, false, emptyList(), 0L)
        repository.addFile(testNode)

        // When
        val scanResult = useCase.scan("/test")
            .toList()
            .filterIsInstance<ScanStatus.Completed>()
            .single()
            .result
        assertTrue(scanResult.scanDurationMs >= 0)
    }

    @Test
    fun `should handle nested directory structure`() = runTest {
        // Given
        val deepFile = createFileNode("/test/sub1/sub2/file.txt", "file.txt", 500, false, false, emptyList(), 0L)
        val sub2 = createFileNode("/test/sub1/sub2", "sub2", 0, true, false, listOf(deepFile), 0L)
        val sub1 = createFileNode("/test/sub1", "sub1", 0, true, false, listOf(sub2), 0L)
        val testNode = createFileNode("/test", "test", 0, true, false, listOf(sub1), 0L)
        repository.addFile(testNode)

        // When
        val scanResult = useCase.scan("/test")
            .toList()
            .filterIsInstance<ScanStatus.Completed>()
            .single()
            .result
        assertEquals(500, scanResult.totalSize)
        assertEquals(1, scanResult.fileCount)
        assertEquals(2, scanResult.directoryCount) // sub1 and sub2
    }

    @Test
    fun `refreshNode replaces subtree and forwards progress through the store`() = runTest {
        val oldInner = createFileNode("/test/dir/old.txt", "old.txt", 100, false, false, emptyList(), 0L)
        val initialDir = createFileNode("/test/dir", "dir", 0, true, false, listOf(oldInner), 0L)
        val sibling = createFileNode("/test/keep.txt", "keep.txt", 42, false, false, emptyList(), 0L)
        val root = createFileNode("/test", "test", 0, true, false, listOf(initialDir, sibling), 0L)
        repository.addFile(root)

        val store = disksize.domain.FileTreeStore()
        store.applyComplete(root, emptyList(), 0)

        // Now mutate disk: /test/dir gets new content.
        val newInner = createFileNode("/test/dir/new.bin", "new.bin", 555, false, false, emptyList(), 0L)
        val updatedDir = createFileNode("/test/dir", "dir", 0, true, false, listOf(newInner), 0L)
        repository.addFile(updatedDir)

        useCase.refreshNode("/test/dir", store)

        val final = store.state.value
        assertEquals(null, final.refreshingPath, "refreshingPath should be cleared after success")
        assertEquals(null, final.scanProgress, "scanProgress should be cleared after success")
        val dirNode = final.rootNode!!.children.first { it.name == "dir" }
        assertEquals(1, dirNode.children.size)
        assertEquals("new.bin", dirNode.children[0].name)
        assertEquals(555, dirNode.totalSize())
        // Sibling untouched.
        assertTrue(final.rootNode!!.children.any { it.name == "keep.txt" })
    }

    @Test
    fun `should forward progress updates`() = runTest {
        val deepFile = createFileNode("/test/sub/file.txt", "file.txt", 100, false, false, emptyList(), 0L)
        val subDir = createFileNode("/test/sub", "sub", 0, true, false, listOf(deepFile), 0L)
        val sibling = createFileNode("/test/other.txt", "other.txt", 50, false, false, emptyList(), 0L)
        val root = createFileNode("/test", "test", 0, true, false, listOf(subDir, sibling), 0L)
        repository.addFile(root)

        val emissions = useCase.scan("/test").toList()
        val progressUpdates = emissions.filterIsInstance<ScanStatus.Progress>().map { it.value }
        assertTrue(progressUpdates.isNotEmpty())
        val final = progressUpdates.last()
        assertEquals(1, final.processedDirectories)
        assertEquals(2, final.processedFiles)
        assertTrue(emissions.any { it is ScanStatus.Completed })
    }
}
