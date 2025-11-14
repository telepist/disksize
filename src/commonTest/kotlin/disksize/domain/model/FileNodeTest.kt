package disksize.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileNodeTest {

    @Test
    fun `should create file node with basic properties`() {
        val node = createFileNode(
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
        val child1 = createFileNode(
            path = "/test/dir/file1.txt",
            name = "file1.txt",
            size = 100,
            isDirectory = false,
            isSymlink = false,
            children = emptyList(),
            lastModified = 0L
        )

        val child2 = createFileNode(
            path = "/test/dir/file2.txt",
            name = "file2.txt",
            size = 200,
            isDirectory = false,
            isSymlink = false,
            children = emptyList(),
            lastModified = 0L
        )

        val directory = createFileNode(
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
        val child1 = createFileNode("/test/file1.txt", "file1.txt", 100, false)
        val child2 = createFileNode("/test/file2.txt", "file2.txt", 200, false)
        val subdir = createFileNode("/test/subdir", "subdir", 0, true,
            children = listOf(
                createFileNode("/test/subdir/file3.txt", "file3.txt", 300, false)
            )
        )

        val root = createFileNode(
            path = "/test",
            name = "test",
            size = 50, // Directory's own metadata size
            isDirectory = true,
            children = listOf(child1, child2, subdir)
        )

        val totalSize = root.totalSize()

        // Should include directory size + all children recursively: 50 + 100 + 200 + (0 + 300)
        assertEquals(650, totalSize)
    }

    @Test
    fun `should return size for file without children`() {
        val file = createFileNode("/test/file.txt", "file.txt", 1024, false)

        assertEquals(1024, file.totalSize())
    }

    @Test
    fun `should return zero for empty directory`() {
        val emptyDir = createFileNode("/test/empty", "empty", 0, true)

        assertEquals(0, emptyDir.totalSize())
    }

    @Test
    fun `should check if directory is empty`() {
        val emptyDir = createFileNode("/test/empty", "empty", 0, true)
        val nonEmptyDir = createFileNode("/test/nonempty", "nonempty", 0, true,
            children = listOf(createFileNode("/test/nonempty/file.txt", "file.txt", 100, false))
        )

        assertTrue(emptyDir.isEmpty())
        assertFalse(nonEmptyDir.isEmpty())
    }

    @Test
    fun `should count total files recursively`() {
        val child1 = createFileNode("/test/file1.txt", "file1.txt", 100, false)
        val child2 = createFileNode("/test/file2.txt", "file2.txt", 200, false)
        val subdir = createFileNode("/test/subdir", "subdir", 0, true,
            children = listOf(
                createFileNode("/test/subdir/file3.txt", "file3.txt", 300, false),
                createFileNode("/test/subdir/file4.txt", "file4.txt", 400, false)
            )
        )

        val root = createFileNode("/test", "test", 0, true, children = listOf(child1, child2, subdir))

        assertEquals(4, root.fileCount())
    }

    @Test
    fun `should count total directories recursively`() {
        val subsubdir = createFileNode("/test/sub/subsub", "subsub", 0, true)
        val subdir1 = createFileNode("/test/sub1", "sub1", 0, true, children = listOf(subsubdir))
        val subdir2 = createFileNode("/test/sub2", "sub2", 0, true)

        val root = createFileNode("/test", "test", 0, true, children = listOf(subdir1, subdir2))

        // Should count sub1, sub2, and subsub = 3
        assertEquals(3, root.directoryCount())
    }

    @Test
    fun `should return zero counts for file node`() {
        val file = createFileNode("/test/file.txt", "file.txt", 1024, false)

        assertEquals(1, file.fileCount()) // The file itself
        assertEquals(0, file.directoryCount())
    }
}
