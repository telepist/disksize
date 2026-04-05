package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import disksize.domain.FileTreeStore
import disksize.domain.model.DeletionResult
import disksize.domain.model.FileTreeState
import disksize.domain.model.ScanPhase
import disksize.domain.usecase.DeleteFileUseCase
import disksize.domain.usecase.ScanDirectoryUseCase
import disksize.presentation.BrowserItemKind
import disksize.presentation.UiSelections
import disksize.presentation.deriveExplorerState
import disksize.presentation.findParentIndex
import disksize.util.normalizePath
import disksize.util.parentPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * HACK: Thrown to exit the Mosaic composition gracefully.
 * Workaround for https://github.com/JakeWharton/mosaic/issues/963 —
 * using exitProcess() corrupts terminal state (cursor disappears, raw mode not reset).
 * Remove once Mosaic supports proper exiting via MosaicScope cancellation.
 */
class ExitException : RuntimeException("exit")

data class HistoryEntry(
    val treeState: FileTreeState,
    val selectedIndex: Int,
    val sortOrder: disksize.presentation.SortOrder,
    val expandedPaths: Set<String>
)

@Composable
fun DiskSizeApp(
    initialPath: String,
    scanDirectoryUseCase: ScanDirectoryUseCase,
    deleteFileUseCase: DeleteFileUseCase,
    store: FileTreeStore
) {
    val treeState by store.state.collectAsState()
    var ui by remember { mutableStateOf(UiSelections()) }
    var pendingScan by remember { mutableStateOf<String?>(initialPath) }
    var history by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    val scope = rememberCoroutineScope()

    // Derive the full ExplorerState reactively
    val state = remember(treeState, ui) {
        deriveExplorerState(treeState, ui)
    }

    // Scan effect — launches scan and pushes updates into the store
    LaunchedEffect(pendingScan) {
        val path = pendingScan ?: return@LaunchedEffect
        ui = ui.copy(selectedIndex = 0, expandedPaths = emptySet(), errorMessage = null)
        try {
            scanDirectoryUseCase.scanInto(path, store)
        } catch (e: ExitException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (throwable: Throwable) {
            store.setError(throwable.message ?: "Failed to scan $path")
        }
        pendingScan = null
    }

    // Spinner animation
    LaunchedEffect(treeState.scanPhase, ui.isDeletingInProgress) {
        val needsSpinner = treeState.scanPhase == ScanPhase.LOADING ||
            treeState.scanPhase == ScanPhase.SCANNING ||
            ui.isDeletingInProgress
        if (!needsSpinner) return@LaunchedEffect
        while (true) {
            delay(120)
            ui = ui.copy(spinnerIndex = ui.spinnerIndex + 1)
            val phase = store.state.value.scanPhase
            if (phase != ScanPhase.LOADING && phase != ScanPhase.SCANNING && !ui.isDeletingInProgress) break
        }
    }

    MainScreen(
        state = state,
        onMoveSelection = { delta ->
            val items = state.browserItems
            if (items.isEmpty()) return@MainScreen
            val next = ui.selectedIndex + delta
            val bounded = next.coerceIn(0, items.lastIndex)
            if (bounded != ui.selectedIndex) {
                ui = ui.copy(selectedIndex = bounded)
            }
        },
        onToggleExpand = {
            val selectedItem = state.browserItems.getOrNull(state.selectedIndex) ?: return@MainScreen
            if (!selectedItem.node.isDirectory) return@MainScreen
            val path = selectedItem.node.path
            val newExpanded = if (path in ui.expandedPaths) {
                ui.expandedPaths.filterNot { p -> p == path || p.isSubPathOf(path) }.toSet()
            } else {
                ui.expandedPaths + path
            }
            ui = ui.copy(expandedPaths = newExpanded)
        },
        onExpandOrEnter = {
            val selectedItem = state.browserItems.getOrNull(state.selectedIndex) ?: return@MainScreen
            if (!selectedItem.node.isDirectory) return@MainScreen
            if (selectedItem.isExpanded) {
                val nextIndex = state.selectedIndex + 1
                if (nextIndex < state.browserItems.size && state.browserItems[nextIndex].depth > selectedItem.depth) {
                    ui = ui.copy(selectedIndex = nextIndex)
                }
            } else {
                ui = ui.copy(expandedPaths = ui.expandedPaths + selectedItem.node.path)
            }
        },
        onCollapseOrParent = {
            val selectedItem = state.browserItems.getOrNull(state.selectedIndex) ?: return@MainScreen
            if (selectedItem.kind == BrowserItemKind.DIRECTORY && selectedItem.isExpanded) {
                val path = selectedItem.node.path
                ui = ui.copy(
                    expandedPaths = ui.expandedPaths.filterNot { p -> p == path || p.isSubPathOf(path) }.toSet()
                )
            } else {
                val parentIdx: Int? = state.findParentIndex(state.selectedIndex)
                if (parentIdx != null) {
                    ui = ui.copy(selectedIndex = parentIdx)
                }
            }
        },
        onNavigateUp = {
            if (history.isNotEmpty()) {
                val previous = history.last()
                history = history.dropLast(1)
                store.restore(previous.treeState)
                ui = ui.copy(
                    selectedIndex = previous.selectedIndex,
                    sortOrder = previous.sortOrder,
                    expandedPaths = previous.expandedPaths
                )
                pendingScan = null
            } else {
                val parent = parentPath(state.currentPath) ?: return@MainScreen
                pendingScan = normalizePath(parent)
            }
        },
        onCycleSort = {
            ui = ui.copy(sortOrder = ui.sortOrder.next())
        },
        onRefresh = {
            if (!state.isScanInProgress) {
                pendingScan = state.currentPath
            }
        },
        onRequestDelete = {
            val selectedItem = state.browserItems.getOrNull(state.selectedIndex) ?: return@MainScreen
            ui = ui.copy(confirmDeleteItem = selectedItem)
        },
        onConfirmDelete = {
            val itemToDelete = ui.confirmDeleteItem ?: return@MainScreen
            ui = ui.copy(isDeletingInProgress = true)
            scope.launch {
                val result = deleteFileUseCase.delete(itemToDelete.node.path)
                when (result) {
                    is DeletionResult.Success -> {
                        store.removeNode(itemToDelete.node.path)
                        ui = ui.copy(confirmDeleteItem = null, isDeletingInProgress = false)
                    }
                    is DeletionResult.Failure -> {
                        ui = ui.copy(
                            confirmDeleteItem = null,
                            isDeletingInProgress = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        },
        onCancelDelete = {
            ui = ui.copy(confirmDeleteItem = null, isDeletingInProgress = false)
        },
        onClearError = {
            ui = ui.copy(errorMessage = null)
        },
        onQuit = {
            throw ExitException()
        }
    )
}

private fun String.isSubPathOf(parent: String): Boolean =
    startsWith("$parent/") || startsWith("$parent\\")
