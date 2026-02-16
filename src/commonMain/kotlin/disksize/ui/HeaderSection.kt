package disksize.ui

import disksize.presentation.ExplorerState

internal fun headerLine(state: ExplorerState, width: Int): FrameLine {
    val innerWidth = width - 1
    val appName = "disksize"
    val sep = " \u2500\u2500\u2500 "  // " ─── "
    val prefixLen = appName.length + sep.length
    val pathMaxLen = (innerWidth - prefixLen).coerceAtLeast(10)
    val path = shortenPath(state.currentPath, pathMaxLen)

    return frameLine(
        width,
        listOf(
            Segment(appName, Theme.title),
            Segment(sep, Theme.separator),
            Segment(path, Theme.pathText)
        )
    )
}
