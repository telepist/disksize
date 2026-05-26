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

/**
 * Tests that path-based operations work correctly with both forward-slash (Unix)
 * and backslash (Windows) path separators.  The bug these tests guard against:
 * all `startsWith("$path/")` checks silently failed on Windows because the
 * actual paths used `\` as the separator.
 */
class CrossPlatformPathTest {

    // ── isSubPathOf unit tests ──────────────────────────────────────────

    @Test
    fun `isSubPathOf matches forward-slash child`() {
        assertTrue("/root/dir/sub".isSubPathOf("/root/dir"))
    }

    @Test
    fun `isSubPathOf matches backslash child`() {
        assertTrue("C:\\Users\\foo\\bar".isSubPathOf("C:\\Users\\foo"))
    }

    @Test
    fun `isSubPathOf rejects exact match`() {
        assertFalse("/root/dir".isSubPathOf("/root/dir"))
    }

    @Test
    fun `isSubPathOf rejects exact match with backslash`() {
        assertFalse("C:\\Users\\foo".isSubPathOf("C:\\Users\\foo"))
    }

    @Test
    fun `isSubPathOf rejects partial directory name match with forward slash`() {
        // "/root/dirABC" should NOT match parent "/root/dir"
        assertFalse("/root/dirABC".isSubPathOf("/root/dir"))
    }

    @Test
    fun `isSubPathOf rejects partial directory name match with backslash`() {
        assertFalse("C:\\Users\\foobar".isSubPathOf("C:\\Users\\foo"))
    }

    @Test
    fun `isSubPathOf matches deeply nested forward-slash path`() {
        assertTrue("/a/b/c/d/e/f".isSubPathOf("/a/b"))
    }

    @Test
    fun `isSubPathOf matches deeply nested backslash path`() {
        assertTrue("D:\\a\\b\\c\\d\\e\\f".isSubPathOf("D:\\a\\b"))
    }

    @Test
    fun `isSubPathOf rejects unrelated paths`() {
        assertFalse("/other/path".isSubPathOf("/root/dir"))
        assertFalse("C:\\Other\\Path".isSubPathOf("C:\\Root\\Dir"))
    }

    @Test
    fun `isSubPathOf handles single-character child name`() {
        assertTrue("/root/x/y".isSubPathOf("/root/x"))
        assertTrue("C:\\root\\x\\y".isSubPathOf("C:\\root\\x"))
    }

    @Test
    fun `isSubPathOf handles drive root as parent`() {
        assertTrue("C:\\Users".isSubPathOf("C:"))
        assertTrue("D:\\folder\\sub".isSubPathOf("D:"))
    }

    @Test
    fun `isSubPathOf handles unix root as parent`() {
        assertTrue("/usr/bin".isSubPathOf("/usr"))
    }

    @Test
    fun `isSubPathOf tolerates trailing separator on parent`() {
        assertTrue("C:\\Pelit".isSubPathOf("C:\\"))
        assertTrue("C:\\Users\\foo".isSubPathOf("C:\\"))
        assertTrue("/usr".isSubPathOf("/"))
        assertTrue("/etc/hosts".isSubPathOf("/"))
    }

    @Test
    fun `isSubPathOf rejects filesystem root as its own child`() {
        assertFalse("C:\\".isSubPathOf("C:\\"))
        assertFalse("/".isSubPathOf("/"))
    }

    @Test
    fun `isSubPathOf handles trailing slash on regular directory parent`() {
        assertTrue("/root/dir/sub".isSubPathOf("/root/dir/"))
        assertTrue("C:\\Users\\foo\\bar".isSubPathOf("C:\\Users\\foo\\"))
    }

    // ── Scanning detection with Windows paths ───────────────────────────

    @Test
    fun `isScanning matches when loadingDirectoryPath is backslash child of directory`() {
        val dirA = winDirectory("C:\\root\\dirA", "dirA", size = 100)
        val dirB = winDirectory("C:\\root\\dirB", "dirB", size = 200)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA, dirB))
        val scanResult = scanResult("C:\\root", root)

        val state = ExplorerState(
            currentPath = "C:\\root",
            loadingDirectoryPath = "C:\\root\\dirB\\sub\\deep"
        ).withPartialScanResult(scanResult, scannedPaths = setOf("C:\\root\\dirA"))

        val itemA = state.browserItems.first { it.node.path == "C:\\root\\dirA" }
        val itemB = state.browserItems.first { it.node.path == "C:\\root\\dirB" }
        assertFalse(itemA.isScanning, "Scanned dirA should not be scanning")
        assertTrue(itemB.isScanning, "Unscanned dirB matching loadingDirectoryPath should be scanning")
    }

    @Test
    fun `isScanning matches when loadingDirectoryPath exactly equals directory with backslash`() {
        val dirA = winDirectory("C:\\root\\dirA", "dirA", size = 100)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA))
        val scanResult = scanResult("C:\\root", root)

        val state = ExplorerState(
            currentPath = "C:\\root",
            loadingDirectoryPath = "C:\\root\\dirA"
        ).withPartialScanResult(scanResult, scannedPaths = emptySet())

        val item = state.browserItems.first { it.node.path == "C:\\root\\dirA" }
        assertTrue(item.isScanning)
    }

    @Test
    fun `isScanning does not match unrelated directory with backslash paths`() {
        val dirA = winDirectory("C:\\root\\dirA", "dirA", size = 100)
        val dirB = winDirectory("C:\\root\\dirB", "dirB", size = 200)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA, dirB))
        val scanResult = scanResult("C:\\root", root)

        val state = ExplorerState(
            currentPath = "C:\\root",
            loadingDirectoryPath = "C:\\root\\dirA\\sub"
        ).withPartialScanResult(scanResult, scannedPaths = emptySet())

        val itemA = state.browserItems.first { it.node.path == "C:\\root\\dirA" }
        val itemB = state.browserItems.first { it.node.path == "C:\\root\\dirB" }
        assertTrue(itemA.isScanning, "dirA should be scanning (loadingDir is its child)")
        assertFalse(itemB.isScanning, "dirB should not be scanning")
    }

    @Test
    fun `isScanning avoids false positive for similar-prefix backslash paths`() {
        // "C:\root\dir" should not match "C:\root\dirExtra\sub"
        val dir = winDirectory("C:\\root\\dir", "dir", size = 100)
        val dirExtra = winDirectory("C:\\root\\dirExtra", "dirExtra", size = 200)
        val root = winDirectory("C:\\root", "root", children = listOf(dir, dirExtra))
        val scanResult = scanResult("C:\\root", root)

        val state = ExplorerState(
            currentPath = "C:\\root",
            loadingDirectoryPath = "C:\\root\\dirExtra\\sub"
        ).withPartialScanResult(scanResult, scannedPaths = emptySet())

        val itemDir = state.browserItems.first { it.node.path == "C:\\root\\dir" }
        val itemExtra = state.browserItems.first { it.node.path == "C:\\root\\dirExtra" }
        assertFalse(itemDir.isScanning, "dir should NOT match dirExtra's child")
        assertTrue(itemExtra.isScanning)
    }

    @Test
    fun `isScanning is false for already-scanned dir even with matching backslash loadingPath`() {
        val dirA = winDirectory("C:\\root\\dirA", "dirA", size = 100)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA))
        val scanResult = scanResult("C:\\root", root)

        val state = ExplorerState(
            currentPath = "C:\\root",
            loadingDirectoryPath = "C:\\root\\dirA\\sub"
        ).withPartialScanResult(scanResult, scannedPaths = setOf("C:\\root\\dirA"))

        val item = state.browserItems.first { it.node.path == "C:\\root\\dirA" }
        assertFalse(item.isScanning, "Already-scanned dir should not be marked as scanning")
    }

    @Test
    fun `isScanning is false for files with backslash paths`() {
        val fileA = winFile("C:\\root\\file.txt", "file.txt", size = 100)
        val root = winDirectory("C:\\root", "root", children = listOf(fileA))
        val scanResult = scanResult("C:\\root", root)

        val state = ExplorerState(
            currentPath = "C:\\root",
            loadingDirectoryPath = "C:\\root\\file.txt\\something"
        ).withPartialScanResult(scanResult, scannedPaths = emptySet())

        val item = state.browserItems.first { it.node.path == "C:\\root\\file.txt" }
        assertFalse(item.isScanning, "Files should never be scanning")
    }

    // ── Scanning with forward-slash paths (regression) ──────────────────

    @Test
    fun `isScanning still works with forward-slash paths`() {
        val dirA = directory("/root/dirA", "dirA", size = 100)
        val dirB = directory("/root/dirB", "dirB", size = 200)
        val root = directory("/root", "root", children = listOf(dirA, dirB))
        val scanResult = scanResult("/root", root)

        val state = ExplorerState(
            currentPath = "/root",
            loadingDirectoryPath = "/root/dirB/sub/deep"
        ).withPartialScanResult(scanResult, scannedPaths = setOf("/root/dirA"))

        val itemA = state.browserItems.first { it.node.path == "/root/dirA" }
        val itemB = state.browserItems.first { it.node.path == "/root/dirB" }
        assertFalse(itemA.isScanning)
        assertTrue(itemB.isScanning)
    }

    // ── Live bytes / parentTotalSize with Windows paths ─────────────────

    @Test
    fun `scanning dir live bytes affect parentTotalSize with backslash paths`() {
        val dirA = winDirectory("C:\\root\\dirA", "dirA", size = 100)
        val dirB = winDirectory("C:\\root\\dirB", "dirB", size = 200)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA, dirB))

        // Simulate: scanning inside dirB, live bytes accumulated to 500
        val tree = disksize.domain.model.FileTreeState(
            rootPath = "C:\\root",
            rootNode = root,
            scanPhase = disksize.domain.model.ScanPhase.SCANNING,
            scannedPaths = setOf("C:\\root\\dirA"),
            scanProgress = disksize.domain.model.ScanProgress(
                processedFiles = 10,
                processedDirectories = 5,
                scannedBytes = 500,
                bytesPerSecond = 100,
                currentDirectory = "C:\\root\\dirB\\deep\\sub"
            ),
            lastPartialScannedBytes = 0
        )
        val state = deriveExplorerState(tree, UiSelections())

        // dirB should use live bytes (500) instead of stale tree size (200)
        val itemB = state.browserItems.first { it.node.path == "C:\\root\\dirB" }
        assertTrue(itemB.isScanning)
        assertEquals(500, itemB.totalSize, "Scanning dir should use live bytes")

        // parentTotalSize for all depth-0 dirs should include live bytes
        val itemA = state.browserItems.first { it.node.path == "C:\\root\\dirA" }
        assertEquals(600, itemA.parentTotalSize, "parentTotalSize should include live bytes for scanning dir")
    }

    @Test
    fun `sorting reflects live bytes for scanning dir with backslash paths`() {
        // dirA has 300 bytes, dirB has 100 bytes (but scanning shows 500)
        val dirA = winDirectory("C:\\root\\dirA", "dirA", size = 300)
        val dirB = winDirectory("C:\\root\\dirB", "dirB", size = 100)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA, dirB))

        val tree = disksize.domain.model.FileTreeState(
            rootPath = "C:\\root",
            rootNode = root,
            scanPhase = disksize.domain.model.ScanPhase.SCANNING,
            scannedPaths = setOf("C:\\root\\dirA"),
            scanProgress = disksize.domain.model.ScanProgress(
                processedFiles = 10,
                processedDirectories = 5,
                scannedBytes = 500,
                bytesPerSecond = 100,
                currentDirectory = "C:\\root\\dirB\\sub"
            ),
            lastPartialScannedBytes = 0
        )
        val state = deriveExplorerState(tree, UiSelections())

        // With SIZE_DESC sort, dirB (500 live bytes) should come before dirA (300)
        val names = state.browserItems.filter { it.kind == BrowserItemKind.DIRECTORY }.map { it.node.name }
        assertEquals("dirB", names[0], "Scanning dir with higher live bytes should sort first")
        assertEquals("dirA", names[1])
    }

    // ── Expand / collapse with Windows paths ────────────────────────────

    @Test
    fun `withToggleExpand expands directory with backslash paths`() {
        val state = stateWithWindowsNestedTree()
        assertFalse(state.expandedPaths.contains("C:\\root\\dirA"))

        val expanded = state.withToggleExpand("C:\\root\\dirA")

        assertTrue(expanded.expandedPaths.contains("C:\\root\\dirA"))
        val names = expanded.browserItems.map { it.node.name }
        assertTrue(names.contains("sub1"))
        assertTrue(names.contains("sub2"))
    }

    @Test
    fun `collapsing parent removes nested expanded children with backslash paths`() {
        var state = stateWithWindowsNestedTree()
        state = state.withToggleExpand("C:\\root\\dirA")
        state = state.withToggleExpand("C:\\root\\dirA\\sub1")
        assertTrue(state.expandedPaths.contains("C:\\root\\dirA"))
        assertTrue(state.expandedPaths.contains("C:\\root\\dirA\\sub1"))

        val collapsed = state.withToggleExpand("C:\\root\\dirA")

        assertFalse(collapsed.expandedPaths.contains("C:\\root\\dirA"))
        assertFalse(collapsed.expandedPaths.contains("C:\\root\\dirA\\sub1"))
    }

    @Test
    fun `collapsing parent does not affect sibling paths with similar prefix`() {
        // Ensure collapsing "C:\root\dir" doesn't affect "C:\root\dirExtra"
        val sub = winDirectory("C:\\root\\dir\\sub", "sub", size = 50)
        val dir = winDirectory("C:\\root\\dir", "dir", children = listOf(sub))
        val extraSub = winDirectory("C:\\root\\dirExtra\\sub", "sub", size = 75)
        val dirExtra = winDirectory("C:\\root\\dirExtra", "dirExtra", children = listOf(extraSub))
        val root = winDirectory("C:\\root", "root", children = listOf(dir, dirExtra))
        val result = scanResult("C:\\root", root)

        var state = ExplorerState(currentPath = "C:\\root").withScanResult(result)
        state = state.withToggleExpand("C:\\root\\dir")
        state = state.withToggleExpand("C:\\root\\dirExtra")
        assertTrue(state.expandedPaths.contains("C:\\root\\dir"))
        assertTrue(state.expandedPaths.contains("C:\\root\\dirExtra"))

        // Collapse only "dir"
        val collapsed = state.withToggleExpand("C:\\root\\dir")
        assertFalse(collapsed.expandedPaths.contains("C:\\root\\dir"))
        assertTrue(collapsed.expandedPaths.contains("C:\\root\\dirExtra"),
            "Sibling with similar prefix should NOT be collapsed")
    }

    // ── Delete with Windows paths ───────────────────────────────────────

    @Test
    fun `withItemDeleted cleans expandedPaths with backslash paths`() {
        var state = stateWithWindowsNestedTree()
        state = state.withToggleExpand("C:\\root\\dirA")
        state = state.withToggleExpand("C:\\root\\dirA\\sub1")
        assertTrue(state.expandedPaths.contains("C:\\root\\dirA"))
        assertTrue(state.expandedPaths.contains("C:\\root\\dirA\\sub1"))

        val updated = state.withItemDeleted("C:\\root\\dirA")

        assertFalse(updated.expandedPaths.contains("C:\\root\\dirA"))
        assertFalse(updated.expandedPaths.contains("C:\\root\\dirA\\sub1"))
    }

    @Test
    fun `withItemDeleted does not affect sibling with similar prefix on Windows`() {
        val sub = winDirectory("C:\\root\\dir\\sub", "sub", size = 50)
        val dir = winDirectory("C:\\root\\dir", "dir", children = listOf(sub))
        val extraSub = winDirectory("C:\\root\\dirExtra\\sub", "sub", size = 75)
        val dirExtra = winDirectory("C:\\root\\dirExtra", "dirExtra", children = listOf(extraSub))
        val root = winDirectory("C:\\root", "root", children = listOf(dir, dirExtra))
        val result = scanResult("C:\\root", root)

        var state = ExplorerState(currentPath = "C:\\root").withScanResult(result)
        state = state.withToggleExpand("C:\\root\\dir")
        state = state.withToggleExpand("C:\\root\\dirExtra")

        val updated = state.withItemDeleted("C:\\root\\dir")

        assertFalse(updated.expandedPaths.contains("C:\\root\\dir"))
        assertTrue(updated.expandedPaths.contains("C:\\root\\dirExtra"),
            "Deleting dir should not affect dirExtra's expanded state")
    }

    @Test
    fun `withItemDeleted removes nested file with backslash paths`() {
        val deep = winFile("C:\\root\\dirA\\sub1\\deep.txt", "deep.txt", size = 200)
        val sub1 = winDirectory("C:\\root\\dirA\\sub1", "sub1", children = listOf(deep))
        val dirA = winDirectory("C:\\root\\dirA", "dirA", children = listOf(sub1))
        val root = winDirectory("C:\\root", "root", children = listOf(dirA))
        val result = scanResult("C:\\root", root)

        val state = ExplorerState(currentPath = "C:\\root").withScanResult(result)
        val updated = state.withItemDeleted("C:\\root\\dirA\\sub1\\deep.txt")

        val allPaths = collectAllPaths(updated.scanResult!!.rootNode)
        assertFalse(allPaths.contains("C:\\root\\dirA\\sub1\\deep.txt"))
        assertTrue(allPaths.contains("C:\\root\\dirA\\sub1"))
        assertTrue(allPaths.contains("C:\\root\\dirA"))
    }

    // ── Node update with Windows paths ──────────────────────────────────

    @Test
    fun `withNodeUpdated finds nested node with backslash paths`() {
        val file1 = winFile("C:\\root\\parent\\child\\file1.txt", "file1.txt", size = 50)
        val child = winDirectory("C:\\root\\parent\\child", "child", children = listOf(file1))
        val parent = winDirectory("C:\\root\\parent", "parent", children = listOf(child))
        val root = winDirectory("C:\\root", "root", children = listOf(parent))
        val result = scanResult("C:\\root", root)

        val state = ExplorerState(currentPath = "C:\\root").withScanResult(result)

        val newFile = winFile("C:\\root\\parent\\child\\file2.txt", "file2.txt", size = 100)
        val updatedChild = winDirectory("C:\\root\\parent\\child", "child", children = listOf(newFile))
        val updated = state.withNodeUpdated("C:\\root\\parent\\child", updatedChild)

        val allPaths = collectAllPaths(updated.scanResult!!.rootNode)
        assertTrue(allPaths.contains("C:\\root\\parent\\child\\file2.txt"))
        assertFalse(allPaths.contains("C:\\root\\parent\\child\\file1.txt"))
    }

    // ── Progress / loadingDirectoryPath with Windows paths ──────────────

    @Test
    fun `withProgress stores backslash currentDirectory`() {
        var state = ExplorerState(currentPath = "C:\\root", isLoading = true)
        state = state.withProgress(
            ScanProgress(
                processedFiles = 1,
                processedDirectories = 0,
                scannedBytes = 1024,
                bytesPerSecond = 512,
                currentDirectory = "C:\\root\\dirA\\sub"
            )
        )
        assertEquals("C:\\root\\dirA\\sub", state.loadingDirectoryPath)
    }

    @Test
    fun `withProgress retains backslash directory when next progress has null`() {
        var state = ExplorerState(currentPath = "C:\\root", isLoading = true)
        state = state.withProgress(
            ScanProgress(
                processedFiles = 1,
                processedDirectories = 0,
                scannedBytes = 1024,
                bytesPerSecond = 512,
                currentDirectory = "C:\\root\\dirA"
            )
        )
        assertEquals("C:\\root\\dirA", state.loadingDirectoryPath)

        state = state.withProgress(
            ScanProgress(
                processedFiles = 2,
                processedDirectories = 1,
                scannedBytes = 2048,
                bytesPerSecond = 512,
                currentDirectory = null
            )
        )
        assertEquals("C:\\root\\dirA", state.loadingDirectoryPath)
    }

    @Test
    fun `withProgress triggers re-sort with backslash scanning dir`() {
        val dirA = winDirectory("C:\\root\\dirA", "dirA", size = 300)
        val dirB = winDirectory("C:\\root\\dirB", "dirB", size = 100)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA, dirB))
        val result = scanResult("C:\\root", root)

        // Set up state with dirA scanned, dirB being scanned
        var state = ExplorerState(currentPath = "C:\\root")
            .withPartialScanResult(result, scannedPaths = setOf("C:\\root\\dirA"))

        // Initially SIZE_DESC: dirA(300) before dirB(100)
        assertEquals("dirA", state.browserItems[0].node.name)

        // Progress update with high scannedBytes for dirB
        state = state.copy(loadingDirectoryPath = "C:\\root\\dirB\\sub")
        state = state.withProgress(
            ScanProgress(
                processedFiles = 50,
                processedDirectories = 10,
                scannedBytes = 500,
                bytesPerSecond = 100,
                currentDirectory = "C:\\root\\dirB\\sub\\deep"
            )
        )

        // dirB (500 live bytes) should now sort before dirA (300)
        assertEquals("dirB", state.browserItems[0].node.name,
            "Re-sort should use live bytes for scanning dir with backslash paths")
    }

    // ── findParentIndex with Windows paths ──────────────────────────────

    @Test
    fun `findParentIndex works with backslash paths`() {
        var state = stateWithWindowsNestedTree()
        state = state.withToggleExpand("C:\\root\\dirA")

        val names = state.browserItems.map { it.node.name }
        val dirAIdx = names.indexOf("dirA")
        val sub1Idx = names.indexOf("sub1")
        val sub2Idx = names.indexOf("sub2")
        val dirBIdx = names.indexOf("dirB")

        assertEquals(dirAIdx, state.findParentIndex(sub1Idx))
        assertEquals(dirAIdx, state.findParentIndex(sub2Idx))
        assertNull(state.findParentIndex(dirAIdx))
        assertNull(state.findParentIndex(dirBIdx))
    }

    // ── Selection preservation with Windows paths ───────────────────────

    @Test
    fun `withToggleExpand preserves selection by path with backslash paths`() {
        var state = stateWithWindowsNestedTree()
        state = state.withSelection(1) // dirB
        assertEquals("dirB", state.browserItems[state.selectedIndex].node.name)

        state = state.withToggleExpand("C:\\root\\dirA")
        assertEquals("dirB", state.browserItems[state.selectedIndex].node.name)
    }

    @Test
    fun `withPartialScanResult preserves selection with backslash paths`() {
        val dirA = winDirectory("C:\\root\\dirA", "dirA", size = 100)
        val dirB = winDirectory("C:\\root\\dirB", "dirB", size = 100)
        val dirC = winDirectory("C:\\root\\dirC", "dirC", size = 100)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA, dirB, dirC))
        val result = scanResult("C:\\root", root)

        var state = ExplorerState(currentPath = "C:\\root")
            .withPartialScanResult(result, scannedPaths = setOf("C:\\root\\dirA"))
        val bIndex = state.browserItems.indexOfFirst { it.node.name == "dirB" }
        state = state.withSelection(bIndex)
        assertEquals("dirB", state.browserItems[state.selectedIndex].node.name)

        val updated = state.withPartialScanResult(result, scannedPaths = setOf("C:\\root\\dirA", "C:\\root\\dirB"))
        assertEquals("dirB", updated.browserItems[updated.selectedIndex].node.name)
    }

    // ── Depth-0 scanning indicator with expanded tree on Windows ────────

    @Test
    fun `isScanning propagates through expanded tree with backslash paths`() {
        val deep = winFile("C:\\root\\dirA\\sub1\\deep.txt", "deep.txt", size = 200)
        val sub1 = winDirectory("C:\\root\\dirA\\sub1", "sub1", children = listOf(deep))
        val sub2 = winDirectory("C:\\root\\dirA\\sub2", "sub2", size = 100)
        val dirA = winDirectory("C:\\root\\dirA", "dirA", children = listOf(sub1, sub2))
        val dirB = winDirectory("C:\\root\\dirB", "dirB", size = 200)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA, dirB))
        val result = scanResult("C:\\root", root)

        // withPartialScanResult uses loadingDirectoryPath to build items, then resets it.
        // Simulate a progress update restoring the path, then expand.
        var state = ExplorerState(
            currentPath = "C:\\root",
            loadingDirectoryPath = "C:\\root\\dirA\\sub2\\inner"
        ).withPartialScanResult(result, scannedPaths = emptySet())
        state = state.withProgress(ScanProgress(
            processedFiles = 5, processedDirectories = 2,
            scannedBytes = 100, bytesPerSecond = 50,
            currentDirectory = "C:\\root\\dirA\\sub2\\inner"
        ))
        state = state.withToggleExpand("C:\\root\\dirA")

        // dirA at depth 0 should be scanning
        val dirAItem = state.browserItems.first { it.node.name == "dirA" }
        assertTrue(dirAItem.isScanning, "dirA (ancestor of loading path) should be scanning")

        // dirB should not be scanning
        val dirBItem = state.browserItems.first { it.node.name == "dirB" }
        assertFalse(dirBItem.isScanning, "dirB should not be scanning")
    }

    // ── Mixed scenarios ─────────────────────────────────────────────────

    @Test
    fun `isScanning handles UNC paths`() {
        val dir = winDirectory("\\\\server\\share\\folder", "folder", size = 100)
        val root = winDirectory("\\\\server\\share", "share", children = listOf(dir))
        val result = scanResult("\\\\server\\share", root)

        val state = ExplorerState(
            currentPath = "\\\\server\\share",
            loadingDirectoryPath = "\\\\server\\share\\folder\\sub"
        ).withPartialScanResult(result, scannedPaths = emptySet())

        val item = state.browserItems.first { it.node.path == "\\\\server\\share\\folder" }
        assertTrue(item.isScanning, "UNC path should be detected as scanning")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Windows-path nested tree:
     * C:\root
     *   dirA\ (300 bytes total)
     *     sub1\ (200 bytes)
     *       deep.txt (200 bytes)
     *     sub2\ (100 bytes)
     *   dirB\ (200 bytes)
     *   file.txt (50 bytes)
     */
    private fun stateWithWindowsNestedTree(): ExplorerState {
        val deep = winFile("C:\\root\\dirA\\sub1\\deep.txt", "deep.txt", size = 200)
        val sub1 = winDirectory("C:\\root\\dirA\\sub1", "sub1", children = listOf(deep))
        val sub2 = winDirectory("C:\\root\\dirA\\sub2", "sub2", size = 100)
        val dirA = winDirectory("C:\\root\\dirA", "dirA", children = listOf(sub1, sub2))
        val dirB = winDirectory("C:\\root\\dirB", "dirB", size = 200)
        val fileTxt = winFile("C:\\root\\file.txt", "file.txt", size = 50)
        val root = winDirectory("C:\\root", "root", children = listOf(dirA, dirB, fileTxt))

        val result = scanResult("C:\\root", root)
        return ExplorerState(currentPath = "C:\\root").withScanResult(result)
    }

    private fun scanResult(rootPath: String, root: FileNode) = ScanResult(
        rootPath = rootPath,
        totalSize = root.totalSize(),
        fileCount = root.fileCount(),
        directoryCount = root.directoryCount(),
        rootNode = root,
        scanDurationMs = 0,
        errors = emptyList()
    )

    private fun collectAllPaths(node: FileNode): Set<String> {
        val paths = mutableSetOf(node.path)
        for (child in node.children) {
            paths.addAll(collectAllPaths(child))
        }
        return paths
    }

    private fun winDirectory(
        path: String,
        name: String,
        size: Long = 0,
        children: List<FileNode> = emptyList(),
        lastModified: Long = 0L
    ): FileNode = createFileNode(
        path = path, name = name, size = size,
        isDirectory = true, isSymlink = false,
        children = children, lastModified = lastModified
    )

    private fun winFile(
        path: String,
        name: String,
        size: Long,
        lastModified: Long = 0L
    ): FileNode = createFileNode(
        path = path, name = name, size = size,
        isDirectory = false, isSymlink = false,
        children = emptyList(), lastModified = lastModified
    )

    private fun directory(
        path: String,
        name: String,
        size: Long = 0,
        children: List<FileNode> = emptyList(),
        lastModified: Long = 0L
    ): FileNode = createFileNode(
        path = path, name = name, size = size,
        isDirectory = true, isSymlink = false,
        children = children, lastModified = lastModified
    )
}
