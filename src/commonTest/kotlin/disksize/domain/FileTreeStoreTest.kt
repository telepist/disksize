package disksize.domain

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

class FileTreeStoreTest {

    @Test
    fun `reset sets loading state and clears tree`() {
        val store = FileTreeStore()
        store.reset("/test")

        val state = store.state.value
        assertEquals("/test", state.rootPath)
        assertEquals(ScanPhase.LOADING, state.scanPhase)
        assertNull(state.rootNode)
        assertNotNull(state.scanStartTimeMark)
    }

    @Test
    fun `updateProgress updates scan progress`() {
        val store = FileTreeStore()
        store.reset("/test")

        val progress = ScanProgress(
            processedFiles = 10,
            processedDirectories = 3,
            scannedBytes = 1024,
            bytesPerSecond = 512,
            currentDirectory = "/test/sub"
        )
        store.updateProgress(progress)

        assertEquals(progress, store.state.value.scanProgress)
    }

    @Test
    fun `applyPartialTree updates tree and sets scanning phase`() {
        val store = FileTreeStore()
        store.reset("/test")

        val root = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 100),
            directory("/test/b", "b", size = 200)
        ))
        store.applyPartialTree(root, scannedPaths = setOf("/test/a"), errors = emptyList())

        val state = store.state.value
        assertEquals(ScanPhase.SCANNING, state.scanPhase)
        assertNotNull(state.rootNode)
        assertEquals(2, state.rootNode!!.children.size)
        assertEquals(setOf("/test/a"), state.scannedPaths)
    }

    @Test
    fun `applyComplete sets completed phase and clears progress`() {
        val store = FileTreeStore()
        store.reset("/test")
        store.updateProgress(ScanProgress(1, 0, 100, 50))

        val root = directory("/test", "test", children = listOf(
            file("/test/file.txt", "file.txt", size = 100)
        ))
        store.applyComplete(root, errors = emptyList(), durationMs = 42)

        val state = store.state.value
        assertEquals(ScanPhase.COMPLETED, state.scanPhase)
        assertNull(state.scanProgress)
        assertEquals(42, state.scanDurationMs)
        assertTrue(state.scannedPaths.isEmpty())
    }

    @Test
    fun `removeNode removes node from tree`() {
        val store = FileTreeStore()
        store.reset("/test")

        val root = directory("/test", "test", children = listOf(
            file("/test/a.txt", "a.txt", size = 100),
            file("/test/b.txt", "b.txt", size = 200)
        ))
        store.applyComplete(root, emptyList(), 0)

        store.removeNode("/test/a.txt")

        val state = store.state.value
        assertEquals(1, state.rootNode!!.children.size)
        assertEquals("b.txt", state.rootNode!!.children[0].name)
    }

    @Test
    fun `removeNode during scan filters deleted path from subsequent partial trees`() {
        val store = FileTreeStore()
        store.reset("/test")

        val root1 = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 100),
            directory("/test/b", "b", size = 200)
        ))
        store.applyPartialTree(root1, setOf("/test/a"), emptyList())

        // Delete "a" during scan
        store.removeNode("/test/a")
        assertEquals(1, store.state.value.rootNode!!.children.size)

        // Next partial tree arrives with "a" still in it (from disk scan)
        val root2 = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 150),
            directory("/test/b", "b", size = 300)
        ))
        store.applyPartialTree(root2, setOf("/test/a", "/test/b"), emptyList())

        // "a" should still be filtered out
        val state = store.state.value
        assertEquals(1, state.rootNode!!.children.size)
        assertEquals("b", state.rootNode!!.children[0].name)
    }

    @Test
    fun `removeNode during scan filters deleted path from completed tree`() {
        val store = FileTreeStore()
        store.reset("/test")

        val root1 = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 100),
            directory("/test/b", "b", size = 200)
        ))
        store.applyPartialTree(root1, setOf("/test/a"), emptyList())

        store.removeNode("/test/a")

        // Complete scan arrives with "a" still present
        val rootFinal = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 150),
            directory("/test/b", "b", size = 300)
        ))
        store.applyComplete(rootFinal, emptyList(), 100)

        val state = store.state.value
        assertEquals(1, state.rootNode!!.children.size)
        assertEquals("b", state.rootNode!!.children[0].name)
    }

    @Test
    fun `deletedDuringScan is cleared after complete`() {
        val store = FileTreeStore()
        store.reset("/test")

        val root = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 100)
        ))
        store.applyPartialTree(root, emptySet(), emptyList())
        store.removeNode("/test/a")
        store.applyComplete(root, emptyList(), 0)

        // Start a new scan — the deleted path should not be filtered
        store.reset("/test")
        val newRoot = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 200)
        ))
        store.applyPartialTree(newRoot, emptySet(), emptyList())

        assertEquals(1, store.state.value.rootNode!!.children.size)
        assertEquals("a", store.state.value.rootNode!!.children[0].name)
    }

    @Test
    fun `removeNode after scan complete does not track in deletedDuringScan`() {
        val store = FileTreeStore()
        store.reset("/test")

        val root = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 100),
            directory("/test/b", "b", size = 200)
        ))
        store.applyComplete(root, emptyList(), 0)

        // Delete after scan is complete
        store.removeNode("/test/a")
        assertEquals(1, store.state.value.rootNode!!.children.size)

        // If we do a new scan, "a" should appear (it wasn't in deletedDuringScan)
        store.reset("/test")
        val newRoot = directory("/test", "test", children = listOf(
            directory("/test/a", "a", size = 100),
            directory("/test/b", "b", size = 200)
        ))
        store.applyComplete(newRoot, emptyList(), 0)
        assertEquals(2, store.state.value.rootNode!!.children.size)
    }

    @Test
    fun `setError sets error phase and message`() {
        val store = FileTreeStore()
        store.reset("/test")
        store.setError("Access denied")

        val state = store.state.value
        assertEquals(ScanPhase.ERROR, state.scanPhase)
        assertEquals("Access denied", state.errorMessage)
        assertNull(state.scanProgress)
    }

    @Test
    fun `restore overwrites state`() {
        val store = FileTreeStore()
        val savedState = FileTreeState(
            rootPath = "/saved",
            rootNode = directory("/saved", "saved", children = listOf(
                file("/saved/x.txt", "x.txt", size = 42)
            )),
            scanPhase = ScanPhase.COMPLETED,
            scanDurationMs = 99
        )

        store.restore(savedState)

        val state = store.state.value
        assertEquals("/saved", state.rootPath)
        assertEquals(ScanPhase.COMPLETED, state.scanPhase)
        assertEquals(1, state.rootNode!!.children.size)
        assertEquals(99, state.scanDurationMs)
    }

    @Test
    fun `applyPartialTree stores lastPartialScannedBytes from progress`() {
        val store = FileTreeStore()
        store.reset("/test")
        store.updateProgress(ScanProgress(5, 2, 1024, 512))

        val root = directory("/test", "test")
        store.applyPartialTree(root, emptySet(), emptyList())

        assertEquals(1024, store.state.value.lastPartialScannedBytes)
    }

    @Test
    fun `errors are tracked through scan phases`() {
        val store = FileTreeStore()
        store.reset("/test")

        val errors = listOf(
            ScanError("/test/secret", "Permission denied", ErrorType.PERMISSION_DENIED)
        )
        val root = directory("/test", "test")
        store.applyPartialTree(root, emptySet(), errors)
        assertEquals(1, store.state.value.errors.size)

        store.applyComplete(root, errors, 100)
        assertEquals(1, store.state.value.errors.size)
        assertEquals("Permission denied", store.state.value.errors[0].message)
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
