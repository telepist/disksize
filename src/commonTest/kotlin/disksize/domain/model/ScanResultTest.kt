package disksize.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanResultTest {

    @Test
    fun `should create scan result with basic properties`() {
        val rootNode = createFileNode("/test", "test", 0, true, false, emptyList(), 0L)
        val result = ScanResult(
            rootPath = "/test",
            totalSize = 1024L,
            fileCount = 10,
            directoryCount = 2,
            rootNode = rootNode,
            scanDurationMs = 500L,
            errors = emptyList()
        )

        assertEquals("/test", result.rootPath)
        assertEquals(1024L, result.totalSize)
        assertEquals(10, result.fileCount)
        assertEquals(2, result.directoryCount)
        assertEquals(rootNode, result.rootNode)
        assertEquals(500L, result.scanDurationMs)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `should create scan result with errors`() {
        val rootNode = createFileNode("/test", "test", 0, true, false, emptyList(), 0L)
        val errors = listOf(
            ScanError("/test/restricted", "Permission denied", ErrorType.PERMISSION_DENIED),
            ScanError("/test/missing", "File not found", ErrorType.NOT_FOUND)
        )

        val result = ScanResult(
            rootPath = "/test",
            totalSize = 0L,
            fileCount = 0,
            directoryCount = 0,
            rootNode = rootNode,
            scanDurationMs = 100L,
            errors = errors
        )

        assertEquals(2, result.errors.size)
        assertEquals("/test/restricted", result.errors[0].path)
        assertEquals(ErrorType.PERMISSION_DENIED, result.errors[0].type)
    }

    @Test
    fun `should check if scan has errors`() {
        val rootNode = createFileNode("/test", "test", 0, true, false, emptyList(), 0L)

        val resultWithoutErrors = ScanResult(
            rootPath = "/test",
            totalSize = 0L,
            fileCount = 0,
            directoryCount = 0,
            rootNode = rootNode,
            scanDurationMs = 100L,
            errors = emptyList()
        )

        val resultWithErrors = ScanResult(
            rootPath = "/test",
            totalSize = 0L,
            fileCount = 0,
            directoryCount = 0,
            rootNode = rootNode,
            scanDurationMs = 100L,
            errors = listOf(ScanError("/test/error", "Error", ErrorType.IO_ERROR))
        )

        assertTrue(resultWithoutErrors.isSuccessful())
        assertTrue(!resultWithErrors.isSuccessful())
    }
}

class ScanErrorTest {

    @Test
    fun `should create scan error with properties`() {
        val error = ScanError(
            path = "/test/error.txt",
            message = "Permission denied",
            type = ErrorType.PERMISSION_DENIED
        )

        assertEquals("/test/error.txt", error.path)
        assertEquals("Permission denied", error.message)
        assertEquals(ErrorType.PERMISSION_DENIED, error.type)
    }

    @Test
    fun `should create errors of different types`() {
        val permissionError = ScanError("/test/1", "Denied", ErrorType.PERMISSION_DENIED)
        val notFoundError = ScanError("/test/2", "Not found", ErrorType.NOT_FOUND)
        val ioError = ScanError("/test/3", "I/O error", ErrorType.IO_ERROR)
        val unknownError = ScanError("/test/4", "Unknown", ErrorType.UNKNOWN)

        assertEquals(ErrorType.PERMISSION_DENIED, permissionError.type)
        assertEquals(ErrorType.NOT_FOUND, notFoundError.type)
        assertEquals(ErrorType.IO_ERROR, ioError.type)
        assertEquals(ErrorType.UNKNOWN, unknownError.type)
    }
}
