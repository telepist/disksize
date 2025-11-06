package disksize.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileNodeTest {

    @Test
    fun `should create file node with basic properties`() {
        val node = FileNode(
            path = "/test/file.txt",
            name = "file.txt",
            size = 1024,
            isDirectory = false,
            isSymlink = false,
            children = emptyList(),
            lastModified = 1234567890L
        )

        assertEquals("/test/file.txt", node.path)
        assertEquals("file.txt", node.name)
        assertEquals(1024, node.size)
        assertFalse(node.isDirectory)
        assertTrue(node.children.isEmpty())
        assertEquals(1234567890L, node.lastModified)
    }

    @Test
    fun `should create directory node with children`() {
        val child1 = FileNode(
            path = "/test/dir/file1.txt",
            name = "file1.txt",
            size = 100,
            isDirectory = false,
            isSymlink = false,
            children = emptyList(),
            lastModified = 0L
        )

        val child2 = FileNode(
            path = "/test/dir/file2.txt",
            name = "file2.txt",
            size = 200,
            isDirectory = false,
            isSymlink = false,
            children = emptyList(),
            lastModified = 0L
        )

        val directory = FileNode(
            path = "/test/dir",
            name = "dir",
            size = 0,
            isDirectory = true,
            isSymlink = false,
            children = listOf(child1, child2),
            lastModified = 0L
        )

        assertTrue(directory.isDirectory)
        assertEquals(2, directory.children.size)
        assertEquals(listOf(child1, child2), directory.children)
    }

    @Test
    fun `should calculate total size including children`() {
        val child1 = FileNode("/test/file1.txt", "file1.txt", 100, false, false, emptyList(), 0L)
        val child2 = FileNode("/test/file2.txt", "file2.txt", 200, false, false, emptyList(), 0L)
        val subdir = FileNode("/test/subdir", "subdir", 0, true, false,
            listOf(
                FileNode("/test/subdir/file3.txt", "file3.txt", 300, false, false, emptyList(), 0L)
            ), 0L
        )

        val root = FileNode(
            path = "/test",
            name = "test",
            size = 50, // Directory's own metadata size
            isDirectory = true,
            isSymlink = false,
            children = listOf(child1, child2, subdir),
            lastModified = 0L
        )

        val totalSize = root.totalSize()

        // Should include directory size + all children recursively: 50 + 100 + 200 + (0 + 300)
        assertEquals(650, totalSize)
    }

    @Test
    fun `should return size for file without children`() {
        val file = FileNode("/test/file.txt", "file.txt", 1024, false, false, emptyList(), 0L)

        assertEquals(1024, file.totalSize())
    }

    @Test
    fun `should return zero for empty directory`() {
        val emptyDir = FileNode("/test/empty", "empty", 0, true, false, emptyList(), 0L)

        assertEquals(0, emptyDir.totalSize())
    }

    @Test
    fun `should check if directory is empty`() {
        val emptyDir = FileNode("/test/empty", "empty", 0, true, false, emptyList(), 0L)
        val nonEmptyDir = FileNode("/test/nonempty", "nonempty", 0, true, false,
            listOf(FileNode("/test/nonempty/file.txt", "file.txt", 100, false, false, emptyList(), 0L)),
            0L
        )

        assertTrue(emptyDir.isEmpty())
        assertFalse(nonEmptyDir.isEmpty())
    }

    @Test
    fun `should count total files recursively`() {
        val child1 = FileNode("/test/file1.txt", "file1.txt", 100, false, false, emptyList(), 0L)
        val child2 = FileNode("/test/file2.txt", "file2.txt", 200, false, false, emptyList(), 0L)
        val subdir = FileNode("/test/subdir", "subdir", 0, true, false,
            listOf(
                FileNode("/test/subdir/file3.txt", "file3.txt", 300, false, false, emptyList(), 0L),
                FileNode("/test/subdir/file4.txt", "file4.txt", 400, false, false, emptyList(), 0L)
            ), 0L
        )

        val root = FileNode("/test", "test", 0, true, false, listOf(child1, child2, subdir), 0L)

        assertEquals(4, root.fileCount())
    }

    @Test
    fun `should count total directories recursively`() {
        val subsubdir = FileNode("/test/sub/subsub", "subsub", 0, true, false, emptyList(), 0L)
        val subdir1 = FileNode("/test/sub1", "sub1", 0, true, false, listOf(subsubdir), 0L)
        val subdir2 = FileNode("/test/sub2", "sub2", 0, true, false, emptyList(), 0L)

        val root = FileNode("/test", "test", 0, true, false, listOf(subdir1, subdir2), 0L)

        // Should count sub1, sub2, and subsub = 3
        assertEquals(3, root.directoryCount())
    }

    @Test
    fun `should return zero counts for file node`() {
        val file = FileNode("/test/file.txt", "file.txt", 1024, false, false, emptyList(), 0L)

        assertEquals(1, file.fileCount()) // The file itself
        assertEquals(0, file.directoryCount())
    }
}
