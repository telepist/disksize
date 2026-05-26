package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import disksize.domain.model.ScanPhase
import disksize.presentation.ExplorerViewModel

/**
 * HACK: Thrown to exit the Mosaic composition gracefully.
 * Workaround for https://github.com/JakeWharton/mosaic/issues/963 —
 * using exitProcess() corrupts terminal state (cursor disappears, raw mode not reset).
 * Remove once Mosaic supports proper exiting via MosaicScope cancellation.
 */
class ExitException : RuntimeException("exit")

@Composable
fun DiskSizeApp(
    initialPath: String,
    viewModel: ExplorerViewModel
) {
    val state by viewModel.state.collectAsState()

    // Kick off initial scan
    LaunchedEffect(Unit) {
        viewModel.startScan(initialPath)
    }

    // Spinner animation
    LaunchedEffect(state.isLoading, state.isScanInProgress, state.isRefreshing, state.isDeletingInProgress) {
        if (state.isLoading || state.isScanInProgress || state.isRefreshing || state.isDeletingInProgress) {
            viewModel.startSpinnerIfNeeded()
        }
    }

    MainScreen(
        state = state,
        onKeyEvent = { key, pageSize ->
            viewModel.handleKey(key, pageSize, onQuit = { throw ExitException() })
        }
    )
}
