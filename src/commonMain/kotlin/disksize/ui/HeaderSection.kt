package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.presentation.ExplorerState

internal fun pathLine(state: ExplorerState, width: Int): FrameLine =
    frameLine(
        width,
        listOf(
            Segment("Path: ", Color.Cyan),
            Segment(shortenPath(state.currentPath, width - 10), Color.White)
        )
    )
