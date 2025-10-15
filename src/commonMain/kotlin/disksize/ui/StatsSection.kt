package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.presentation.ExplorerState
import disksize.util.SizeFormatter

internal fun statsSection(state: ExplorerState, width: Int): List<FrameLine> {
    val totalSize = state.scanResult?.let { SizeFormatter.format(it.totalSize) } ?: "--"
    val fileCount = state.scanResult?.fileCount?.toString() ?: "--"
    val directoryCount = state.scanResult?.directoryCount?.toString() ?: "--"
    return listOf(
        frameLine(width, listOf(Segment("Total Size: ", Color.Cyan), Segment(totalSize, Color.Green))),
        frameLine(width, listOf(Segment("Files: ", Color.Cyan), Segment(fileCount, Color.Green))),
        frameLine(width, listOf(Segment("Directories: ", Color.Cyan), Segment(directoryCount, Color.Green)))
    )
}
