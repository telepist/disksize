package disksize.presentation

import disksize.domain.model.FileNode
import disksize.domain.model.ScanProgress
import disksize.domain.model.ScanResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplorerStateTest {

    @Test
    fun `withScanResult sorts directories descending and resets selection`() {
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 600,
            fileCount = 0,
            directoryCount = 3,
            rootNode = directory(
                path = "/tmp",
                name = "tmp",
                children = listOf(
                    directory("/tmp/a", "a", size = 100),
                    directory("/tmp/b", "b", size = 300),
                    directory("/tmp/c", "c", size = 200)
                )
            ),
            scanDurationMs = 42,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp", selectedIndex = 2)
        val updated = initial.withScanResult(scanResult).resetSelection()

        val names = updated.browserItems.map { it.node.name }
        assertEquals(listOf("b", "c", "a"), names)
        assertEquals(0, updated.selectedIndex)
        assertEquals(600, updated.totalSize)
        assertEquals(600, updated.childDirectoryTotalSize)
        assertEquals(0, updated.spinnerIndex)
        assertEquals(null, updated.loadingDirectoryPath)
    }

    @Test
    fun `withSelection clamps index`() {
        val state = ExplorerState(currentPath = "/tmp").withScanResult(
            ScanResult(
                rootPath = "/tmp",
                totalSize = 300,
                fileCount = 0,
                directoryCount = 3,
                rootNode = directory(
                    path = "/tmp",
                    name = "tmp",
                    children = listOf(
                        directory("/tmp/a", "a", size = 100),
                        directory("/tmp/b", "b", size = 100),
                        directory("/tmp/c", "c", size = 100)
                    )
                ),
                scanDurationMs = 1,
                errors = emptyList()
            )
        )

        assertEquals(2, state.withSelection(5).selectedIndex)
        assertEquals(0, state.withSelection(-1).selectedIndex)
    }

    @Test
    fun `withNextSortOrder cycles sort order and preserves selection`() {
        val childSize = directory("/root/size", "size", size = 300, lastModified = 1_000)
        val childName = directory("/root/alpha", "alpha", size = 100, lastModified = 2_000)
        val childDate = directory("/root/date", "date", size = 200, lastModified = 3_000)
        val scanResult = ScanResult(
            rootPath = "/root",
            totalSize = 600,
            fileCount = 0,
            directoryCount = 3,
            rootNode = directory(
                path = "/root",
                name = "root",
                children = listOf(childSize, childName, childDate)
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        var state = ExplorerState(currentPath = "/root").withScanResult(scanResult)
        // Initially sorted by size desc (size, date, alpha)
        assertEquals(listOf("size", "date", "alpha"), state.browserItems.map { it.node.name })

        state = state.withNextSortOrder()
        assertEquals(SortOrder.NAME_ASC, state.sortOrder)
        assertEquals(listOf("alpha", "date", "size"), state.browserItems.map { it.node.name })

        state = state.withNextSortOrder()
        assertEquals(SortOrder.DATE_DESC, state.sortOrder)
        assertEquals(listOf("date", "alpha", "size"), state.browserItems.map { it.node.name })
        assertEquals(600, state.childDirectoryTotalSize)
    }

    @Test
    fun `withScanResult includes files after directories`() {
        val scanResult = ScanResult(
            rootPath = "/root",
            totalSize = 450,
            fileCount = 2,
            directoryCount = 2,
            rootNode = directory(
                path = "/root",
                name = "root",
                children = listOf(
                    directory("/root/dirA", "dirA", size = 200),
                    file("/root/readme.md", "readme.md", size = 50),
                    directory("/root/dirB", "dirB", size = 150),
                    file("/root/todo.txt", "todo.txt", size = 25)
                )
            ),
            scanDurationMs = 10,
            errors = emptyList()
        )

        val state = ExplorerState(currentPath = "/root").withScanResult(scanResult)

        val names = state.browserItems.map { it.node.name }
        assertEquals(listOf("dirA", "dirB", "readme.md", "todo.txt"), names)
        assertEquals(350, state.childDirectoryTotalSize)
    }

    @Test
    fun `warning count returns scan errors size`() {
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 0,
            fileCount = 0,
            directoryCount = 0,
            rootNode = directory("/tmp", "tmp"),
            scanDurationMs = 0,
            errors = listOf(
                disksize.domain.model.ScanError("/tmp/secret", "Permission denied", disksize.domain.model.ErrorType.PERMISSION_DENIED)
            )
        )

        val state = ExplorerState(currentPath = "/tmp").withScanResult(scanResult)

        assertEquals(1, state.warningCount)
        assertFalse(state.scanResult!!.isSuccessful())
    }

    @Test
    fun `spinner advances while loading`() {
        var state = ExplorerState(currentPath = "/tmp").withLoading("/tmp")
        state = state.tickSpinner()
        assertEquals(1, state.spinnerIndex)
        val nextFrame = state.spinnerFrame
        assertTrue(nextFrame in charArrayOf('|', '/', '-', '\\'))
    }

    @Test
    fun `withProgress stores latest loading progress`() {
        val base = ExplorerState(currentPath = "/tmp", isLoading = true)
        val progress = ScanProgress(
            processedFiles = 3,
            processedDirectories = 2,
            scannedBytes = 1024,
            bytesPerSecond = 512
        )

        val updated = base.withProgress(progress)

        val expected = LoadingProgress.fromDomain(progress)
        assertEquals(expected, updated.loadingProgress)
        assertEquals(null, updated.loadingDirectoryPath)
    }

    @Test
    fun `withLoading resets previous progress`() {
        val progress = LoadingProgress(
            processedFiles = 1,
            processedDirectories = 1,
            scannedBytes = 1024,
            bytesPerSecond = 512
        )
        val state = ExplorerState(
            currentPath = "/tmp",
            isLoading = false,
            loadingProgress = progress
        )

        val reset = state.withLoading("/tmp")

        assertEquals(null, reset.loadingProgress)
        assertTrue(reset.isLoading)
        assertEquals(null, reset.loadingDirectoryPath)
    }

    @Test
    fun `withProgress retains latest directory`() {
        var state = ExplorerState(currentPath = "/tmp", isLoading = true)
        state = state.withProgress(
            ScanProgress(
                processedFiles = 1,
                processedDirectories = 0,
                scannedBytes = 1024,
                bytesPerSecond = 512,
                currentDirectory = "/tmp/dir"
            )
        )

        assertEquals("/tmp/dir", state.loadingDirectoryPath)

        state = state.withProgress(
            ScanProgress(
                processedFiles = 2,
                processedDirectories = 1,
                scannedBytes = 2048,
                bytesPerSecond = 512,
                currentDirectory = null
            )
        )

        assertEquals("/tmp/dir", state.loadingDirectoryPath)
    }

    @Test
    fun `withItemDeleted removes file from browser items`() {
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 300,
            fileCount = 2,
            directoryCount = 1,
            rootNode = directory(
                path = "/tmp",
                name = "tmp",
                children = listOf(
                    directory("/tmp/dir", "dir", size = 100),
                    file("/tmp/file1.txt", "file1.txt", size = 100),
                    file("/tmp/file2.txt", "file2.txt", size = 100)
                )
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp").withScanResult(scanResult)
        assertEquals(3, initial.browserItems.size)

        val updated = initial.withItemDeleted("/tmp/file1.txt")

        assertEquals(2, updated.browserItems.size)
        assertEquals(listOf("dir", "file2.txt"), updated.browserItems.map { it.node.name })
        assertEquals(200, updated.scanResult!!.totalSize)
        assertEquals(1, updated.scanResult!!.fileCount)
    }

    @Test
    fun `withItemDeleted removes directory from browser items`() {
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 300,
            fileCount = 0,
            directoryCount = 3,
            rootNode = directory(
                path = "/tmp",
                name = "tmp",
                children = listOf(
                    directory("/tmp/a", "a", size = 100),
                    directory("/tmp/b", "b", size = 100),
                    directory("/tmp/c", "c", size = 100)
                )
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp").withScanResult(scanResult)
        assertEquals(3, initial.browserItems.size)

        val updated = initial.withItemDeleted("/tmp/b")

        assertEquals(2, updated.browserItems.size)
        assertEquals(listOf("a", "c"), updated.browserItems.map { it.node.name })
        assertEquals(200, updated.scanResult!!.totalSize)
        assertEquals(2, updated.scanResult!!.directoryCount)
        assertEquals(200, updated.childDirectoryTotalSize)
    }

    @Test
    fun `withItemDeleted updates nested tree structure`() {
        // Create a nested structure: /tmp/parent/child/grandchild.txt
        val grandchild = file("/tmp/parent/child/grandchild.txt", "grandchild.txt", size = 50)
        val child = directory(
            path = "/tmp/parent/child",
            name = "child",
            children = listOf(grandchild),
            size = 0
        )
        val parent = directory(
            path = "/tmp/parent",
            name = "parent",
            children = listOf(child),
            size = 0
        )
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 50,
            fileCount = 1,
            directoryCount = 2,
            rootNode = directory(
                path = "/tmp",
                name = "tmp",
                children = listOf(parent)
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp").withScanResult(scanResult)
        val updated = initial.withItemDeleted("/tmp/parent/child/grandchild.txt")

        // Verify the file is removed from the nested structure
        val updatedParent = updated.scanResult!!.rootNode.children.first()
        val updatedChild = updatedParent.children.first()
        assertEquals(0, updatedChild.children.size)
        assertEquals(0, updated.scanResult!!.totalSize)
        assertEquals(0, updated.scanResult!!.fileCount)
    }

    @Test
    fun `withItemDeleted ensures navigating into subdirectory shows updated tree`() {
        // Create a structure where we delete a file deep in the tree
        val fileToDelete = file("/tmp/parent/child/delete-me.txt", "delete-me.txt", size = 100)
        val fileToKeep = file("/tmp/parent/child/keep-me.txt", "keep-me.txt", size = 50)
        val child = directory(
            path = "/tmp/parent/child",
            name = "child",
            children = listOf(fileToDelete, fileToKeep),
            size = 0
        )
        val parent = directory(
            path = "/tmp/parent",
            name = "parent",
            children = listOf(child),
            size = 0
        )
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 150,
            fileCount = 2,
            directoryCount = 2,
            rootNode = directory(
                path = "/tmp",
                name = "tmp",
                children = listOf(parent)
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp").withScanResult(scanResult)
        val afterDelete = initial.withItemDeleted("/tmp/parent/child/delete-me.txt")

        // Get the parent directory node from the updated tree
        val updatedParent = afterDelete.browserItems.first().node

        // Verify that if we navigate into parent, it has the updated child
        val updatedChild = updatedParent.children.first()
        assertEquals(1, updatedChild.children.size)
        assertEquals("keep-me.txt", updatedChild.children.first().name)

        // Verify the deleted file is not in the tree
        val allPaths = collectAllPaths(afterDelete.scanResult!!.rootNode)
        assertFalse(allPaths.contains("/tmp/parent/child/delete-me.txt"))
        assertTrue(allPaths.contains("/tmp/parent/child/keep-me.txt"))
    }

    @Test
    fun `withItemDeleted adjusts selected index when last item is deleted`() {
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 200,
            fileCount = 0,
            directoryCount = 2,
            rootNode = directory(
                path = "/tmp",
                name = "tmp",
                children = listOf(
                    directory("/tmp/a", "a", size = 100),
                    directory("/tmp/b", "b", size = 100)
                )
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp", selectedIndex = 1)
            .withScanResult(scanResult)
            .withSelection(1)

        val updated = initial.withItemDeleted("/tmp/b")

        assertEquals(1, updated.browserItems.size)
        assertEquals(0, updated.selectedIndex) // Should clamp to last valid index
    }

    private fun collectAllPaths(node: FileNode): Set<String> {
        val paths = mutableSetOf(node.path)
        for (child in node.children) {
            paths.addAll(collectAllPaths(child))
        }
        return paths
    }

    private fun directory(
        path: String,
        name: String,
        size: Long = 0,
        children: List<FileNode> = emptyList(),
        lastModified: Long = 0L
    ): FileNode {
        return FileNode(
            path = path,
            name = name,
            size = size,
            isDirectory = true,
            isSymlink = false,
            children = children,
            lastModified = lastModified
        )
    }

    private fun file(
        path: String,
        name: String,
        size: Long,
        lastModified: Long = 0L
    ): FileNode {
        return FileNode(
            path = path,
            name = name,
            size = size,
            isDirectory = false,
            isSymlink = false,
            children = emptyList(),
            lastModified = lastModified
        )
    }
}
