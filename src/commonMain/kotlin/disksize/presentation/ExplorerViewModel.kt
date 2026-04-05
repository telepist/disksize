package disksize.presentation

import disksize.domain.FileTreeStore
import disksize.domain.model.DeletionResult
import disksize.domain.model.FileTreeState
import disksize.domain.model.ScanPhase
import disksize.domain.usecase.DeleteFileUseCase
import disksize.domain.usecase.ScanDirectoryUseCase
import disksize.util.normalizePath
import disksize.util.parentPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryEntry(
    val treeState: FileTreeState,
    val selectedIndex: Int,
    val sortOrder: SortOrder,
    val expandedPaths: Set<String>
)

/**
 * Presentation logic for the directory explorer.
 *
 * Owns [UiSelections], drives [FileTreeStore], and exposes a derived
 * [ExplorerState] via [state].  All user actions are plain method calls
 * that can be unit-tested without a Compose runtime.
 */
class ExplorerViewModel(
    private val scanDirectoryUseCase: ScanDirectoryUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val store: FileTreeStore,
    private val scope: CoroutineScope
) {
    private val _ui = MutableStateFlow(UiSelections())
    private var history = emptyList<HistoryEntry>()
    private var scanJob: Job? = null
    private var spinnerJob: Job? = null
    private val _state = MutableStateFlow(deriveExplorerState(store.state.value, _ui.value))

    /** The derived UI state — collect from Compose via `collectAsState()`. */
    val state: StateFlow<ExplorerState> = _state

    init {
        // Re-derive whenever the tree store changes
        scope.launch {
            store.state.collect { tree ->
                _state.value = deriveExplorerState(tree, _ui.value)
            }
        }
    }

    private val ui: UiSelections get() = _ui.value
    private val currentState: ExplorerState get() = _state.value

    /** Update UI selections and re-derive the exported state. */
    private fun updateUi(transform: (UiSelections) -> UiSelections) {
        _ui.update(transform)
        _state.value = deriveExplorerState(store.state.value, _ui.value)
    }

    // ── Scanning ──

    fun startScan(path: String) {
        updateUi { it.copy(selectedIndex = 0, expandedPaths = emptySet(), errorMessage = null) }
        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                scanDirectoryUseCase.scanInto(path, store)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (throwable: Throwable) {
                store.setError(throwable.message ?: "Failed to scan $path")
            }
        }
    }

    fun refresh() {
        if (!currentState.isScanInProgress) {
            startScan(currentState.currentPath)
        }
    }

    // ── Navigation ──

    fun moveSelection(delta: Int) {
        val items = currentState.browserItems
        if (items.isEmpty()) return
        val next = ui.selectedIndex + delta
        val bounded = next.coerceIn(0, items.lastIndex)
        if (bounded != ui.selectedIndex) {
            updateUi { it.copy(selectedIndex = bounded) }
        }
    }

    fun toggleExpand() {
        val selectedItem = currentState.browserItems.getOrNull(currentState.selectedIndex) ?: return
        if (!selectedItem.node.isDirectory) return
        val path = selectedItem.node.path
        updateUi { ui ->
            val newExpanded = if (path in ui.expandedPaths) {
                ui.expandedPaths.filterNot { p -> p == path || p.isSubPathOf(path) }.toSet()
            } else {
                ui.expandedPaths + path
            }
            ui.copy(expandedPaths = newExpanded)
        }
    }

    fun expandOrEnter() {
        val selectedItem = currentState.browserItems.getOrNull(currentState.selectedIndex) ?: return
        if (!selectedItem.node.isDirectory) return
        if (selectedItem.isExpanded) {
            val nextIndex = currentState.selectedIndex + 1
            if (nextIndex < currentState.browserItems.size &&
                currentState.browserItems[nextIndex].depth > selectedItem.depth) {
                updateUi { it.copy(selectedIndex = nextIndex) }
            }
        } else {
            updateUi { it.copy(expandedPaths = it.expandedPaths + selectedItem.node.path) }
        }
    }

    fun collapseOrParent() {
        val selectedItem = currentState.browserItems.getOrNull(currentState.selectedIndex) ?: return
        if (selectedItem.kind == BrowserItemKind.DIRECTORY && selectedItem.isExpanded) {
            val path = selectedItem.node.path
            updateUi { ui ->
                ui.copy(expandedPaths = ui.expandedPaths.filterNot { p -> p == path || p.isSubPathOf(path) }.toSet())
            }
        } else {
            val parentIdx = currentState.findParentIndex(currentState.selectedIndex)
            if (parentIdx != null) {
                updateUi { it.copy(selectedIndex = parentIdx) }
            }
        }
    }

    fun navigateUp() {
        if (history.isNotEmpty()) {
            val previous = history.last()
            history = history.dropLast(1)
            scanJob?.cancel()
            store.restore(previous.treeState)
            updateUi { it.copy(
                selectedIndex = previous.selectedIndex,
                sortOrder = previous.sortOrder,
                expandedPaths = previous.expandedPaths
            ) }
        } else {
            val parent = parentPath(currentState.currentPath) ?: return
            startScan(normalizePath(parent))
        }
    }

    fun cycleSort() {
        updateUi { it.copy(sortOrder = it.sortOrder.next()) }
    }

    // ── Delete ──

    fun requestDelete() {
        val selectedItem = currentState.browserItems.getOrNull(currentState.selectedIndex) ?: return
        updateUi { it.copy(confirmDeleteItem = selectedItem) }
    }

    fun confirmDelete() {
        val itemToDelete = ui.confirmDeleteItem ?: return
        updateUi { it.copy(isDeletingInProgress = true) }
        scope.launch {
            val result = deleteFileUseCase.delete(itemToDelete.node.path)
            when (result) {
                is DeletionResult.Success -> {
                    store.removeNode(itemToDelete.node.path)
                    updateUi { it.copy(confirmDeleteItem = null, isDeletingInProgress = false) }
                }
                is DeletionResult.Failure -> {
                    updateUi { it.copy(
                        confirmDeleteItem = null,
                        isDeletingInProgress = false,
                        errorMessage = result.message
                    ) }
                }
            }
        }
    }

    fun cancelDelete() {
        updateUi { it.copy(confirmDeleteItem = null, isDeletingInProgress = false) }
    }

    // ── Error ──

    fun clearError() {
        updateUi { it.copy(errorMessage = null) }
    }

    // ── Spinner ──

    fun startSpinnerIfNeeded() {
        spinnerJob?.cancel()
        spinnerJob = scope.launch {
            while (true) {
                val phase = store.state.value.scanPhase
                val deleting = ui.isDeletingInProgress
                if (phase != ScanPhase.LOADING && phase != ScanPhase.SCANNING && !deleting) break
                delay(120)
                updateUi { it.copy(spinnerIndex = it.spinnerIndex + 1) }
            }
        }
    }

    // ── Key handling ──

    /**
     * Process a key event. Returns true if the key was handled.
     * [pageSize] is the number of visible rows for PageUp/PageDown.
     * [onQuit] is called to exit the app (must be handled by the caller).
     */
    fun handleKey(key: String, pageSize: Int, onQuit: () -> Unit): Boolean {
        val state = currentState

        if (state.isDeletingInProgress) return false

        if (state.confirmDeleteItem != null) {
            return when (key) {
                "y", "Y" -> { confirmDelete(); true }
                "n", "N", "Escape" -> { cancelDelete(); true }
                else -> false
            }
        }

        if (state.isLoading && state.browserItems.isEmpty()) {
            return when (key) {
                "q", "Q" -> { onQuit(); true }
                else -> false
            }
        }

        if (state.errorMessage != null && state.scanResult != null) {
            clearError()
        }

        return when (key) {
            "ArrowDown", "j" -> { moveSelection(1); true }
            "ArrowUp", "k" -> { moveSelection(-1); true }
            "PageDown" -> { moveSelection(pageSize); true }
            "PageUp" -> { moveSelection(-pageSize); true }
            "Home" -> { moveSelection(-state.browserItems.size); true }
            "End" -> { moveSelection(state.browserItems.size); true }
            "Enter" -> { toggleExpand(); true }
            "ArrowRight", "l" -> { expandOrEnter(); true }
            "ArrowLeft", "h" -> { collapseOrParent(); true }
            "Backspace" -> { navigateUp(); true }
            "s", "S" -> { cycleSort(); true }
            "r", "R" -> { if (!state.isScanInProgress) refresh(); true }
            "Delete" -> { requestDelete(); true }
            "q", "Q" -> { onQuit(); true }
            else -> false
        }
    }
}

