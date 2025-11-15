package disksize.data

import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import kotlinx.coroutines.flow.Flow
import kotlin.test.Test
import kotlin.test.assertEquals

private class TestFileSystemRepository : FileSystemRepository() {
    override suspend fun scanDirectoryRecursive(
        path: String,
        errors: MutableList<ScanError>,
        tracker: AdaptiveProgressTracker,
        isRoot: Boolean
    ): FileNode {
        throw UnsupportedOperationException("Not used in tests")
    }

    override fun createFileNode(path: String): FileNode {
        throw UnsupportedOperationException("Not used in tests")
    }

    override suspend fun exists(path: String): Boolean = false

    override suspend fun isAccessible(path: String): Boolean = false

    override fun deleteFile(path: String) {
        throw UnsupportedOperationException("Not used in tests")
    }

    override fun deleteDirectoryRecursive(path: String): Int =
        throw UnsupportedOperationException("Not used in tests")
}

class FileSystemRepositoryErrorClassificationTest {

    private val repository = TestFileSystemRepository()

    @Test
    fun `classifies permission denied style messages`() {
        assertEquals(
            ErrorType.PERMISSION_DENIED,
            repository.classifyErrorMessage("Permission denied: /secret")
        )
        assertEquals(
            ErrorType.PERMISSION_DENIED,
            repository.classifyErrorMessage("ACCESS DENIED for C:\\\\Windows")
        )
        assertEquals(
            ErrorType.PERMISSION_DENIED,
            repository.classifyErrorMessage("Operation not permitted")
        )
        assertEquals(
            ErrorType.PERMISSION_DENIED,
            repository.classifyErrorMessage("Read-only file system")
        )
    }

    @Test
    fun `classifies not found style messages`() {
        assertEquals(
            ErrorType.NOT_FOUND,
            repository.classifyErrorMessage("File not found: /missing")
        )
        assertEquals(
            ErrorType.NOT_FOUND,
            repository.classifyErrorMessage("No such file or directory")
        )
        assertEquals(
            ErrorType.NOT_FOUND,
            repository.classifyErrorMessage("Cannot find the file specified")
        )
        assertEquals(
            ErrorType.NOT_FOUND,
            repository.classifyErrorMessage("Cannot find the path specified")
        )
    }

    @Test
    fun `falls back to IO_ERROR when message is known but not matched specifically`() {
        assertEquals(
            ErrorType.IO_ERROR,
            repository.classifyErrorMessage("Generic I/O failure")
        )
    }

    @Test
    fun `returns UNKNOWN when message is null`() {
        assertEquals(
            ErrorType.UNKNOWN,
            repository.classifyErrorMessage(null)
        )
    }
}

