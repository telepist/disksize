package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.presentation.BrowserItem
import disksize.presentation.BrowserItemKind
import disksize.presentation.ExplorerState
import disksize.presentation.LoadingProgress
import disksize.util.SizeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PROGRESS_BAR_TARGET = 24
private val DIM_COLOR = Color(100, 100, 100)

internal fun directorySection(state: ExplorerState, width: Int, maxRows: Int): List<FrameLine> {
    if (maxRows <= 0) return emptyList()

    val innerWidth = width - 2
    val lines = mutableListOf<FrameLine>()

    fun remainingCapacity(): Int = maxRows - lines.size
    fun add(line: FrameLine) {
        if (remainingCapacity() > 0) {
            lines += line
        }
    }

    val headerSegments = mutableListOf(Segment("Entries (Sort: ${state.sortOrder.label})", Color.Cyan))
    if (state.isScanInProgress) {
        headerSegments += Segment(" │ Scanning ${state.spinnerFrame}", Color.Yellow)
    }
    add(frameLine(width, headerSegments))
    if (remainingCapacity() <= 0) return lines

    when {
        state.isLoading -> lines += loadingLines(state, width, innerWidth, remainingCapacity())
        state.errorMessage != null -> {
            val message = state.errorMessage.take(innerWidth - 2)
            add(frameLine(width, listOf(Segment("Error: $message", Color.Red))))
        }
        state.browserItems.isEmpty() -> {
            add(frameLine(width, listOf(Segment("(no entries found)", Color.Cyan))))
        }
        else -> {
            val contentLines = browserLines(
                state = state,
                width = width,
                innerWidth = innerWidth,
                capacity = remainingCapacity()
            )
            lines.addAll(contentLines)
            if (state.warningCount > 0 && remainingCapacity() > 0) {
                add(frameLine(width, listOf(Segment("Warnings: ${state.warningCount} item(s) skipped", Color.Yellow))))
            }
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
        add(indicatorLine(width, "↑ ${startIndex} more"))
    }

    val loadingDirPath = state.loadingDirectoryPath
    val liveBytes = state.scanningDirLiveBytes

    // Pre-compute how much extra size the currently-scanning directory adds beyond
    // what the tree knows about, so we can adjust parentTotalSize for all depth-0
    // siblings and keep percentages consistent.
    val depth0ParentAdjustment: Long = run {
        if (loadingDirPath == null) return@run 0L
        for (item in items) {
            if (item.kind == BrowserItemKind.DIRECTORY && item.depth == 0 && !item.isScanned) {
                val isScanning = loadingDirPath == item.node.path || loadingDirPath.startsWith("${item.node.path}/")
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
                // Use live bytes only for the depth-0 directory being scanned
                // (tracks total progress bytes). Nested dirs get accurate sizes
                // from the partial tree emissions via the scan callback.
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
        add(indicatorLine(width, "↓ ${totalItems - endIndex} more"))
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
        Segment("Scanning ", Color.Cyan),
        Segment(state.spinnerFrame.toString(), Color.Yellow),
        Segment(" $pathDisplay", Color.Cyan)
    )))

    val progress = state.loadingProgress
    if (progress != null) {
        // Show scan stats
        add(frameLine(width, listOf(
            Segment("Files: ", Color.Cyan),
            Segment(formatCount(progress.processedFiles), Color.White),
            Segment("  Dirs: ", Color.Cyan),
            Segment(formatCount(progress.processedDirectories), Color.White),
            Segment("  Size: ", Color.Cyan),
            Segment(SizeFormatter.format(progress.scannedBytes), Color.Green)
        )))

        // Show throughput if available
        if (progress.bytesPerSecond > 0) {
            add(frameLine(width, listOf(
                Segment("Rate: ", Color.Cyan),
                Segment("${SizeFormatter.format(progress.bytesPerSecond)}/s", Color.Yellow)
            )))
        }

        // Show current directory being scanned
        val directoryPath = state.loadingDirectoryPath ?: progress.currentDirectory
        if (directoryPath != null) {
            val directoryLabel = shortenPath(directoryPath, (innerWidth - "Current: ".length).coerceAtLeast(0))
            add(frameLine(width, listOf(
                Segment("Current: ", Color.Cyan),
                Segment(directoryLabel, Color.White)
            )))
        }
    } else {
        add(frameLine(width, listOf(Segment("Starting scan...", Color.Cyan))))
    }

    return lines
}

private fun directoryLine(width: Int, item: BrowserItem, isSelected: Boolean, spinnerFrame: Char = ' ', isCurrentlyScanning: Boolean = false, displaySize: Long = item.totalSize, parentTotalSizeOverride: Long = item.parentTotalSize): FrameLine {
    val innerWidth = width - 2
    val selector = if (isSelected) ">" else " "
    val node = item.node
    val nameWithType = when {
        node.isSymlink -> "${node.name}@"
        node.isDirectory -> "${node.name}/"
        else -> node.name
    }
    val expandIndicator = when {
        isCurrentlyScanning -> "$spinnerFrame "
        !item.isScanned -> "⋯ "
        item.isExpanded -> "▾ "
        node.isDirectory && node.children.isNotEmpty() -> "▸ "
        else -> "  "
    }
    val totalParentSize = parentTotalSizeOverride
    val size = SizeFormatter.format(displaySize)
    val percentage = if (totalParentSize > 0) displaySize.toDouble() / totalParentSize * 100 else 0.0
    val percentStr = formatPercentage(percentage)
    val sizePart = "$size (${percentStr}%)"

    val prefixLen = item.treePrefix.length + expandIndicator.length
    val availableForBar = innerWidth - sizePart.length - 4 - prefixLen
    val barWidth = max(0, min(PROGRESS_BAR_TARGET, availableForBar / 2))
    val availableForLabel = (innerWidth - sizePart.length - barWidth - 3 - prefixLen).coerceAtLeast(0)
    val labelColor = when {
        isSelected -> Color.Green
        isCurrentlyScanning -> Color.Yellow
        !item.isScanned -> DIM_COLOR
        node.isDirectory -> Color.Cyan
        else -> Color.White
    }
    val sizeColor = when {
        displaySize >= 1_000_000_000 -> Color.Red
        displaySize >= 100_000_000 -> Color.Yellow
        displaySize >= 10_000_000 -> Color.Cyan
        else -> Color.White
    }
    val prefixColor = if (isSelected) Color.Green else Color.Cyan

    val segments = mutableListOf<Segment>()
    if (item.treePrefix.isNotEmpty()) {
        segments += Segment(item.treePrefix, prefixColor)
    }
    val label = truncateWithEllipsis("$selector $expandIndicator$nameWithType", availableForLabel + expandIndicator.length + 2)
    segments += Segment(label, labelColor)
    segments += Segment(" ")
    segments += Segment(sizePart, sizeColor)
    if (barWidth > 0) {
        segments += Segment(" ")
        segments += usageBarSegment(barWidth, percentage, isSelected)
    }

    return frameLine(width, segments)
}

private fun fileLine(width: Int, item: BrowserItem, isSelected: Boolean): FrameLine {
    val innerWidth = width - 2
    val selector = if (isSelected) ">" else " "
    val node = item.node
    val name = if (node.isSymlink) "${node.name}@" else node.name
    val size = SizeFormatter.format(item.totalSize)
    val labelColor = if (isSelected) Color.Green else Color.White
    val sizeColor = when {
        item.totalSize >= 1_000_000_000 -> Color.Red
        item.totalSize >= 100_000_000 -> Color.Yellow
        item.totalSize >= 10_000_000 -> Color.Cyan
        else -> Color.White
    }
    val prefixColor = if (isSelected) Color.Green else Color.Cyan
    // "  " spacer aligns with the expand indicator on directory lines
    val spacer = "  "
    val prefixLen = item.treePrefix.length + spacer.length

    val sizePart = size
    val availableForLabel = (innerWidth - sizePart.length - 3 - prefixLen).coerceAtLeast(0)

    val segments = mutableListOf<Segment>()
    if (item.treePrefix.isNotEmpty()) {
        segments += Segment(item.treePrefix, prefixColor)
    }
    val label = truncateWithEllipsis("$selector $spacer$name", availableForLabel + spacer.length + 2)
    segments += Segment(label, labelColor)
    segments += Segment(" ")
    segments += Segment(sizePart, sizeColor)

    return frameLine(width, segments)
}

private fun usageBarSegment(width: Int, percentage: Double, highlight: Boolean): Segment {
    if (width <= 0) return Segment("")
    val filled = (percentage / 100.0 * width).roundToInt().coerceIn(0, width)
    val fillChar = if (highlight) '█' else '▓'
    val emptyChar = '░'
    val text = buildString {
        repeat(filled) { append(fillChar) }
        repeat(width - filled) { append(emptyChar) }
    }
    val color = if (highlight) Color.Green else Color.Magenta
    return Segment(text, color)
}

private fun indicatorLine(width: Int, message: String): FrameLine =
    frameLine(width, listOf(Segment(message, Color.Cyan)))

internal fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${(count / 100_000) / 10.0}M"
    count >= 1_000 -> "${(count / 100) / 10.0}K"
    else -> count.toString()
}
