package disksize.domain.usecase

import disksize.data.fake.FakeFileSystemRepository
import disksize.domain.model.DeletionResult
import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.createFileNode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteFileUseCaseTest {

    @Test
    fun `should delete file successfully`() = runTest {
        val repository = FakeFileSystemRepository()
        val useCase = DeleteFileUseCase(repository)

        val file = createFileNode(
            path = "/test/file.txt",
            name = "file.txt",
            size = 1024,
            isDirectory = false,
            isSymlink = false,
            children = emptyList(),
            lastModified = 0L
        )
        repository.addFile(file)

        val result = useCase.delete("/test/file.txt")

        assertTrue(result is DeletionResult.Success)
        assertEquals("/test/file.txt", result.path)
        assertEquals(1, result.itemsDeleted)
        assertEquals(1024, result.bytesFreed)
    }

    @Test
    fun `should delete directory recursively`() = runTest {
        val repository = FakeFileSystemRepository()
        val useCase = DeleteFileUseCase(repository)

        val child1 = createFileNode("/test/dir/file1.txt", "file1.txt", 100, false, false, emptyList(), 0L)
        val child2 = createFileNode("/test/dir/file2.txt", "file2.txt", 200, false, false, emptyList(), 0L)
        val directory = createFileNode(
            path = "/test/dir",
            name = "dir",
            size = 0,
            isDirectory = true,
            isSymlink = false,
            children = listOf(child1, child2),
            lastModified = 0L
        )
        repository.addFile(directory)
        repository.addFile(child1)
        repository.addFile(child2)

        val result = useCase.delete("/test/dir")

        assertTrue(result is DeletionResult.Success)
        assertEquals("/test/dir", result.path)
        assertEquals(3, result.itemsDeleted) // dir + 2 files
        assertEquals(300, result.bytesFreed)
    }

    @Test
    fun `should return failure when file not found`() = runTest {
        val repository = FakeFileSystemRepository()
        val useCase = DeleteFileUseCase(repository)

        val result = useCase.delete("/nonexistent/file.txt")

        assertTrue(result is DeletionResult.Failure)
        assertEquals("/nonexistent/file.txt", result.path)
        assertEquals(ErrorType.NOT_FOUND, result.errorType)
    }

    @Test
    fun `should return failure on permission denied`() = runTest {
        val repository = FakeFileSystemRepository()
        val useCase = DeleteFileUseCase(repository)

        val file = createFileNode("/test/restricted.txt", "restricted.txt", 1024, false, false, emptyList(), 0L)
        repository.addFile(file)
        repository.markInaccessible("/test/restricted.txt")

        val result = useCase.delete("/test/restricted.txt")

        assertTrue(result is DeletionResult.Failure)
        assertEquals("/test/restricted.txt", result.path)
        assertEquals(ErrorType.PERMISSION_DENIED, result.errorType)
    }
}
