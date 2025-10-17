package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.presentation.ExplorerState

internal fun buildScreenLines(state: ExplorerState, width: Int, rows: Int): List<FrameLine> {
    val lines = mutableListOf<FrameLine>()
    lines += topBorder(width)
    lines += headerTitleLine(width)
    lines += middleBorder(width)
    lines += pathLine(state, width)
    lines += blankLine(width)
    lines += statsSection(state, width)
    lines += blankLine(width)

    val reservedForStatus = 3
    val targetVisibleRows = (rows - 1).coerceAtLeast(0)
    val spaceForDirectory = (targetVisibleRows - lines.size).coerceAtLeast(0)
    lines += directorySection(state, width, spaceForDirectory)

    val fillerCount = targetVisibleRows - (lines.size + reservedForStatus)
    if (fillerCount > 0) {
        repeat(fillerCount) { lines += blankLine(width) }
    }

    lines += middleBorder(width)
    lines += statusLine(state, width)
    lines += bottomBorder(width)
    return lines
}

private fun headerTitleLine(width: Int): FrameLine =
    frameLineCentered(width, "DiskSize - Disk Space Analyzer", Color.Cyan)
