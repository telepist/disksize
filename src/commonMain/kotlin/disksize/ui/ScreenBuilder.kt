package disksize.ui

import disksize.presentation.ExplorerState

internal fun buildScreenLines(state: ExplorerState, width: Int, rows: Int): List<FrameLine> {
    // If deletion is in progress, show the progress dialog
    state.confirmDeleteItem?.let { item ->
        if (state.isDeletingInProgress) {
            return deletingProgressDialog(item, width, rows, state.spinnerFrame)
        } else {
            // Show confirmation dialog
            return confirmationDialog(item, width, rows)
        }
    }

    val lines = mutableListOf<FrameLine>()
    lines += headerLine(state, width)        // 1 line
    lines += statsLine(state, width)         // 1 line
    lines += horizontalRule(width)           // 1 line

    val reservedForFooter = 2  // horizontalRule + statusLine
    val targetVisibleRows = (rows - 1).coerceAtLeast(0)
    val spaceForDirectory = (targetVisibleRows - lines.size - reservedForFooter).coerceAtLeast(0)
    lines += directorySection(state, width, spaceForDirectory)

    val fillerCount = targetVisibleRows - (lines.size + reservedForFooter)
    if (fillerCount > 0) {
        repeat(fillerCount) { lines += blankLine(width) }
    }

    lines += horizontalRule(width)           // 1 line
    lines += statusLine(state, width)        // 1 line
    return lines
}
