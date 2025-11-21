package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.presentation.BrowserItem
import disksize.presentation.BrowserItemKind
import disksize.util.SizeFormatter

/**
 * Renders a progress dialog while deletion is in progress.
 *
 * @param item The item being deleted
 * @param screenWidth The total screen width
 * @param screenHeight The total screen height
 * @param spinnerFrame The current spinner frame character
 * @return List of FrameLines for the centered dialog
 */
internal fun deletingProgressDialog(
    item: BrowserItem,
    screenWidth: Int,
    screenHeight: Int,
    spinnerFrame: Char
): List<FrameLine> {
    // Ensure dialog fits within screen, with minimum of 20 for very small terminals
    val dialogWidth = (screenWidth * 0.7).toInt().coerceIn(20, minOf(80, screenWidth))
    val itemType = if (item.kind == BrowserItemKind.DIRECTORY) "directory" else "file"
    val itemName = item.node.name
    val itemSize = SizeFormatter.format(item.totalSize)

    // Build dialog lines
    val dialogLines = mutableListOf<FrameLine>()
    dialogLines += topBorder(dialogWidth)
    dialogLines += frameLineCentered(dialogWidth, "⏳ DELETING ⏳", Color.Yellow)
    dialogLines += blankLine(dialogWidth)
    dialogLines += frameLine(dialogWidth, listOf(
        Segment("Deleting $itemType: ", Color.White),
        Segment(truncateWithEllipsis(itemName, dialogWidth - 20), Color.Yellow)
    ))
    dialogLines += frameLine(dialogWidth, listOf(
        Segment("Size: ", Color.White),
        Segment(itemSize, Color.Cyan)
    ))
    dialogLines += blankLine(dialogWidth)
    dialogLines += frameLineCentered(dialogWidth, "$spinnerFrame Please wait...", Color.Cyan)
    dialogLines += blankLine(dialogWidth)
    dialogLines += bottomBorder(dialogWidth)

    // Center the dialog on screen
    val dialogHeight = dialogLines.size
    val verticalPadding = ((screenHeight - dialogHeight) / 2).coerceAtLeast(0)
    val horizontalPadding = ((screenWidth - dialogWidth) / 2).coerceAtLeast(0)

    // Create padded lines
    val paddedLines = mutableListOf<FrameLine>()

    // Top padding
    repeat(verticalPadding) {
        paddedLines += FrameLine(listOf(Segment(" ".repeat(screenWidth.coerceAtLeast(0)))))
    }

    // Dialog lines with horizontal centering
    dialogLines.forEach { line ->
        val leftPad = " ".repeat(horizontalPadding.coerceAtLeast(0))
        val rightPadWidth = (screenWidth - dialogWidth - horizontalPadding).coerceAtLeast(0)
        val rightPad = " ".repeat(rightPadWidth)
        paddedLines += FrameLine(
            listOf(Segment(leftPad)) + line.segments + listOf(Segment(rightPad))
        )
    }

    // Bottom padding
    val remainingLines = screenHeight - paddedLines.size
    repeat(remainingLines.coerceAtLeast(0)) {
        paddedLines += FrameLine(listOf(Segment(" ".repeat(screenWidth.coerceAtLeast(0)))))
    }

    return paddedLines
}

/**
 * Renders a confirmation dialog for file/directory deletion.
 *
 * @param item The item to be deleted
 * @param screenWidth The total screen width
 * @param screenHeight The total screen height
 * @return List of FrameLines for the centered dialog
 */
internal fun confirmationDialog(
    item: BrowserItem,
    screenWidth: Int,
    screenHeight: Int
): List<FrameLine> {
    // Ensure dialog fits within screen, with minimum of 20 for very small terminals
    val dialogWidth = (screenWidth * 0.7).toInt().coerceIn(20, minOf(80, screenWidth))
    val itemType = if (item.kind == BrowserItemKind.DIRECTORY) "directory" else "file"
    val itemName = item.node.name
    val itemSize = SizeFormatter.format(item.totalSize)

    val warningText = if (item.kind == BrowserItemKind.DIRECTORY) {
        "This will permanently delete this directory and ALL its contents!"
    } else {
        "This will permanently delete this file!"
    }

    // Build dialog lines
    val dialogLines = mutableListOf<FrameLine>()
    dialogLines += topBorder(dialogWidth)
    dialogLines += frameLineCentered(dialogWidth, "⚠ DELETE CONFIRMATION ⚠", Color.Red)
    dialogLines += blankLine(dialogWidth)
    dialogLines += frameLine(dialogWidth, listOf(
        Segment("Delete $itemType: ", Color.White),
        Segment(truncateWithEllipsis(itemName, dialogWidth - 18), Color.Yellow)
    ))
    dialogLines += frameLine(dialogWidth, listOf(
        Segment("Size: ", Color.White),
        Segment(itemSize, Color.Cyan)
    ))
    dialogLines += blankLine(dialogWidth)
    dialogLines += frameLine(dialogWidth, listOf(Segment(warningText, Color.Red)))
    dialogLines += blankLine(dialogWidth)
    dialogLines += frameLineCentered(dialogWidth, "Press 'y' to confirm or 'n' to cancel", Color.White)
    dialogLines += bottomBorder(dialogWidth)

    // Center the dialog on screen
    val dialogHeight = dialogLines.size
    val verticalPadding = ((screenHeight - dialogHeight) / 2).coerceAtLeast(0)
    val horizontalPadding = ((screenWidth - dialogWidth) / 2).coerceAtLeast(0)

    // Create padded lines
    val paddedLines = mutableListOf<FrameLine>()

    // Top padding
    repeat(verticalPadding) {
        paddedLines += FrameLine(listOf(Segment(" ".repeat(screenWidth.coerceAtLeast(0)))))
    }

    // Dialog lines with horizontal centering
    dialogLines.forEach { line ->
        val leftPad = " ".repeat(horizontalPadding.coerceAtLeast(0))
        val rightPadWidth = (screenWidth - dialogWidth - horizontalPadding).coerceAtLeast(0)
        val rightPad = " ".repeat(rightPadWidth)
        paddedLines += FrameLine(
            listOf(Segment(leftPad)) + line.segments + listOf(Segment(rightPad))
        )
    }

    // Bottom padding
    val remainingLines = screenHeight - paddedLines.size
    repeat(remainingLines.coerceAtLeast(0)) {
        paddedLines += FrameLine(listOf(Segment(" ".repeat(screenWidth.coerceAtLeast(0)))))
    }

    return paddedLines
}
