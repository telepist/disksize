package disksize.ui

import disksize.presentation.ExplorerState
import disksize.util.SizeFormatter

internal fun statusLine(state: ExplorerState, width: Int): FrameLine {
    val innerWidth = width - 1

    val leftSegments = mutableListOf<Segment>()
    val rightSegments = mutableListOf<Segment>()

    // Left side: status
    when {
        state.isLoading -> {
            leftSegments += Segment("Scanning ", Theme.title)
            leftSegments += Segment(state.spinnerFrame.toString(), Theme.spinner)
            val elapsedText = state.scanStartTimeMark?.let { mark ->
                val elapsedSeconds = mark.elapsedNow().inWholeSeconds
                " ${elapsedSeconds}s"
            } ?: ""
            leftSegments += Segment(elapsedText, Theme.spinner)
            val progress = state.loadingProgress
            if (progress != null) {
                leftSegments += Segment(" Files:${formatCount(progress.processedFiles)}", Theme.keyHint)
                leftSegments += Segment(" ${SizeFormatter.format(progress.scannedBytes)}", Theme.pathText)
            }
        }
        state.isScanInProgress -> {
            leftSegments += Segment("Scanning ", Theme.title)
            leftSegments += Segment(state.spinnerFrame.toString(), Theme.spinner)
            val elapsedText = state.scanStartTimeMark?.let { mark ->
                val elapsedSeconds = mark.elapsedNow().inWholeSeconds
                " ${elapsedSeconds}s"
            } ?: ""
            leftSegments += Segment(elapsedText, Theme.spinner)
            val progress = state.loadingProgress
            if (progress != null) {
                leftSegments += Segment(" Files:${formatCount(progress.processedFiles)}", Theme.keyHint)
                leftSegments += Segment(" ${SizeFormatter.format(progress.scannedBytes)}", Theme.pathText)
            }
        }
        state.isRefreshing -> {
            val refreshName = state.refreshingPath?.let { it.substringAfterLast('/').substringAfterLast('\\') }
                ?.takeIf { it.isNotEmpty() }
                ?: state.refreshingPath
                ?: ""
            leftSegments += Segment("Refreshing ", Theme.title)
            if (refreshName.isNotEmpty()) {
                leftSegments += Segment(refreshName, Theme.pathText)
                leftSegments += Segment(" ", Theme.title)
            }
            leftSegments += Segment(state.spinnerFrame.toString(), Theme.spinner)
        }
        state.errorMessage != null && state.scanResult == null -> {
            leftSegments += Segment("Error: ${state.errorMessage.take(innerWidth / 2)}", Theme.statusError)
        }
        state.errorMessage != null && state.scanResult != null -> {
            leftSegments += Segment("Error: ${state.errorMessage.take(innerWidth / 2)}", Theme.statusError)
        }
        state.scanResult != null -> {
            val seconds = state.scanDurationMs / 1000.0
            leftSegments += Segment("\u2713 ", Theme.statusSuccess)  // ✓
            leftSegments += Segment("${formatDuration(seconds)}s", Theme.statusSuccess)
            if (state.warningCount > 0) {
                leftSegments += Segment(" \u00B7 ", Theme.separator)  // ·
                leftSegments += Segment("${state.warningCount} warnings", Theme.statusWarning)
            }
        }
        else -> leftSegments += Segment("Idle", Theme.keyHint)
    }

    // Right side: key hints
    if (state.isLoading && state.browserItems.isEmpty()) {
        rightSegments += keyHint("q", "Quit")
    } else {
        rightSegments += keyHint("\u2191\u2193", "Navigate")  // ↑↓
        rightSegments += Segment("  ")
        rightSegments += keyHint("Enter", "Open")
        rightSegments += Segment("  ")
        rightSegments += keyHint("s", "Sort")
        rightSegments += Segment("  ")
        if (!state.isAnyScanActive) {
            rightSegments += keyHint("r", "Refresh")
            rightSegments += Segment("/", Theme.separator)
            rightSegments += keyHint("R", "All")
            rightSegments += Segment("  ")
        }
        rightSegments += keyHint("Del", "Delete")
        rightSegments += Segment("  ")
        rightSegments += keyHint("q", "Quit")
    }

    val leftLen = leftSegments.sumOf { it.text.length }
    val rightLen = rightSegments.sumOf { it.text.length }
    val padding = (innerWidth - leftLen - rightLen).coerceAtLeast(1)

    val allSegments = mutableListOf<Segment>()
    allSegments.addAll(leftSegments)
    allSegments += Segment(" ".repeat(padding))
    allSegments.addAll(rightSegments)

    return frameLine(width, allSegments)
}

private fun keyHint(key: String, desc: String): List<Segment> = listOf(
    Segment(key, Theme.keyLabel),
    Segment(" $desc", Theme.keyHint)
)

private operator fun MutableList<Segment>.plusAssign(segments: List<Segment>) {
    addAll(segments)
}
