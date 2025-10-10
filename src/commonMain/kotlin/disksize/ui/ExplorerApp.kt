package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanResult
import disksize.domain.usecase.ScanDirectoryUseCase
import disksize.presentation.ExplorerState
import disksize.presentation.resetSelection
import disksize.presentation.tickSpinner
import disksize.presentation.withError
import disksize.presentation.withLoading
import disksize.presentation.withScanResult
import disksize.presentation.withSelection
import disksize.presentation.withNextSortOrder
import disksize.util.normalizePath
import disksize.util.parentPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

@Composable
fun DiskSizeApp(
    initialPath: String,
    scanDirectoryUseCase: ScanDirectoryUseCase
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
        state = state.withLoading(path)
        val result = withContext(Dispatchers.Default) {
            scanDirectoryUseCase.execute(path)
        }
        state = result.fold(
            onSuccess = { scanResult ->
                state.withScanResult(scanResult).resetSelection()
            },
            onFailure = { throwable ->
                val message = throwable.message ?: "Failed to scan $path"
                state.withError(message)
            }
        )
        pendingScan = null
        history = emptyList()
    }

    LaunchedEffect(state.isLoading, pendingScan) {
        if (!state.isLoading) return@LaunchedEffect
        while (true) {
            delay(120)
            if (!state.isLoading) break
            state = state.tickSpinner()
        }
    }

    MainScreen(
        state = state,
        onMoveSelection = { delta ->
            val dirs = state.directories
            if (dirs.isEmpty()) return@MainScreen
            val next = state.selectedIndex + delta
            val bounded = next.coerceIn(0, dirs.lastIndex)
            if (bounded != state.selectedIndex) {
                state = state.withSelection(bounded)
            }
        },
        onOpenSelected = {
            val dirs = state.directories
            val selected = dirs.getOrNull(state.selectedIndex) ?: return@MainScreen
            val currentScan = state.scanResult ?: return@MainScreen
            history = history + state.copy()
            val childErrors = currentScan.errors.filter { it.path == selected.path || it.path.startsWith("${selected.path}/") }
            state = explorerStateFromNode(state, selected, childErrors)
            pendingScan = null
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
        scanResult = scanResult,
        isLoading = false,
        errorMessage = null,
        selectedIndex = 0,
        sortOrder = previousState.sortOrder,
        spinnerIndex = 0
    )
}
