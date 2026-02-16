package disksize.presentation

import disksize.domain.model.FileNode
import disksize.domain.model.ScanProgress
import disksize.domain.model.ScanResult
import disksize.domain.model.createFileNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun `withNodeUpdated replaces node at root level`() {
        val oldChild = file("/tmp/old.txt", "old.txt", size = 100)
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 100,
            fileCount = 1,
            directoryCount = 1,
            rootNode = directory(
                path = "/tmp",
                name = "tmp",
                children = listOf(oldChild)
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp").withScanResult(scanResult)
        assertEquals(1, initial.browserItems.size)
        assertEquals("old.txt", initial.browserItems[0].node.name)

        // Simulate a refresh where the directory now has a different child
        val newChild = file("/tmp/new.txt", "new.txt", size = 200)
        val updatedRootNode = directory(
            path = "/tmp",
            name = "tmp",
            children = listOf(newChild)
        )

        val updated = initial.withNodeUpdated("/tmp", updatedRootNode)

        assertEquals(1, updated.browserItems.size)
        assertEquals("new.txt", updated.browserItems[0].node.name)
        assertEquals(200, updated.totalSize)
    }

    @Test
    fun `withNodeUpdated updates nested node in tree`() {
        val grandchild1 = file("/tmp/parent/child/file1.txt", "file1.txt", size = 50)
        val grandchild2 = file("/tmp/parent/child/file2.txt", "file2.txt", size = 75)
        val child = directory(
            path = "/tmp/parent/child",
            name = "child",
            children = listOf(grandchild1, grandchild2)
        )
        val parent = directory(
            path = "/tmp/parent",
            name = "parent",
            children = listOf(child)
        )
        val root = directory(
            path = "/tmp",
            name = "tmp",
            children = listOf(parent)
        )

        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 125,
            fileCount = 2,
            directoryCount = 3,
            rootNode = root,
            scanDurationMs = 0,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp").withScanResult(scanResult)

        // Simulate refreshing the child directory with new content
        val newGrandchild = file("/tmp/parent/child/file3.txt", "file3.txt", size = 100)
        val updatedChild = directory(
            path = "/tmp/parent/child",
            name = "child",
            children = listOf(newGrandchild)
        )

        val updated = initial.withNodeUpdated("/tmp/parent/child", updatedChild)

        // Navigate to the updated child to verify the change
        val allPaths = collectAllPaths(updated.scanResult!!.rootNode)
        assertTrue(allPaths.contains("/tmp/parent/child/file3.txt"))
        assertFalse(allPaths.contains("/tmp/parent/child/file1.txt"))
        assertFalse(allPaths.contains("/tmp/parent/child/file2.txt"))
    }

    @Test
    fun `withNodeUpdated preserves browser item list structure`() {
        val child1 = directory("/tmp/child1", "child1", size = 100)
        val child2 = directory("/tmp/child2", "child2", size = 200)
        val root = directory(
            path = "/tmp",
            name = "tmp",
            children = listOf(child1, child2)
        )

        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 300,
            fileCount = 0,
            directoryCount = 3,
            rootNode = root,
            scanDurationMs = 0,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp")
            .withScanResult(scanResult)

        // Update root with refreshed data
        val newChild3 = directory("/tmp/child3", "child3", size = 150)
        val updatedRoot = directory(
            path = "/tmp",
            name = "tmp",
            children = listOf(child2, newChild3) // child1 is gone, child3 is new
        )

        val updated = initial.withNodeUpdated("/tmp", updatedRoot)

        // Browser items should reflect the new children
        assertEquals(2, updated.browserItems.size)
        assertEquals(listOf("child2", "child3"), updated.browserItems.map { it.node.name })
        assertEquals(350, updated.totalSize)
        assertEquals(350, updated.childDirectoryTotalSize)
    }

    // ── Tree view tests ──

    @Test
    fun `withToggleExpand expands a directory`() {
        val state = stateWithNestedTree()
        assertFalse(state.expandedPaths.contains("/root/dirA"))

        val expanded = state.withToggleExpand("/root/dirA")

        assertTrue(expanded.expandedPaths.contains("/root/dirA"))
        // Should now contain dirA's children in the list
        val names = expanded.browserItems.map { it.node.name }
        assertTrue(names.contains("sub1"))
        assertTrue(names.contains("sub2"))
    }

    @Test
    fun `withToggleExpand collapses an expanded directory`() {
        val state = stateWithNestedTree().withToggleExpand("/root/dirA")
        assertTrue(state.expandedPaths.contains("/root/dirA"))

        val collapsed = state.withToggleExpand("/root/dirA")

        assertFalse(collapsed.expandedPaths.contains("/root/dirA"))
        // dirA's children should be gone
        val names = collapsed.browserItems.map { it.node.name }
        assertFalse(names.contains("sub1"))
        assertFalse(names.contains("sub2"))
    }

    @Test
    fun `collapsing parent also collapses nested expanded children`() {
        var state = stateWithNestedTree()
        state = state.withToggleExpand("/root/dirA")
        state = state.withToggleExpand("/root/dirA/sub1")
        assertTrue(state.expandedPaths.contains("/root/dirA"))
        assertTrue(state.expandedPaths.contains("/root/dirA/sub1"))

        // Collapse parent
        val collapsed = state.withToggleExpand("/root/dirA")

        assertFalse(collapsed.expandedPaths.contains("/root/dirA"))
        assertFalse(collapsed.expandedPaths.contains("/root/dirA/sub1"))
    }

    @Test
    fun `tree prefix strings are correct for nested items`() {
        var state = stateWithNestedTree()
        state = state.withToggleExpand("/root/dirA")

        // Depth 0 items have no prefix
        val dirA = state.browserItems.first { it.node.name == "dirA" }
        assertEquals("", dirA.treePrefix)
        assertEquals(0, dirA.depth)

        // Depth 1 items have tree prefixes
        val sub1 = state.browserItems.first { it.node.name == "sub1" }
        assertEquals(1, sub1.depth)
        assertEquals("├── ", sub1.treePrefix)

        val sub2 = state.browserItems.first { it.node.name == "sub2" }
        assertEquals(1, sub2.depth)
        assertEquals("└── ", sub2.treePrefix)
    }

    @Test
    fun `tree prefix chaining for deeply nested items`() {
        // Verify "│   " vs "    " propagation
        assertEquals("├── ", buildTreePrefix(emptyList(), isLast = false))
        assertEquals("└── ", buildTreePrefix(emptyList(), isLast = true))
        assertEquals("├── ", buildTreePrefix(listOf(false), isLast = false))
        assertEquals("└── ", buildTreePrefix(listOf(false), isLast = true))
        assertEquals("│   ├── ", buildTreePrefix(listOf(false, false), isLast = false))
        assertEquals("│   └── ", buildTreePrefix(listOf(false, false), isLast = true))
        assertEquals("    ├── ", buildTreePrefix(listOf(true, false), isLast = false))
        assertEquals("    └── ", buildTreePrefix(listOf(true, true), isLast = true))
    }

    @Test
    fun `parentTotalSize is set correctly for nested items`() {
        var state = stateWithNestedTree()
        state = state.withToggleExpand("/root/dirA")

        // Depth 0: parentTotalSize = sum of root-level directory sizes
        val dirA = state.browserItems.first { it.node.name == "dirA" }
        // dirA=300 + dirB=200 = 500
        assertEquals(500, dirA.parentTotalSize)

        // Depth 1: parentTotalSize = sum of dirA's directory children sizes
        val sub1 = state.browserItems.first { it.node.name == "sub1" }
        // sub1=200 + sub2=100 = 300 (these are the dir children under dirA)
        assertEquals(300, sub1.parentTotalSize)
    }

    @Test
    fun `findParentIndex returns correct index`() {
        var state = stateWithNestedTree()
        state = state.withToggleExpand("/root/dirA")

        // items: dirA(0), sub1(1), sub2(2), dirB(3), file.txt(4)
        val names = state.browserItems.map { it.node.name }
        val dirAIdx = names.indexOf("dirA")
        val sub1Idx = names.indexOf("sub1")
        val sub2Idx = names.indexOf("sub2")
        val dirBIdx = names.indexOf("dirB")

        assertEquals(dirAIdx, state.findParentIndex(sub1Idx))
        assertEquals(dirAIdx, state.findParentIndex(sub2Idx))
        assertNull(state.findParentIndex(dirAIdx)) // depth 0 → null
        assertNull(state.findParentIndex(dirBIdx)) // depth 0 → null
    }

    @Test
    fun `sorting rebuilds tree per-level`() {
        var state = stateWithNestedTree()
        state = state.withToggleExpand("/root/dirA")

        // Default is SIZE_DESC — sub1(200) before sub2(100) under dirA
        val sizeOrder = state.browserItems.map { it.node.name }
        assertEquals("sub1", sizeOrder[1])
        assertEquals("sub2", sizeOrder[2])

        // Switch to NAME_ASC — sub1 still before sub2 alphabetically
        state = state.withNextSortOrder()
        assertEquals(SortOrder.NAME_ASC, state.sortOrder)
        val nameOrder = state.browserItems.map { it.node.name }
        // Name asc: dirA, (expanded children: sub1, sub2), dirB, file.txt
        assertEquals("dirA", nameOrder[0])
        assertEquals("sub1", nameOrder[1])
        assertEquals("sub2", nameOrder[2])
        assertEquals("dirB", nameOrder[3])
    }

    @Test
    fun `withItemDeleted cleans expandedPaths`() {
        var state = stateWithNestedTree()
        state = state.withToggleExpand("/root/dirA")
        state = state.withToggleExpand("/root/dirA/sub1")
        assertTrue(state.expandedPaths.contains("/root/dirA"))
        assertTrue(state.expandedPaths.contains("/root/dirA/sub1"))

        val updated = state.withItemDeleted("/root/dirA")

        assertFalse(updated.expandedPaths.contains("/root/dirA"))
        assertFalse(updated.expandedPaths.contains("/root/dirA/sub1"))
    }

    @Test
    fun `expandedPaths preserved across withScanResult`() {
        var state = stateWithNestedTree()
        state = state.withToggleExpand("/root/dirA")
        assertTrue(state.expandedPaths.contains("/root/dirA"))

        // Simulate a refresh with same data
        val refreshedResult = state.scanResult!!
        val updated = state.withScanResult(refreshedResult)

        assertTrue(updated.expandedPaths.contains("/root/dirA"))
        // dirA's children should still be visible
        val names = updated.browserItems.map { it.node.name }
        assertTrue(names.contains("sub1"))
    }

    @Test
    fun `depth 0 items have no tree prefix`() {
        val state = stateWithNestedTree()
        for (item in state.browserItems) {
            assertEquals(0, item.depth)
            assertEquals("", item.treePrefix)
        }
    }

    @Test
    fun `files have parentTotalSize of 0`() {
        val state = stateWithNestedTree()
        val fileItem = state.browserItems.first { it.kind == BrowserItemKind.FILE }
        assertEquals(0L, fileItem.parentTotalSize)
    }

    @Test
    fun `withToggleExpand preserves selection by path`() {
        var state = stateWithNestedTree()
        // Select dirB (index 1)
        state = state.withSelection(1)
        assertEquals("dirB", state.browserItems[state.selectedIndex].node.name)

        // Expand dirA — dirB should shift but selection should follow it
        state = state.withToggleExpand("/root/dirA")
        assertEquals("dirB", state.browserItems[state.selectedIndex].node.name)
    }

    // ── Progressive scanning tests ──

    @Test
    fun `withPartialScanResult sets correct flags and preserves expandedPaths`() {
        var state = stateWithNestedTree()
        state = state.withToggleExpand("/root/dirA")
        assertTrue(state.expandedPaths.contains("/root/dirA"))

        // Simulate partial scan result with some paths scanned
        val scanResult = state.scanResult!!
        val scannedPaths = setOf("/root/dirA")

        val updated = state.withPartialScanResult(scanResult, scannedPaths)

        assertFalse(updated.isLoading)
        assertTrue(updated.isScanInProgress)
        assertEquals(scannedPaths, updated.scannedPaths)
        assertTrue(updated.expandedPaths.contains("/root/dirA"))
        // dirA's children should still be visible
        val names = updated.browserItems.map { it.node.name }
        assertTrue(names.contains("sub1"))
    }

    @Test
    fun `isScanned is false for unscanned depth-0 dirs during scan`() {
        val scanResult = ScanResult(
            rootPath = "/root",
            totalSize = 300,
            fileCount = 0,
            directoryCount = 3,
            rootNode = directory(
                path = "/root",
                name = "root",
                children = listOf(
                    directory("/root/a", "a", size = 100),
                    directory("/root/b", "b", size = 100),
                    directory("/root/c", "c", size = 100)
                )
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        val state = ExplorerState(currentPath = "/root")
            .withPartialScanResult(scanResult, scannedPaths = setOf("/root/a"))

        val itemA = state.browserItems.first { it.node.name == "a" }
        val itemB = state.browserItems.first { it.node.name == "b" }
        val itemC = state.browserItems.first { it.node.name == "c" }

        assertTrue(itemA.isScanned)
        assertFalse(itemB.isScanned)
        assertFalse(itemC.isScanned)
    }

    @Test
    fun `isScanned is true for all items when not scanning`() {
        val state = stateWithNestedTree()

        for (item in state.browserItems) {
            assertTrue(item.isScanned, "Expected isScanned=true for ${item.node.name}")
        }
    }

    @Test
    fun `withScanResult clears isScanInProgress and scannedPaths`() {
        val scanResult = ScanResult(
            rootPath = "/root",
            totalSize = 100,
            fileCount = 0,
            directoryCount = 1,
            rootNode = directory("/root", "root", children = listOf(
                directory("/root/a", "a", size = 100)
            )),
            scanDurationMs = 0,
            errors = emptyList()
        )

        // First set up a partial scan state
        val partial = ExplorerState(currentPath = "/root")
            .withPartialScanResult(scanResult, scannedPaths = setOf("/root/a"))
        assertTrue(partial.isScanInProgress)
        assertTrue(partial.scannedPaths.isNotEmpty())

        // Now complete the scan
        val completed = partial.withScanResult(scanResult)
        assertFalse(completed.isScanInProgress)
        assertTrue(completed.scannedPaths.isEmpty())
    }

    @Test
    fun `withPartialScanResult preserves selection by path`() {
        val scanResult = ScanResult(
            rootPath = "/root",
            totalSize = 300,
            fileCount = 0,
            directoryCount = 3,
            rootNode = directory(
                path = "/root",
                name = "root",
                children = listOf(
                    directory("/root/a", "a", size = 100),
                    directory("/root/b", "b", size = 100),
                    directory("/root/c", "c", size = 100)
                )
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        // Start with partial result, select "b"
        var state = ExplorerState(currentPath = "/root")
            .withPartialScanResult(scanResult, scannedPaths = setOf("/root/a"))
        val bIndex = state.browserItems.indexOfFirst { it.node.name == "b" }
        state = state.withSelection(bIndex)
        assertEquals("b", state.browserItems[state.selectedIndex].node.name)

        // Second partial result — selection should stay on "b"
        val updated = state.withPartialScanResult(scanResult, scannedPaths = setOf("/root/a", "/root/b"))
        assertEquals("b", updated.browserItems[updated.selectedIndex].node.name)
    }

    @Test
    fun `progressive partial results update sizes correctly`() {
        val dirA = directory("/root/a", "a", size = 10)  // placeholder, just metadata
        val dirB = directory("/root/b", "b", size = 10)
        val fileTxt = file("/root/file.txt", "file.txt", size = 50)
        val root = directory("/root", "root", children = listOf(dirA, dirB, fileTxt))

        val initialResult = ScanResult(
            rootPath = "/root",
            totalSize = root.totalSize(),
            fileCount = root.fileCount(),
            directoryCount = root.directoryCount(),
            rootNode = root,
            scanDurationMs = 0,
            errors = emptyList()
        )

        // Initial partial: only files scanned, dirs are placeholders
        var state = ExplorerState(currentPath = "/root")
            .withPartialScanResult(initialResult, scannedPaths = emptySet())
        assertEquals(70, state.totalSize) // 10 + 10 + 50

        // After scanning dirA (now has real children with 200 bytes total)
        val scannedDirA = directory("/root/a", "a", size = 10, children = listOf(
            file("/root/a/big.bin", "big.bin", size = 200)
        ))
        val root2 = directory("/root", "root", children = listOf(scannedDirA, dirB, fileTxt))
        val result2 = ScanResult(
            rootPath = "/root",
            totalSize = root2.totalSize(),
            fileCount = root2.fileCount(),
            directoryCount = root2.directoryCount(),
            rootNode = root2,
            scanDurationMs = 0,
            errors = emptyList()
        )

        state = state.withPartialScanResult(result2, scannedPaths = setOf("/root/a"))
        assertEquals(270, state.totalSize) // 210 + 10 + 50

        // Verify dirA now shows scanned, dirB still unscanned
        val itemA = state.browserItems.first { it.node.name == "a" }
        val itemB = state.browserItems.first { it.node.name == "b" }
        assertTrue(itemA.isScanned)
        assertFalse(itemB.isScanned)
    }

    @Test
    fun `isScanned is true for depth greater than 0 dirs during scan`() {
        // Expand dirA which is scanned — its children should all be isScanned=true
        val scanResult = ScanResult(
            rootPath = "/root",
            totalSize = 550,
            fileCount = 1,
            directoryCount = 4,
            rootNode = directory(
                path = "/root",
                name = "root",
                children = listOf(
                    directory("/root/dirA", "dirA", children = listOf(
                        directory("/root/dirA/sub1", "sub1", size = 200),
                        directory("/root/dirA/sub2", "sub2", size = 100)
                    )),
                    directory("/root/dirB", "dirB", size = 200)
                )
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        var state = ExplorerState(currentPath = "/root")
            .withPartialScanResult(scanResult, scannedPaths = setOf("/root/dirA"))
        state = state.withToggleExpand("/root/dirA")

        // Depth-1 children of scanned dirA should be isScanned=true
        val sub1 = state.browserItems.first { it.node.name == "sub1" }
        val sub2 = state.browserItems.first { it.node.name == "sub2" }
        assertTrue(sub1.isScanned)
        assertTrue(sub2.isScanned)

        // But dirB at depth 0 is unscanned
        val dirB = state.browserItems.first { it.node.name == "dirB" }
        assertFalse(dirB.isScanned)
    }

    /**
     * Helper: creates a state with this structure:
     * /root
     *   dirA/ (300 bytes total)
     *     sub1/ (200 bytes)
     *       deep.txt (200 bytes)
     *     sub2/ (100 bytes)
     *   dirB/ (200 bytes)
     *   file.txt (50 bytes)
     */
    private fun stateWithNestedTree(): ExplorerState {
        val deep = file("/root/dirA/sub1/deep.txt", "deep.txt", size = 200)
        val sub1 = directory("/root/dirA/sub1", "sub1", children = listOf(deep))
        val sub2 = directory("/root/dirA/sub2", "sub2", size = 100)
        val dirA = directory("/root/dirA", "dirA", children = listOf(sub1, sub2))
        val dirB = directory("/root/dirB", "dirB", size = 200)
        val fileTxt = file("/root/file.txt", "file.txt", size = 50)
        val root = directory("/root", "root", children = listOf(dirA, dirB, fileTxt))

        val scanResult = ScanResult(
            rootPath = "/root",
            totalSize = root.totalSize(),
            fileCount = root.fileCount(),
            directoryCount = root.directoryCount(),
            rootNode = root,
            scanDurationMs = 0,
            errors = emptyList()
        )
        return ExplorerState(currentPath = "/root").withScanResult(scanResult)
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
        return createFileNode(
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
        return createFileNode(
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
