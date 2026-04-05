package disksize.presentation

import disksize.domain.model.FileNode
import disksize.domain.model.FileTreeState
import disksize.domain.model.ScanError
import disksize.domain.model.ScanPhase
import disksize.domain.model.ScanProgress
import disksize.domain.model.ErrorType
import disksize.domain.model.createFileNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeriveExplorerStateTest {

    @Test
    fun `idle state with no tree`() {
        val tree = FileTreeState(rootPath = "/test")
        val state = deriveExplorerState(tree, UiSelections())

        assertEquals("/test", state.currentPath)
        assertNull(state.scanResult)
        assertFalse(state.isLoading)
        assertFalse(state.isScanInProgress)
        assertTrue(state.browserItems.isEmpty())
    }

    @Test
    fun `loading phase`() {
        val tree = FileTreeState(rootPath = "/test", scanPhase = ScanPhase.LOADING)
        val state = deriveExplorerState(tree, UiSelections())

        assertTrue(state.isLoading)
        assertTrue(state.isScanInProgress)
        assertTrue(state.browserItems.isEmpty())
    }

    @Test
    fun `scanning phase with partial tree`() {
        val root = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 200),
            directory("/test/b", "b", size = 100)
        ))
        val tree = FileTreeState(
            rootPath = "/test",
            rootNode = root,
            scanPhase = ScanPhase.SCANNING,
            scannedPaths = setOf("/test/a")
        )
        val state = deriveExplorerState(tree, UiSelections())

        assertFalse(state.isLoading)
        assertTrue(state.isScanInProgress)
        assertEquals(2, state.browserItems.size)
        // SIZE_DESC: a(200) before b(100)
        assertEquals("a", state.browserItems[0].node.name)
        assertEquals("b", state.browserItems[1].node.name)
        // Scanned status
        assertTrue(state.browserItems[0].isScanned)
        assertFalse(state.browserItems[1].isScanned)
    }

    @Test
    fun `completed phase with scan result`() {
        val root = directory("/test", "test", children = listOf(
            file("/test/big.bin", "big.bin", size = 500),
            directory("/test/dir", "dir", size = 100)
        ))
        val tree = FileTreeState(
            rootPath = "/test",
            rootNode = root,
            scanPhase = ScanPhase.COMPLETED,
            scanDurationMs = 42
        )
        val state = deriveExplorerState(tree, UiSelections())

        assertFalse(state.isLoading)
        assertFalse(state.isScanInProgress)
        assertNotNull(state.scanResult)
        assertEquals(600, state.totalSize)
        assertEquals(1, state.fileCount)
        assertEquals(1, state.directoryCount)
        assertEquals(42, state.scanDurationMs)
    }

    @Test
    fun `error phase from tree`() {
        val tree = FileTreeState(
            rootPath = "/test",
            scanPhase = ScanPhase.ERROR,
            errorMessage = "Access denied"
        )
        val state = deriveExplorerState(tree, UiSelections())

        assertEquals("Access denied", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `UI error takes precedence over tree error`() {
        val tree = FileTreeState(
            rootPath = "/test",
            rootNode = directory("/test", "test"),
            scanPhase = ScanPhase.COMPLETED
        )
        val ui = UiSelections(errorMessage = "Delete failed")
        val state = deriveExplorerState(tree, ui)

        assertEquals("Delete failed", state.errorMessage)
    }

    @Test
    fun `tree error shown when no UI error`() {
        val tree = FileTreeState(
            rootPath = "/test",
            scanPhase = ScanPhase.ERROR,
            errorMessage = "Scan failed"
        )
        val ui = UiSelections(errorMessage = null)
        val state = deriveExplorerState(tree, ui)

        assertEquals("Scan failed", state.errorMessage)
    }

    @Test
    fun `sort order from UI selections`() {
        val root = directory("/test", "test", children = listOf(
            directory("/test/alpha", "alpha", size = 100),
            directory("/test/beta", "beta", size = 200)
        ))
        val tree = FileTreeState(rootPath = "/test", rootNode = root, scanPhase = ScanPhase.COMPLETED)

        // SIZE_DESC
        val sizeDesc = deriveExplorerState(tree, UiSelections(sortOrder = SortOrder.SIZE_DESC))
        assertEquals("beta", sizeDesc.browserItems[0].node.name)

        // NAME_ASC
        val nameAsc = deriveExplorerState(tree, UiSelections(sortOrder = SortOrder.NAME_ASC))
        assertEquals("alpha", nameAsc.browserItems[0].node.name)
    }

    @Test
    fun `expanded paths from UI selections`() {
        val root = directory("/test", "test", children = listOf(
            directory("/test/dir", "dir", children = listOf(
                file("/test/dir/file.txt", "file.txt", size = 50)
            ))
        ))
        val tree = FileTreeState(rootPath = "/test", rootNode = root, scanPhase = ScanPhase.COMPLETED)

        // Not expanded
        val collapsed = deriveExplorerState(tree, UiSelections())
        assertEquals(1, collapsed.browserItems.size)

        // Expanded
        val expanded = deriveExplorerState(tree, UiSelections(expandedPaths = setOf("/test/dir")))
        assertEquals(2, expanded.browserItems.size)
        assertEquals("file.txt", expanded.browserItems[1].node.name)
    }

    @Test
    fun `selected index is bounded`() {
        val root = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 100)
        ))
        val tree = FileTreeState(rootPath = "/test", rootNode = root, scanPhase = ScanPhase.COMPLETED)

        val state = deriveExplorerState(tree, UiSelections(selectedIndex = 99))
        assertEquals(0, state.selectedIndex) // Bounded to last index (0)
    }

    @Test
    fun `selected index 0 when no items`() {
        val tree = FileTreeState(rootPath = "/test", scanPhase = ScanPhase.LOADING)
        val state = deriveExplorerState(tree, UiSelections(selectedIndex = 5))
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun `progress info is carried through`() {
        val tree = FileTreeState(
            rootPath = "/test",
            scanPhase = ScanPhase.SCANNING,
            scanProgress = ScanProgress(
                processedFiles = 42,
                processedDirectories = 5,
                scannedBytes = 10240,
                bytesPerSecond = 1024,
                currentDirectory = "/test/subdir"
            )
        )
        val state = deriveExplorerState(tree, UiSelections())

        assertNotNull(state.loadingProgress)
        assertEquals(42, state.loadingProgress!!.processedFiles)
        assertEquals("/test/subdir", state.loadingDirectoryPath)
    }

    @Test
    fun `scanning dir live bytes from progress minus last partial`() {
        val root = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 100)
        ))
        val tree = FileTreeState(
            rootPath = "/test",
            rootNode = root,
            scanPhase = ScanPhase.SCANNING,
            scanProgress = ScanProgress(10, 2, 500, 100, "/test/a/sub"),
            lastPartialScannedBytes = 200
        )
        val state = deriveExplorerState(tree, UiSelections())

        // scanningDirLiveBytes = 500 - 200 = 300
        assertEquals(300, state.scanningDirLiveBytes)
    }

    @Test
    fun `warning count from errors`() {
        val root = directory("/test", "test")
        val tree = FileTreeState(
            rootPath = "/test",
            rootNode = root,
            scanPhase = ScanPhase.COMPLETED,
            errors = listOf(
                ScanError("/test/secret", "denied", ErrorType.PERMISSION_DENIED),
                ScanError("/test/gone", "not found", ErrorType.NOT_FOUND)
            )
        )
        val state = deriveExplorerState(tree, UiSelections())

        assertEquals(2, state.warningCount)
    }

    @Test
    fun `UI selections are passed through`() {
        val tree = FileTreeState(rootPath = "/test", scanPhase = ScanPhase.IDLE)
        val item = BrowserItem(
            node = directory("/test/x", "x"),
            totalSize = 0,
            kind = BrowserItemKind.DIRECTORY
        )
        val ui = UiSelections(
            spinnerIndex = 7,
            isDeletingInProgress = true,
            confirmDeleteItem = item
        )
        val state = deriveExplorerState(tree, ui)

        assertEquals(7, state.spinnerIndex)
        assertTrue(state.isDeletingInProgress)
        assertNotNull(state.confirmDeleteItem)
    }

    // ── Helpers ──

    private fun directory(
        path: String,
        name: String,
        size: Long = 0,
        children: List<FileNode> = emptyList()
    ): FileNode = createFileNode(
        path = path, name = name, size = size,
        isDirectory = true, isSymlink = false,
        children = children, lastModified = 0L
    )

    private fun file(
        path: String,
        name: String,
        size: Long
    ): FileNode = createFileNode(
        path = path, name = name, size = size,
        isDirectory = false, isSymlink = false,
        children = emptyList(), lastModified = 0L
    )
}
