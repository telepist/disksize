package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Column
import disksize.presentation.ExplorerState
import kotlin.math.max

private const val MIN_WIDTH = 48
private const val MIN_ROWS = 16

@Composable
fun MainScreen(
    state: ExplorerState,
    onMoveSelection: (Int) -> Unit,
    onToggleExpand: () -> Unit,
    onExpandOrEnter: () -> Unit,
    onCollapseOrParent: () -> Unit,
    onNavigateUp: () -> Unit,
    onCycleSort: () -> Unit,
    onRefresh: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onClearError: () -> Unit,
    onQuit: () -> Unit
) {
    val terminalState = LocalTerminalState.current
    val frameWidth = max(MIN_WIDTH, (terminalState?.size?.columns ?: 80) - 1)
    val frameRows = max(MIN_ROWS, terminalState?.size?.rows ?: 24)

    val frameLines = remember(state, frameWidth, frameRows) {
        buildScreenLines(state, frameWidth, frameRows)
    }

    // Page size = approximate visible item rows in the directory listing area.
    // Overhead: header 1 + stats 1 + rule 1 + rule 1 + status 1 = 5, plus 1 margin = 6
    val pageSize = max(1, frameRows - 6)

    Column(
        modifier = Modifier.onKeyEvent { event ->
            handleKey(
                event = event,
                state = state,
                pageSize = pageSize,
                moveSelection = onMoveSelection,
                toggleExpand = onToggleExpand,
                expandOrEnter = onExpandOrEnter,
                collapseOrParent = onCollapseOrParent,
                navigateUp = onNavigateUp,
                cycleSort = onCycleSort,
                refresh = onRefresh,
                requestDelete = onRequestDelete,
                confirmDelete = onConfirmDelete,
                cancelDelete = onCancelDelete,
                clearError = onClearError,
                quit = onQuit
            )
        }
    ) {
        FrameLines(frameLines)
    }
}

private fun handleKey(
    event: KeyEvent,
    state: ExplorerState,
    pageSize: Int,
    moveSelection: (Int) -> Unit,
    toggleExpand: () -> Unit,
    expandOrEnter: () -> Unit,
    collapseOrParent: () -> Unit,
    navigateUp: () -> Unit,
    cycleSort: () -> Unit,
    refresh: () -> Unit,
    requestDelete: () -> Unit,
    confirmDelete: () -> Unit,
    cancelDelete: () -> Unit,
    clearError: () -> Unit,
    quit: () -> Unit
): Boolean {
    // If deletion is in progress, ignore all keys
    if (state.isDeletingInProgress) {
        return false
    }

    // If confirmation dialog is showing, only handle y/n/Escape
    if (state.confirmDeleteItem != null) {
        return when (event.key) {
            "y", "Y" -> { confirmDelete(); true }
            "n", "N", "Escape" -> { cancelDelete(); true }
            else -> false
        }
    }

    // If scanning is in progress, only allow quit
    if (state.isLoading) {
        return when (event.key) {
            "q", "Q" -> { quit(); true }
            else -> false
        }
    }

    // Clear transient error on any key press
    if (state.errorMessage != null && state.scanResult != null) {
        clearError()
    }

    // Normal navigation and operations
    return when (event.key) {
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
        "Delete" -> { if (!state.isScanInProgress) requestDelete(); true }
        "q", "Q" -> { quit(); true }
        else -> false
    }
}
