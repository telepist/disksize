package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.presentation.BrowserItem
import disksize.presentation.BrowserItemKind
import disksize.presentation.ExplorerState
import disksize.presentation.isSubPathOf
import disksize.util.SizeFormatter
import kotlin.math.max
import kotlin.math.min

private const val PROGRESS_BAR_TARGET = 24

internal fun directorySection(state: ExplorerState, width: Int, maxRows: Int): List<FrameLine> {
    if (maxRows <= 0) return emptyList()

    val innerWidth = width - 1
    val lines = mutableListOf<FrameLine>()

    fun remainingCapacity(): Int = maxRows - lines.size
    fun add(line: FrameLine) {
        if (remainingCapacity() > 0) {
            lines += line
        }
    }

    // Directory header line with scan status
    val headerSegments = mutableListOf<Segment>()
    if (state.isScanInProgress) {
        headerSegments += Segment("Scanning ${state.spinnerFrame}", Theme.spinner)
        val progress = state.loadingProgress
        if (progress != null) {
            headerSegments += Segment("  Files:${formatCount(progress.processedFiles)}", Theme.keyHint)
            headerSegments += Segment("  ${SizeFormatter.format(progress.scannedBytes)}", Theme.pathText)
        }
    }
    if (headerSegments.isNotEmpty()) {
        add(frameLine(width, headerSegments))
        if (remainingCapacity() <= 0) return lines
    }

    when {
        state.isLoading -> lines += loadingLines(state, width, innerWidth, remainingCapacity())
        state.errorMessage != null -> {
            val message = state.errorMessage.take(innerWidth - 2)
            add(frameLine(width, listOf(Segment("Error: $message", Theme.statusError))))
        }
        state.browserItems.isEmpty() -> {
            add(frameLine(width, listOf(Segment("(no entries found)", Theme.keyHint))))
        }
        else -> {
            val contentLines = browserLines(
                state = state,
                width = width,
                innerWidth = innerWidth,
                capacity = remainingCapacity()
            )
            lines.addAll(contentLines)
        }
    }

    return lines
}

private fun browserLines(
    state: ExplorerState,
    width: Int,
    innerWidth: Int,
    capacity: Int
): List<FrameLine> {
    if (capacity <= 0) return emptyList()
    val items = state.browserItems
    val totalItems = items.size
    if (totalItems == 0) return emptyList()

    val lines = mutableListOf<FrameLine>()
    fun add(line: FrameLine) {
        if (lines.size < capacity) {
            lines += line
        }
    }

    val selectedIndex = state.selectedIndex.coerceIn(0, totalItems - 1)
    var availableForItems = min(capacity, totalItems)
    var startIndex = 0
    var endIndex = totalItems
    var indicatorTop = false
    var indicatorBottom = false

    if (totalItems > capacity) {
        var indicatorSlots = when {
            capacity >= 3 -> 2
            capacity == 2 -> 1
            else -> 0
        }
        availableForItems = (capacity - indicatorSlots).coerceAtLeast(1)
        startIndex = (selectedIndex - availableForItems / 2).coerceAtLeast(0)
        endIndex = (startIndex + availableForItems).coerceAtMost(totalItems)
        startIndex = (endIndex - availableForItems).coerceAtLeast(0)
        indicatorTop = startIndex > 0 && indicatorSlots > 0
        indicatorBottom = endIndex < totalItems && indicatorSlots > 0

        val usedIndicatorSlots = (if (indicatorTop) 1 else 0) + (if (indicatorBottom) 1 else 0)
        availableForItems = (capacity - usedIndicatorSlots).coerceAtLeast(1)
        startIndex = (selectedIndex - availableForItems / 2).coerceAtLeast(0)
        endIndex = (startIndex + availableForItems).coerceAtMost(totalItems)
        startIndex = (endIndex - availableForItems).coerceAtLeast(0)
        indicatorTop = startIndex > 0 && usedIndicatorSlots > 0
        indicatorBottom = endIndex < totalItems && usedIndicatorSlots > 0
    } else {
        startIndex = 0
        endIndex = totalItems
    }

    if (indicatorTop && lines.size < capacity) {
        add(indicatorLine(width, "\u2191 ${startIndex} more"))
    }

    val loadingDirPath = state.loadingDirectoryPath
    val liveBytes = state.scanningDirLiveBytes

    val depth0ParentAdjustment: Long = run {
        if (loadingDirPath == null) return@run 0L
        for (item in items) {
            if (item.kind == BrowserItemKind.DIRECTORY && item.depth == 0 && !item.isScanned) {
                val isScanning = loadingDirPath == item.node.path || loadingDirPath.isSubPathOf(item.node.path)
                if (isScanning) {
                    val displaySize = liveBytes.coerceAtLeast(item.totalSize)
                    return@run (displaySize - item.totalSize).coerceAtLeast(0L)
                }
            }
        }
        0L
    }

    for (index in startIndex until endIndex) {
        if (lines.size >= capacity) break
        val item = items[index]
        val line = when (item.kind) {
            BrowserItemKind.DIRECTORY -> {
                val isDepth0Scanning = item.isScanning && item.depth == 0
                val displaySize = if (isDepth0Scanning) liveBytes.coerceAtLeast(item.totalSize) else item.totalSize
                val adjustedParentTotal = if (item.depth == 0 && depth0ParentAdjustment > 0L) {
                    item.parentTotalSize + depth0ParentAdjustment
                } else {
                    item.parentTotalSize
                }
                directoryLine(width, item, index == state.selectedIndex, state.spinnerFrame, item.isScanning, displaySize, adjustedParentTotal)
            }
            BrowserItemKind.FILE -> fileLine(width, item, index == state.selectedIndex)
        }
        add(line)
    }

    if (indicatorBottom && lines.size < capacity) {
        add(indicatorLine(width, "\u2193 ${totalItems - endIndex} more"))
    }

    return lines
}

private fun loadingLines(
    state: ExplorerState,
    width: Int,
    innerWidth: Int,
    capacity: Int
): List<FrameLine> {
    if (capacity <= 0) return emptyList()
    val lines = mutableListOf<FrameLine>()

    fun add(line: FrameLine) {
        if (lines.size < capacity) {
            lines += line
        }
    }

    val pathDisplay = shortenPath(state.currentPath, innerWidth - 12)
    add(frameLine(width, listOf(
        Segment("Scanning ", Theme.title),
        Segment(state.spinnerFrame.toString(), Theme.spinner),
        Segment(" $pathDisplay", Theme.pathText)
    )))

    val progress = state.loadingProgress
    if (progress != null) {
        add(frameLine(width, listOf(
            Segment("Files: ", Theme.keyHint),
            Segment(formatCount(progress.processedFiles), Theme.pathText),
            Segment("  Dirs: ", Theme.keyHint),
            Segment(formatCount(progress.processedDirectories), Theme.pathText),
            Segment("  Size: ", Theme.keyHint),
            Segment(SizeFormatter.format(progress.scannedBytes), Theme.statusSuccess)
        )))

        if (progress.bytesPerSecond > 0) {
            add(frameLine(width, listOf(
                Segment("Rate: ", Theme.keyHint),
                Segment("${SizeFormatter.format(progress.bytesPerSecond)}/s", Theme.spinner)
            )))
        }

        val directoryPath = state.loadingDirectoryPath ?: progress.currentDirectory
        if (directoryPath != null) {
            val directoryLabel = shortenPath(directoryPath, (innerWidth - "Current: ".length).coerceAtLeast(0))
            add(frameLine(width, listOf(
                Segment("Current: ", Theme.keyHint),
                Segment(directoryLabel, Theme.pathText)
            )))
        }
    } else {
        add(frameLine(width, listOf(Segment("Starting scan...", Theme.title))))
    }

    return lines
}

private fun directoryLine(width: Int, item: BrowserItem, isSelected: Boolean, spinnerFrame: Char = ' ', isCurrentlyScanning: Boolean = false, displaySize: Long = item.totalSize, parentTotalSizeOverride: Long = item.parentTotalSize): FrameLine {
    val innerWidth = width - 1
    val selector = if (isSelected) "\u25B8" else " "  // ▸
    val node = item.node
    val nameWithType = when {
        node.isSymlink -> "${node.name}@"
        node.isDirectory -> "${node.name}/"
        else -> node.name
    }
    val expandIndicator = when {
        isCurrentlyScanning -> "$spinnerFrame "
        !item.isScanned -> "\u22EF "  // ⋯
        item.isExpanded -> "\u25BE "   // ▾
        node.isDirectory && node.children.isNotEmpty() -> "\u25B8 "  // ▸
        else -> "  "
    }
    val totalParentSize = parentTotalSizeOverride
    val size = SizeFormatter.format(displaySize)
    val percentage = if (totalParentSize > 0) displaySize.toDouble() / totalParentSize * 100 else 0.0
    val percentStr = formatPercentage(percentage)
    val sizeField = size.padStart(8)
    val percentField = "${percentStr}%".padStart(6)

    val selectorStr = "$selector "  // "▸ " or "  "
    val prefixLen = selectorStr.length + item.treePrefix.length + expandIndicator.length
    val rightPartLen = sizeField.length + 1 + percentField.length  // " " between size and percent
    val availableForBar = innerWidth - rightPartLen - 3 - prefixLen
    val barWidth = max(0, min(PROGRESS_BAR_TARGET, availableForBar / 2))
    val availableForLabel = (innerWidth - rightPartLen - barWidth - 3 - prefixLen).coerceAtLeast(0)

    val labelColor = when {
        isCurrentlyScanning -> Theme.spinner
        !item.isScanned -> Theme.dim
        node.isDirectory -> Theme.directoryName
        else -> Theme.fileName
    }
    val sizeColor = sizeColorFor(displaySize)

    val segments = mutableListOf<Segment>()
    segments += Segment(selectorStr, if (isSelected) Theme.barSelected else null)
    if (item.treePrefix.isNotEmpty()) {
        segments += Segment(item.treePrefix, Theme.treeConnector)
    }
    val label = truncateWithEllipsis("$expandIndicator$nameWithType", availableForLabel + expandIndicator.length)
    segments += Segment(label, labelColor)
    segments += Segment(" ")
    segments += Segment(sizeField, sizeColor)
    segments += Segment(" ")
    segments += Segment(percentField, sizeColor)
    if (barWidth > 0) {
        segments += Segment(" ")
        segments += eighthBlockBar(barWidth, percentage, isSelected)
    }

    val bg = if (isSelected) Theme.selectedBg else null
    return frameLine(width, segments, background = bg)
}

private fun fileLine(width: Int, item: BrowserItem, isSelected: Boolean): FrameLine {
    val innerWidth = width - 1
    val selector = if (isSelected) "\u25B8" else " "  // ▸
    val node = item.node
    val name = if (node.isSymlink) "${node.name}@" else node.name
    val size = SizeFormatter.format(item.totalSize)
    val sizeField = size.padStart(8)
    val labelColor = Theme.fileName
    val sizeColor = sizeColorFor(item.totalSize)
    val spacer = "  "
    val selectorStr = "$selector "  // "▸ " or "  "
    val prefixLen = selectorStr.length + item.treePrefix.length + spacer.length

    val rightPartLen = sizeField.length
    val availableForBar = innerWidth - rightPartLen - 4 - prefixLen
    val barWidth = max(0, min(PROGRESS_BAR_TARGET, availableForBar / 2))
    val availableForLabel = (innerWidth - rightPartLen - barWidth - 3 - prefixLen).coerceAtLeast(0)

    val segments = mutableListOf<Segment>()
    segments += Segment(selectorStr, if (isSelected) Theme.barSelected else null)
    if (item.treePrefix.isNotEmpty()) {
        segments += Segment(item.treePrefix, Theme.treeConnector)
    }
    val label = truncateWithEllipsis("$spacer$name", availableForLabel + spacer.length)
    segments += Segment(label, labelColor)
    segments += Segment(" ")
    segments += Segment(sizeField, sizeColor)

    val bg = if (isSelected) Theme.selectedBg else null
    return frameLine(width, segments, background = bg)
}

private fun sizeColorFor(bytes: Long): Color = when {
    bytes >= 1_000_000_000 -> Theme.sizeHuge
    bytes >= 100_000_000 -> Theme.sizeLarge
    bytes >= 10_000_000 -> Theme.sizeMedium
    else -> Theme.sizeSmall
}

/**
 * Renders a usage bar using eighth-block characters for sub-character precision.
 * Characters: ▏▎▍▌▋▊▉█ (1/8 through 8/8)
 */
private fun eighthBlockBar(width: Int, percentage: Double, highlight: Boolean): List<Segment> {
    if (width <= 0) return listOf(Segment(""))
    val eighths = charArrayOf('\u258F', '\u258E', '\u258D', '\u258C', '\u258B', '\u258A', '\u2589', '\u2588')
    // ▏ ▎ ▍ ▌ ▋ ▊ ▉ █
    val totalEighths = (percentage / 100.0 * width * 8).toInt().coerceIn(0, width * 8)
    val fullBlocks = totalEighths / 8
    val remainder = totalEighths % 8

    val fg = if (highlight) Theme.barSelected else Theme.barFilled
    val filledText = buildString {
        repeat(fullBlocks) { append('\u2588') }  // █
        if (remainder > 0 && fullBlocks < width) {
            append(eighths[remainder - 1])
        }
    }
    val used = fullBlocks + (if (remainder > 0 && fullBlocks < width) 1 else 0)
    val emptyCount = (width - used).coerceAtLeast(0)

    val segments = mutableListOf<Segment>()
    if (filledText.isNotEmpty()) {
        segments += Segment(filledText, fg, Theme.barEmpty)
    }
    if (emptyCount > 0) {
        segments += Segment("\u2591".repeat(emptyCount), Theme.barEmpty)  // ░
    }
    return segments
}

private fun indicatorLine(width: Int, message: String): FrameLine =
    frameLine(width, listOf(Segment(message, Theme.keyHint)))

internal fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${(count / 100_000) / 10.0}M"
    count >= 1_000 -> "${(count / 100) / 10.0}K"
    else -> count.toString()
}
