package disksize.data.fake

import disksize.domain.model.FileNode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeFileSystemRepositoryTest {

    @Test
    fun `should return file node when path exists`() = runTest {
        val repo = FakeFileSystemRepository()
        val testNode = FileNode(
            path = "/test",
            name = "test",
            size = 0,
            isDirectory = true,
            children = emptyList(),
            lastModified = 0L
        )
        repo.addFile(testNode)

        val result = repo.scanDirectory("/test")

        assertTrue(result.isSuccess)
        val scanResult = result.getOrNull()!!
        assertEquals(testNode, scanResult.root)
        assertTrue(scanResult.errors.isEmpty())
    }

    @Test
    fun `should return error when directory not found`() = runTest {
        val repo = FakeFileSystemRepository()

        val result = repo.scanDirectory("/nonexistent")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    @Test
    fun `should return error when path is not a directory`() = runTest {
        val repo = FakeFileSystemRepository()
        val fileNode = FileNode(
            path = "/test/file.txt",
            name = "file.txt",
            size = 1024,
            isDirectory = false,
            children = emptyList(),
            lastModified = 0L
        )
        repo.addFile(fileNode)

        val result = repo.scanDirectory("/test/file.txt")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Not a directory") == true)
    }

    @Test
    fun `should return error when path is inaccessible`() = runTest {
        val repo = FakeFileSystemRepository()
        val testNode = FileNode(
            path = "/restricted",
            name = "restricted",
            size = 0,
            isDirectory = true,
            children = emptyList(),
            lastModified = 0L
        )
        repo.addFile(testNode)
        repo.markInaccessible("/restricted")

        val result = repo.scanDirectory("/restricted")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Permission denied") == true)
    }

    @Test
    fun `should return file info without children`() = runTest {
        val repo = FakeFileSystemRepository()
        val child = FileNode("/test/child", "child", 100, false, emptyList(), 0L)
        val testNode = FileNode(
            path = "/test",
            name = "test",
            size = 0,
            isDirectory = true,
            children = listOf(child),
            lastModified = 0L
        )
        repo.addFile(testNode)

        val result = repo.getFileInfo("/test")

        assertTrue(result.isSuccess)
        val info = result.getOrNull()!!
        assertEquals("/test", info.path)
        assertTrue(info.children.isEmpty())
    }

    @Test
    fun `should check if path exists`() = runTest {
        val repo = FakeFileSystemRepository()
        val testNode = FileNode("/test", "test", 0, true, emptyList(), 0L)
        repo.addFile(testNode)

        assertTrue(repo.exists("/test"))
        assertFalse(repo.exists("/nonexistent"))
    }

    @Test
    fun `should check if path is accessible`() = runTest {
        val repo = FakeFileSystemRepository()
        val testNode = FileNode("/test", "test", 0, true, emptyList(), 0L)
        repo.addFile(testNode)
        repo.markInaccessible("/test")

        assertFalse(repo.isAccessible("/test"))
        assertTrue(repo.isAccessible("/nonexistent") == false)
    }

    @Test
    fun `should clear all files`() = runTest {
        val repo = FakeFileSystemRepository()
        repo.addFile(FileNode("/test1", "test1", 0, true, emptyList(), 0L))
        repo.addFile(FileNode("/test2", "test2", 0, true, emptyList(), 0L))
        repo.markInaccessible("/test1")

        repo.clear()

        assertFalse(repo.exists("/test1"))
        assertFalse(repo.exists("/test2"))
        // After clear, paths should no longer be marked as inaccessible
        repo.addFile(FileNode("/test1", "test1", 0, true, emptyList(), 0L))
        assertTrue(repo.isAccessible("/test1"))
    }

    @Test
    fun `should include errors for inaccessible children`() = runTest {
        val repo = FakeFileSystemRepository()
        val child = FileNode("/test/secret", "secret", 0, true, emptyList(), 0L)
        val parent = FileNode(
            path = "/test",
            name = "test",
            size = 0,
            isDirectory = true,
            children = listOf(child),
            lastModified = 0L
        )
        repo.addFile(child)
        repo.addFile(parent)
        repo.markInaccessible(child.path)

        val result = repo.scanDirectory("/test")

        assertTrue(result.isSuccess)
        val scanResult = result.getOrNull()!!
        assertTrue(scanResult.root.children.isEmpty())
        assertEquals(1, scanResult.errors.size)
        assertEquals(child.path, scanResult.errors.first().path)
    }
}
