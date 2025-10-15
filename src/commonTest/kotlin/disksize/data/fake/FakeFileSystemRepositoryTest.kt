package disksize.data.fake

import disksize.data.DirectoryScanUpdate
import disksize.domain.model.FileNode
import disksize.domain.model.ScanProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.toList

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

        val updates = repo.scanDirectory("/test").toList()

        val complete = updates.filterIsInstance<DirectoryScanUpdate.Complete>().single()
        assertEquals(testNode, complete.result.root)
        assertTrue(complete.result.errors.isEmpty())
    }

    @Test
    fun `should return error when directory not found`() = runTest {
        val repo = FakeFileSystemRepository()

        val exception = assertFailsWith<Exception> {
            repo.scanDirectory("/nonexistent").toList()
        }
        assertTrue(exception.message?.contains("not found") == true)
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

        val exception = assertFailsWith<Exception> {
            repo.scanDirectory("/test/file.txt").toList()
        }
        assertTrue(exception.message?.contains("Not a directory") == true)
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

        val exception = assertFailsWith<Exception> {
            repo.scanDirectory("/restricted").toList()
        }
        assertTrue(exception.message?.contains("Permission denied") == true)
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

        val updates = repo.scanDirectory("/test").toList()
        val complete = updates.filterIsInstance<DirectoryScanUpdate.Complete>().single()
        assertTrue(complete.result.root.children.isEmpty())
        assertEquals(1, complete.result.errors.size)
        assertEquals(child.path, complete.result.errors.first().path)
    }

    @Test
    fun `should emit progress updates`() = runTest {
        val repo = FakeFileSystemRepository()
        val leaf = FileNode("/test/sub/file.txt", "file.txt", 10, false, emptyList(), 0L)
        val subDir = FileNode("/test/sub", "sub", 0, true, listOf(leaf), 0L)
        val sibling = FileNode("/test/other.txt", "other.txt", 5, false, emptyList(), 0L)
        val root = FileNode("/test", "test", 0, true, listOf(subDir, sibling), 0L)
        repo.addFile(root)

        val updates = mutableListOf<ScanProgress>()

        val emissions = repo.scanDirectory("/test").toList()
        emissions.filterIsInstance<DirectoryScanUpdate.Progress>().forEach { updates += it.progress }

        assertTrue(emissions.any { it is DirectoryScanUpdate.Complete })
        assertTrue(updates.isNotEmpty())
        val final = updates.last()
        assertEquals(2, final.totalFiles)
        assertEquals(1, final.totalDirectories)
        assertEquals(final.totalFiles, final.processedFiles)
        assertEquals(final.totalDirectories, final.processedDirectories)
    }
}
