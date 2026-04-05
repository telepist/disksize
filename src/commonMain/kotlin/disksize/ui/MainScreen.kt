package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.LocalTerminalState
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
    onKeyEvent: (key: String, pageSize: Int) -> Boolean
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
            onKeyEvent(event.key, pageSize)
        }
    ) {
        FrameLines(frameLines)
    }
}
