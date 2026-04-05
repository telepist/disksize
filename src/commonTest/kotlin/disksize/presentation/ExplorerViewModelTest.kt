package disksize.presentation

import disksize.data.fake.FakeFileSystemRepository
import disksize.domain.FileTreeStore
import disksize.domain.model.FileNode
import disksize.domain.model.ScanPhase
import disksize.domain.model.createFileNode
import disksize.domain.usecase.DeleteFileUseCase
import disksize.domain.usecase.ScanDirectoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerViewModelTest {

    private lateinit var repository: FakeFileSystemRepository
    private lateinit var store: FileTreeStore
    private lateinit var viewModel: ExplorerViewModel
    private lateinit var vmScope: CoroutineScope

    @BeforeTest
    fun setup() {
        repository = FakeFileSystemRepository()
        store = FileTreeStore()
        val dispatcher = UnconfinedTestDispatcher()
        val scanUseCase = ScanDirectoryUseCase(repository, dispatcher)
        val deleteUseCase = DeleteFileUseCase(repository)
        vmScope = CoroutineScope(dispatcher)
        viewModel = ExplorerViewModel(scanUseCase, deleteUseCase, store, vmScope)
    }

    @AfterTest
    fun teardown() {
        vmScope.cancel()
    }

    private fun addTestTree() {
        val fileA = file("/root/a.txt", "a.txt", 300)
        val fileB = file("/root/b.txt", "b.txt", 100)
        val sub = directory("/root/dir", "dir", children = listOf(
            file("/root/dir/inner.txt", "inner.txt", 50)
        ))
        val root = directory("/root", "root", children = listOf(sub, fileA, fileB))
        repository.addFile(root)
    }

    // ── Scanning ──

    @Test
    fun `startScan loads directory and completes`() {
        addTestTree()

        viewModel.startScan("/root")

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isScanInProgress)
        assertNotNull(state.scanResult)
        assertEquals(450, state.totalSize)
        assertEquals(3, state.browserItems.size)
    }

    @Test
    fun `startScan with invalid path sets error`() {
        viewModel.startScan("/nonexistent")

        val state = viewModel.state.value
        assertNotNull(state.errorMessage)
        assertEquals(ScanPhase.ERROR, store.state.value.scanPhase)
    }

    @Test
    fun `refresh triggers rescan`() {
        addTestTree()

        viewModel.startScan("/root")
        assertEquals(450, viewModel.state.value.totalSize)

        repository.clear()
        val newRoot = directory("/root", "root", children = listOf(
            file("/root/big.bin", "big.bin", 1000)
        ))
        repository.addFile(newRoot)

        viewModel.refresh()

        assertEquals(1000, viewModel.state.value.totalSize)
    }

    // ── Selection ──

    @Test
    fun `moveSelection changes selected index`() {
        addTestTree()
        viewModel.startScan("/root")

        assertEquals(0, viewModel.state.value.selectedIndex)

        viewModel.moveSelection(1)
        assertEquals(1, viewModel.state.value.selectedIndex)

        viewModel.moveSelection(1)
        assertEquals(2, viewModel.state.value.selectedIndex)
    }

    @Test
    fun `moveSelection clamps to bounds`() {
        addTestTree()
        viewModel.startScan("/root")

        viewModel.moveSelection(-10)
        assertEquals(0, viewModel.state.value.selectedIndex)

        viewModel.moveSelection(100)
        assertEquals(2, viewModel.state.value.selectedIndex)
    }

    // ── Expand / collapse ──

    @Test
    fun `toggleExpand expands directory`() {
        addTestTree()
        viewModel.startScan("/root")

        val dirIndex = viewModel.state.value.browserItems.indexOfFirst { it.node.name == "dir" }
        viewModel.moveSelection(dirIndex)

        viewModel.toggleExpand()

        val state = viewModel.state.value
        assertTrue(state.expandedPaths.contains("/root/dir"))
        assertTrue(state.browserItems.any { it.node.name == "inner.txt" })
    }

    @Test
    fun `toggleExpand collapses expanded directory`() {
        addTestTree()
        viewModel.startScan("/root")

        val dirIndex = viewModel.state.value.browserItems.indexOfFirst { it.node.name == "dir" }
        viewModel.moveSelection(dirIndex)

        viewModel.toggleExpand()
        assertTrue(viewModel.state.value.expandedPaths.contains("/root/dir"))

        viewModel.toggleExpand()
        assertFalse(viewModel.state.value.expandedPaths.contains("/root/dir"))
        assertFalse(viewModel.state.value.browserItems.any { it.node.name == "inner.txt" })
    }

    // ── Sort ──

    @Test
    fun `cycleSort changes sort order`() {
        addTestTree()
        viewModel.startScan("/root")

        assertEquals(SortOrder.SIZE_DESC, viewModel.state.value.sortOrder)

        viewModel.cycleSort()
        assertEquals(SortOrder.NAME_ASC, viewModel.state.value.sortOrder)

        viewModel.cycleSort()
        assertEquals(SortOrder.DATE_DESC, viewModel.state.value.sortOrder)
    }

    // ── Delete ──

    @Test
    fun `requestDelete sets confirmDeleteItem`() {
        addTestTree()
        viewModel.startScan("/root")

        assertNull(viewModel.state.value.confirmDeleteItem)

        viewModel.requestDelete()

        assertNotNull(viewModel.state.value.confirmDeleteItem)
    }

    @Test
    fun `cancelDelete clears confirmDeleteItem`() {
        addTestTree()
        viewModel.startScan("/root")

        viewModel.requestDelete()
        assertNotNull(viewModel.state.value.confirmDeleteItem)

        viewModel.cancelDelete()
        assertNull(viewModel.state.value.confirmDeleteItem)
        assertFalse(viewModel.state.value.isDeletingInProgress)
    }

    @Test
    fun `confirmDelete removes item from tree`() {
        addTestTree()
        viewModel.startScan("/root")

        val aIndex = viewModel.state.value.browserItems.indexOfFirst { it.node.name == "a.txt" }
        viewModel.moveSelection(aIndex)

        viewModel.requestDelete()
        viewModel.confirmDelete()

        val state = viewModel.state.value
        assertNull(state.confirmDeleteItem)
        assertFalse(state.isDeletingInProgress)
        assertFalse(state.browserItems.any { it.node.name == "a.txt" })
        assertEquals(150, state.totalSize)
    }

    @Test
    fun `confirmDelete on locked file shows error`() {
        addTestTree()
        viewModel.startScan("/root")

        repository.markInaccessible("/root/a.txt")

        val aIndex = viewModel.state.value.browserItems.indexOfFirst { it.node.name == "a.txt" }
        viewModel.moveSelection(aIndex)

        viewModel.requestDelete()
        viewModel.confirmDelete()

        val state = viewModel.state.value
        assertNotNull(state.errorMessage)
        assertFalse(state.isDeletingInProgress)
        assertNull(state.confirmDeleteItem)
        assertTrue(state.browserItems.any { it.node.name == "a.txt" })
    }

    // ── Error ──

    @Test
    fun `clearError clears error message`() {
        addTestTree()
        viewModel.startScan("/root")

        repository.markInaccessible("/root/a.txt")
        val aIndex = viewModel.state.value.browserItems.indexOfFirst { it.node.name == "a.txt" }
        viewModel.moveSelection(aIndex)
        viewModel.requestDelete()
        viewModel.confirmDelete()
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.state.value.errorMessage)
    }

    // ── Key handling ──

    @Test
    fun `handleKey returns false during confirmation dialog`() {
        addTestTree()
        viewModel.startScan("/root")

        viewModel.requestDelete()

        assertFalse(viewModel.handleKey("j", 10) {})
        assertTrue(viewModel.handleKey("n", 10) {})
    }

    @Test
    fun `handleKey quit calls onQuit`() {
        addTestTree()
        viewModel.startScan("/root")

        var quitCalled = false
        viewModel.handleKey("q", 10) { quitCalled = true }
        assertTrue(quitCalled)
    }

    @Test
    fun `handleKey navigation`() {
        addTestTree()
        viewModel.startScan("/root")

        assertTrue(viewModel.handleKey("ArrowDown", 10) {})
        assertEquals(1, viewModel.state.value.selectedIndex)

        assertTrue(viewModel.handleKey("ArrowUp", 10) {})
        assertEquals(0, viewModel.state.value.selectedIndex)

        assertTrue(viewModel.handleKey("s", 10) {})
        assertEquals(SortOrder.NAME_ASC, viewModel.state.value.sortOrder)
    }

    @Test
    fun `handleKey clears transient error`() {
        addTestTree()
        viewModel.startScan("/root")

        repository.markInaccessible("/root/a.txt")
        val aIndex = viewModel.state.value.browserItems.indexOfFirst { it.node.name == "a.txt" }
        viewModel.moveSelection(aIndex)
        viewModel.requestDelete()
        viewModel.confirmDelete()
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.handleKey("j", 10) {}
        assertNull(viewModel.state.value.errorMessage)
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
