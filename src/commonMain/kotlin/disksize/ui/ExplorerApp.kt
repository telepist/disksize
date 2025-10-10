package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import disksize.domain.usecase.ScanDirectoryUseCase
import disksize.presentation.ExplorerState
import disksize.presentation.resetSelection
import disksize.presentation.withError
import disksize.presentation.withLoading
import disksize.presentation.withScanResult
import disksize.presentation.withSelection
import disksize.presentation.withNextSortOrder
import disksize.presentation.tickSpinner
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
    var currentPath by remember { mutableStateOf(initialPath) }
    var reloadTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        awaitCancellation()
    }

    LaunchedEffect(currentPath, reloadTrigger) {
        state = state.withLoading(currentPath)
        val result = withContext(Dispatchers.Default) {
            scanDirectoryUseCase.execute(currentPath)
        }
        state = result.fold(
            onSuccess = { scanResult ->
                state.withScanResult(scanResult).resetSelection()
            },
            onFailure = { throwable ->
                val message = throwable.message ?: "Failed to scan $currentPath"
                state.withError(message)
            }
        )
    }

    LaunchedEffect(state.isLoading, reloadTrigger) {
        if (state.isLoading) {
            while (true) {
                delay(120)
                if (!state.isLoading) break
                state = state.tickSpinner()
            }
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
            if (state.isLoading || dirs.isEmpty()) return@MainScreen
            val selected = dirs.getOrNull(state.selectedIndex) ?: return@MainScreen
            currentPath = normalizePath(selected.path)
            reloadTrigger++
        },
        onNavigateUp = {
            if (state.isLoading) return@MainScreen
            val parent = parentPath(state.currentPath) ?: return@MainScreen
            currentPath = parent
            reloadTrigger++
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
