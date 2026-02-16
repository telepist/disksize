package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.presentation.ExplorerState
import disksize.util.SizeFormatter

private const val KEY_HINTS = "  Enter: Expand  s: Sort  r: Refresh  Del: Delete  q: Quit"
private const val SCAN_KEY_HINTS = "  Enter: Expand  s: Sort  q: Quit"
private const val LOADING_KEY_HINTS = "  q: Quit"

internal fun statusLine(state: ExplorerState, width: Int): FrameLine {
    val innerWidth = width - 2
    val keyHints = when {
        state.isLoading -> LOADING_KEY_HINTS
        state.isScanInProgress -> SCAN_KEY_HINTS
        else -> KEY_HINTS
    }
    val availableForStatus = innerWidth - keyHints.length

    val segments = mutableListOf<Segment>()
    when {
        state.isLoading -> {
            segments += Segment("Scanning ", Color.Cyan)
            segments += Segment(state.spinnerFrame.toString(), Color.Yellow)

            // Calculate elapsed time
            val elapsedText = state.scanStartTimeMark?.let { mark ->
                val elapsedSeconds = mark.elapsedNow().inWholeSeconds
                " (${elapsedSeconds}s) "
            } ?: " "

            val pathMaxLength = (availableForStatus - 10 - elapsedText.length).coerceAtLeast(10)
            segments += Segment(elapsedText, Color.Yellow)
            segments += Segment(shortenPath(state.currentPath, pathMaxLength), Color.Cyan)
        }
        state.isScanInProgress -> {
            segments += Segment("Scanning ", Color.Cyan)
            segments += Segment(state.spinnerFrame.toString(), Color.Yellow)

            val elapsedText = state.scanStartTimeMark?.let { mark ->
                val elapsedSeconds = mark.elapsedNow().inWholeSeconds
                " (${elapsedSeconds}s)"
            } ?: ""
            segments += Segment(elapsedText, Color.Yellow)

            val progress = state.loadingProgress
            if (progress != null) {
                segments += Segment(" Files:${formatCount(progress.processedFiles)}", Color.Cyan)
                segments += Segment(" Dirs:${formatCount(progress.processedDirectories)}", Color.Cyan)
                segments += Segment(" ${SizeFormatter.format(progress.scannedBytes)}", Color.Green)
            }
        }
        state.errorMessage != null -> {
            segments += Segment("Error: ${state.errorMessage.take(availableForStatus - 7)}", Color.Red)
        }
        state.scanResult != null -> {
            val seconds = state.scanDurationMs / 1000.0
            val base = "Scan completed in ${formatDuration(seconds)}s"
            segments += Segment(base, Color.Green)
            if (state.warningCount > 0) {
                segments += Segment(" • ${state.warningCount} warning(s)", Color.Yellow)
            }
        }
        else -> segments += Segment("Idle", Color.Cyan)
    }
    val statusLength = segments.sumOf { it.text.length }
    val padding = (innerWidth - statusLength - keyHints.length).coerceAtLeast(1)
    segments += Segment(" ".repeat(padding))
    segments += Segment(keyHints, Color.Yellow)
    return frameLine(width, segments)
}
