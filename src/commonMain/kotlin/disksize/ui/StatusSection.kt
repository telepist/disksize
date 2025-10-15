package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.presentation.ExplorerState
import disksize.util.SizeFormatter

internal fun statusLine(state: ExplorerState, width: Int): FrameLine {
    val innerWidth = width - 2
    val segments = mutableListOf<Segment>()
    when {
        state.isLoading -> {
            segments += Segment("Scanning ", Color.Cyan)
            segments += Segment(state.spinnerFrame.toString(), Color.Yellow)
            segments += Segment(" ${shortenPath(state.currentPath, innerWidth - 10)}", Color.Cyan)
            state.loadingProgress?.takeIf { it.totalItems > 0 }?.let { progress ->
                val percent = (progress.completionFraction * 100.0).coerceIn(0.0, 100.0)
                val percentText = formatPercentage(percent)
                segments += Segment(" • ${percentText}% (${progress.processedItems}/${progress.totalItems})", Color.Green)
            }
        }
        state.errorMessage != null -> {
            segments += Segment("Error: ${state.errorMessage.take(innerWidth - 7)}", Color.Red)
        }
        state.scanResult != null -> {
            val seconds = state.scanDurationMs / 1000.0
            val base = "Scan completed in ${formatDuration(seconds)}s"
            segments += Segment(base, Color.Green)
            if (state.warningCount > 0) {
                segments += Segment(" • ${state.warningCount} warning(s)", Color.Yellow)
            }
            state.selectedDirectory?.let {
                val size = SizeFormatter.format(it.totalSize())
                val name = shortenPath(it.name, innerWidth - base.length - 25)
                segments += Segment(" • Selected: $name ($size)", Color.Cyan)
            }
        }
        else -> segments += Segment("Idle", Color.Cyan)
    }
    segments += Segment("  q: Quit", Color.Yellow)
    return frameLine(width, segments)
}
