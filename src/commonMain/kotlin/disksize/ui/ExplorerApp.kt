package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import disksize.domain.model.DeletionResult
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanResult
import disksize.domain.model.ScanStatus
import disksize.domain.usecase.DeleteFileUseCase
import disksize.domain.usecase.ScanDirectoryUseCase
import disksize.presentation.ExplorerState
import disksize.presentation.cancelConfirmDelete
import disksize.presentation.BrowserItemKind
import disksize.presentation.findParentIndex
import disksize.presentation.resetSelection
import disksize.presentation.startDeleting
import disksize.presentation.tickSpinner
import disksize.presentation.withConfirmDelete
import disksize.presentation.withError
import disksize.presentation.withItemDeleted
import disksize.presentation.withLoading
import disksize.presentation.withNodeUpdated
import disksize.presentation.withPartialScanResult
import disksize.presentation.withScanResult
import disksize.presentation.withSelection
import disksize.presentation.withNextSortOrder
import disksize.presentation.withProgress
import disksize.presentation.withToggleExpand
import disksize.util.normalizePath
import disksize.util.parentPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

@Composable
fun DiskSizeApp(
    initialPath: String,
    scanDirectoryUseCase: ScanDirectoryUseCase,
    deleteFileUseCase: DeleteFileUseCase
) {
    var state by remember {
        mutableStateOf(ExplorerState(currentPath = initialPath, isLoading = true))
    }
    var pendingScan by remember { mutableStateOf<String?>(initialPath) }
    var history by remember { mutableStateOf(emptyList<ExplorerState>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        awaitCancellation()
    }

    LaunchedEffect(pendingScan) {
        val path = pendingScan ?: return@LaunchedEffect
        // Detect if this is a refresh (same path) vs navigation (different path)
        val isRefresh = (path == state.currentPath)
        state = state.withLoading(path)
        val result = try {
            scanDirectoryUseCase
                .scan(path)
                .flowOn(Dispatchers.Default)
                .collect { update ->
                when (update) {
                    is ScanStatus.Progress -> {
                        state = state.withProgress(update.value)
                    }
                    is ScanStatus.PartialResult -> {
                        state = state.withPartialScanResult(update.result, update.scannedPaths)
                    }
                    is ScanStatus.Completed -> {
                        state = state.withScanResult(update.result).resetSelection()
                        // Update history if this is a refresh to keep parent cache in sync
                        if (isRefresh && history.isNotEmpty()) {
                            history = history.map { it.withNodeUpdated(path, update.result.rootNode) }
                        }
                    }
                }
            }
            null
        } catch (throwable: Throwable) {
            throwable
        }
        result?.let { error ->
            val message = error.message ?: "Failed to scan $path"
            state = state.withError(message)
        }
        pendingScan = null
        // Only clear history when navigating to a different directory, not when refreshing
        if (!isRefresh) {
            history = emptyList()
        }
    }

    LaunchedEffect(state.isLoading, state.isScanInProgress, state.isDeletingInProgress, pendingScan) {
        if (!state.isLoading && !state.isScanInProgress && !state.isDeletingInProgress) return@LaunchedEffect
        while (true) {
            delay(120)
            if (!state.isLoading && !state.isScanInProgress && !state.isDeletingInProgress) break
            state = state.tickSpinner()
        }
    }

    MainScreen(
        state = state,
        onMoveSelection = { delta ->
            val items = state.browserItems
            if (items.isEmpty()) return@MainScreen
            val next = state.selectedIndex + delta
            val bounded = next.coerceIn(0, items.lastIndex)
            if (bounded != state.selectedIndex) {
                state = state.withSelection(bounded)
            }
        },
        onToggleExpand = {
            val selectedItem = state.browserItems.getOrNull(state.selectedIndex) ?: return@MainScreen
            if (!selectedItem.node.isDirectory) return@MainScreen
            state = state.withToggleExpand(selectedItem.node.path)
        },
        onExpandOrEnter = {
            val selectedItem = state.browserItems.getOrNull(state.selectedIndex) ?: return@MainScreen
            if (!selectedItem.node.isDirectory) return@MainScreen
            if (selectedItem.isExpanded) {
                // Already expanded — move to first child
                val nextIndex = state.selectedIndex + 1
                if (nextIndex < state.browserItems.size && state.browserItems[nextIndex].depth > selectedItem.depth) {
                    state = state.withSelection(nextIndex)
                }
            } else {
                state = state.withToggleExpand(selectedItem.node.path)
            }
        },
        onCollapseOrParent = {
            val selectedItem = state.browserItems.getOrNull(state.selectedIndex) ?: return@MainScreen
            if (selectedItem.kind == BrowserItemKind.DIRECTORY && selectedItem.isExpanded) {
                // Collapse this directory
                state = state.withToggleExpand(selectedItem.node.path)
            } else {
                // Jump to parent item in tree
                val parentIdx = state.findParentIndex(state.selectedIndex)
                if (parentIdx != null) {
                    state = state.withSelection(parentIdx)
                }
                // If depth 0, do nothing (Backspace handles going up)
            }
        },
        onNavigateUp = {
            if (history.isNotEmpty()) {
                val previous = history.last()
                history = history.dropLast(1)
                state = previous
                pendingScan = null
            } else {
                val parent = parentPath(state.currentPath) ?: return@MainScreen
                pendingScan = normalizePath(parent)
            }
        },
        onCycleSort = {
            state = state.withNextSortOrder()
        },
        onRefresh = {
            if (!state.isScanInProgress) {
                // Trigger a rescan of the current directory
                pendingScan = state.currentPath
                // History will be preserved and updated to reflect new scan results
            }
        },
        onRequestDelete = {
            if (!state.isScanInProgress) {
                val selectedItem = state.browserItems.getOrNull(state.selectedIndex) ?: return@MainScreen
                state = state.withConfirmDelete(selectedItem)
            }
        },
        onConfirmDelete = {
            val itemToDelete = state.confirmDeleteItem ?: return@MainScreen
            state = state.startDeleting()
            scope.launch {
                val result = deleteFileUseCase.delete(itemToDelete.node.path)
                when (result) {
                    is DeletionResult.Success -> {
                        state = state.withItemDeleted(itemToDelete.node.path)
                        // Update history to remove deleted item from all cached states
                        history = history.map { it.withItemDeleted(itemToDelete.node.path) }
                    }
                    is DeletionResult.Failure -> {
                        state = state.cancelConfirmDelete().withError(result.message)
                    }
                }
            }
        },
        onCancelDelete = {
            state = state.cancelConfirmDelete()
        },
        onQuit = {
            scope.cancel()
            exitProcess(0)
        }
    )
}

private fun explorerStateFromNode(
    previousState: ExplorerState,
    node: FileNode,
    errors: List<ScanError>
): ExplorerState {
    val scanResult = ScanResult(
        rootPath = node.path,
        totalSize = node.totalSize(),
        fileCount = node.fileCount(),
        directoryCount = node.directoryCount(),
        rootNode = node,
        scanDurationMs = 0,
        errors = errors
    )
    return ExplorerState(
        currentPath = node.path,
        sortOrder = previousState.sortOrder
    ).withScanResult(scanResult)
}
