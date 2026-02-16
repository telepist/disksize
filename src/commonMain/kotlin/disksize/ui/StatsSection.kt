package disksize.ui

import disksize.presentation.ExplorerState
import disksize.util.SizeFormatter

internal fun statsLine(state: ExplorerState, width: Int): FrameLine {
    val innerWidth = width - 1
    val totalSize = state.scanResult?.let { SizeFormatter.format(it.totalSize) } ?: "--"
    val fileCount = state.scanResult?.fileCount?.let { formatNumber(it) } ?: "--"
    val directoryCount = state.scanResult?.directoryCount?.let { formatNumber(it) } ?: "--"

    val sortLabel = "Sort: ${state.sortOrder.label}"

    val segments = mutableListOf<Segment>()
    segments += Segment(totalSize, Theme.pathText)
    segments += Segment(" \u00B7 ", Theme.separator)  // middle dot
    segments += Segment("$fileCount files", Theme.pathText)
    segments += Segment(" \u00B7 ", Theme.separator)
    segments += Segment("$directoryCount dirs", Theme.pathText)

    val leftLen = segments.sumOf { it.text.length }
    val padding = (innerWidth - leftLen - sortLabel.length).coerceAtLeast(1)
    segments += Segment(" ".repeat(padding))
    segments += Segment(sortLabel, Theme.keyHint)

    return frameLine(width, segments)
}

private fun formatNumber(n: Int): String {
    if (n < 1_000) return n.toString()
    val str = n.toString()
    val result = StringBuilder()
    var count = 0
    for (i in str.lastIndex downTo 0) {
        if (count > 0 && count % 3 == 0) result.insert(0, ',')
        result.insert(0, str[i])
        count++
    }
    return result.toString()
}
