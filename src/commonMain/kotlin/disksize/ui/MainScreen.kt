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
    onOpenSelected: () -> Unit,
    onNavigateUp: () -> Unit,
    onCycleSort: () -> Unit,
    onRefresh: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onQuit: () -> Unit
) {
    val terminalState = LocalTerminalState.current
    val frameWidth = max(MIN_WIDTH, terminalState?.size?.columns ?: 80)
    val frameRows = max(MIN_ROWS, terminalState?.size?.rows ?: 24)

    val frameLines = remember(state, frameWidth, frameRows) {
        buildScreenLines(state, frameWidth, frameRows)
    }

    Column(
        modifier = Modifier.onKeyEvent { event ->
            handleKey(
                event = event,
                state = state,
                moveSelection = onMoveSelection,
                openSelected = onOpenSelected,
                navigateUp = onNavigateUp,
                cycleSort = onCycleSort,
                refresh = onRefresh,
                requestDelete = onRequestDelete,
                confirmDelete = onConfirmDelete,
                cancelDelete = onCancelDelete,
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
    moveSelection: (Int) -> Unit,
    openSelected: () -> Unit,
    navigateUp: () -> Unit,
    cycleSort: () -> Unit,
    refresh: () -> Unit,
    requestDelete: () -> Unit,
    confirmDelete: () -> Unit,
    cancelDelete: () -> Unit,
    quit: () -> Unit
): Boolean {
    // If confirmation dialog is showing, only handle y/n/Escape
    if (state.confirmDeleteItem != null) {
        return when (event.key) {
            "y", "Y" -> { confirmDelete(); true }
            "n", "N", "Escape" -> { cancelDelete(); true }
            else -> false
        }
    }

    // Normal navigation and operations
    return when (event.key) {
        "ArrowDown", "j" -> { moveSelection(1); true }
        "ArrowUp", "k" -> { moveSelection(-1); true }
        "Enter", "ArrowRight", "l" -> { openSelected(); true }
        "Backspace", "ArrowLeft", "h" -> { navigateUp(); true }
        "s", "S" -> { cycleSort(); true }
        "r", "R" -> { refresh(); true }
        "Delete" -> { requestDelete(); true }
        "q", "Q" -> { quit(); true }
        else -> false
    }
}
