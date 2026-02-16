package disksize.ui

import disksize.presentation.BrowserItem
import disksize.presentation.BrowserItemKind
import disksize.util.SizeFormatter

internal fun deletingProgressDialog(
    item: BrowserItem,
    screenWidth: Int,
    screenHeight: Int,
    spinnerFrame: Char
): List<FrameLine> {
    val dialogWidth = (screenWidth * 0.7).toInt().coerceIn(20, minOf(80, screenWidth))
    val itemType = if (item.kind == BrowserItemKind.DIRECTORY) "directory" else "file"
    val itemName = item.node.name
    val itemSize = SizeFormatter.format(item.totalSize)

    val dialogLines = mutableListOf<FrameLine>()
    dialogLines += dialogTop(dialogWidth)
    dialogLines += dialogFrameCentered(dialogWidth, "DELETING", Theme.spinner)
    dialogLines += dialogBlankLine(dialogWidth)
    dialogLines += dialogFrame(dialogWidth, listOf(
        Segment("Deleting $itemType: ", Theme.pathText),
        Segment(truncateWithEllipsis(itemName, dialogWidth - 20), Theme.spinner)
    ))
    dialogLines += dialogFrame(dialogWidth, listOf(
        Segment("Size: ", Theme.pathText),
        Segment(itemSize, Theme.title)
    ))
    dialogLines += dialogBlankLine(dialogWidth)
    dialogLines += dialogFrameCentered(dialogWidth, "$spinnerFrame Please wait...", Theme.title)
    dialogLines += dialogBlankLine(dialogWidth)
    dialogLines += dialogBottom(dialogWidth)

    return centerDialog(dialogLines, dialogWidth, screenWidth, screenHeight)
}

internal fun confirmationDialog(
    item: BrowserItem,
    screenWidth: Int,
    screenHeight: Int
): List<FrameLine> {
    val dialogWidth = (screenWidth * 0.7).toInt().coerceIn(20, minOf(80, screenWidth))
    val itemType = if (item.kind == BrowserItemKind.DIRECTORY) "directory" else "file"
    val itemName = item.node.name
    val itemSize = SizeFormatter.format(item.totalSize)

    val warningText = if (item.kind == BrowserItemKind.DIRECTORY) {
        "This will permanently delete this directory and ALL its contents!"
    } else {
        "This will permanently delete this file!"
    }

    val dialogLines = mutableListOf<FrameLine>()
    dialogLines += dialogTop(dialogWidth)
    dialogLines += dialogFrameCentered(dialogWidth, "DELETE CONFIRMATION", Theme.statusError)
    dialogLines += dialogBlankLine(dialogWidth)
    dialogLines += dialogFrame(dialogWidth, listOf(
        Segment("Delete $itemType: ", Theme.pathText),
        Segment(truncateWithEllipsis(itemName, dialogWidth - 18), Theme.spinner)
    ))
    dialogLines += dialogFrame(dialogWidth, listOf(
        Segment("Size: ", Theme.pathText),
        Segment(itemSize, Theme.title)
    ))
    dialogLines += dialogBlankLine(dialogWidth)
    dialogLines += dialogFrame(dialogWidth, listOf(Segment(warningText, Theme.statusError)))
    dialogLines += dialogBlankLine(dialogWidth)
    dialogLines += dialogFrameCentered(dialogWidth, "Press 'y' to confirm or 'n' to cancel", Theme.pathText)
    dialogLines += dialogBottom(dialogWidth)

    return centerDialog(dialogLines, dialogWidth, screenWidth, screenHeight)
}

private fun centerDialog(
    dialogLines: List<FrameLine>,
    dialogWidth: Int,
    screenWidth: Int,
    screenHeight: Int
): List<FrameLine> {
    val dialogHeight = dialogLines.size
    val verticalPadding = ((screenHeight - dialogHeight) / 2).coerceAtLeast(0)
    val horizontalPadding = ((screenWidth - dialogWidth) / 2).coerceAtLeast(0)

    val paddedLines = mutableListOf<FrameLine>()

    repeat(verticalPadding) {
        paddedLines += FrameLine(listOf(Segment(" ".repeat(screenWidth.coerceAtLeast(0)))))
    }

    dialogLines.forEach { line ->
        val leftPad = " ".repeat(horizontalPadding.coerceAtLeast(0))
        val rightPadWidth = (screenWidth - dialogWidth - horizontalPadding).coerceAtLeast(0)
        val rightPad = " ".repeat(rightPadWidth)
        paddedLines += FrameLine(
            listOf(Segment(leftPad)) + line.segments + listOf(Segment(rightPad))
        )
    }

    val remainingLines = screenHeight - paddedLines.size
    repeat(remainingLines.coerceAtLeast(0)) {
        paddedLines += FrameLine(listOf(Segment(" ".repeat(screenWidth.coerceAtLeast(0)))))
    }

    return paddedLines
}
