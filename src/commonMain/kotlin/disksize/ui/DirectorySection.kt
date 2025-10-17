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

    add(frameLine(width, listOf(Segment("Entries (Sort: ${state.sortOrder.label})", Color.Cyan))))
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

    val totalDirectorySize = state.childDirectoryTotalSize.coerceAtLeast(1L)
    for (index in startIndex until endIndex) {
        if (lines.size >= capacity) break
        val item = items[index]
        val line = when (item.kind) {
            BrowserItemKind.DIRECTORY -> directoryLine(width, item, totalDirectorySize, index == state.selectedIndex)
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
        if (progress.totalItems > 0) {
            val percent = (progress.completionFraction * 100.0).coerceIn(0.0, 100.0)
            val percentText = formatPercentage(percent)
            val statusText = " ${progress.processedItems}/${progress.totalItems} (${percentText}%)"
            val label = "Progress: "
            val availableForBar = (innerWidth - label.length - statusText.length).coerceAtLeast(0)
            val progressSegments = mutableListOf<Segment>()
            progressSegments += Segment(label, Color.Cyan)
            if (availableForBar >= 6) {
                progressSegments += determinateProgressSegment(progress.completionFraction, availableForBar)
                progressSegments += Segment(statusText, Color.Green)
            } else {
                progressSegments += Segment(statusText.trim(), Color.Green)
            }
            add(frameLine(width, progressSegments))
        }
        add(frameLine(width, listOf(Segment(progressCountsLabel(progress), Color.Cyan))))
        val directoryPath = state.loadingDirectoryPath
        val directoryLabel = directoryPath?.let { shortenPath(it, (innerWidth - "Directory: ".length).coerceAtLeast(0)) } ?: "--"
        add(frameLine(width, listOf(
            Segment("Directory: ", Color.Cyan),
            Segment(directoryLabel, Color.White)
        )))
    } else {
        add(frameLine(width, listOf(Segment("Preparing directory statistics...", Color.Cyan))))
        add(frameLine(width, listOf(
            Segment("Directory: ", Color.Cyan),
            Segment("--", Color.White)
        )))
    }

    add(frameLine(width, listOf(Segment("Collecting directory statistics...", Color.Cyan))))
    return lines
}

private fun progressCountsLabel(progress: LoadingProgress): String =
    buildString {
        append("Files ")
        if (progress.totalFiles > 0) {
            append("${progress.processedFiles}/${progress.totalFiles}")
        } else {
            append(progress.processedFiles)
        }
        append(" • Directories ")
        if (progress.totalDirectories > 0) {
            append("${progress.processedDirectories}/${progress.totalDirectories}")
        } else {
            append(progress.processedDirectories)
        }
    }

private fun directoryLine(width: Int, item: BrowserItem, totalParentSize: Long, isSelected: Boolean): FrameLine {
    val innerWidth = width - 2
    val selector = if (isSelected) ">" else " "
    val node = item.node
    val nameWithType = if (node.isDirectory) "${node.name}/" else node.name
    val size = SizeFormatter.format(item.totalSize)
    val percentage = if (totalParentSize > 0) item.totalSize.toDouble() / totalParentSize * 100 else 0.0
    val percentStr = formatPercentage(percentage)
    val sizePart = "$size (${percentStr}%)"

    val availableForBar = innerWidth - sizePart.length - 4
    val barWidth = max(0, min(PROGRESS_BAR_TARGET, availableForBar / 2))
    val availableForLabel = (innerWidth - sizePart.length - barWidth - 3).coerceAtLeast(0)
    val labelColor = when {
        isSelected -> Color.Green
        node.isDirectory -> Color.Cyan
        else -> Color.White
    }
    val sizeColor = when {
        item.totalSize >= 1_000_000_000 -> Color.Red
        item.totalSize >= 100_000_000 -> Color.Yellow
        item.totalSize >= 10_000_000 -> Color.Cyan
        else -> Color.White
    }

    val segments = mutableListOf<Segment>()
    val label = truncateWithEllipsis("$selector $nameWithType", availableForLabel)
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
    val name = item.node.name
    val size = SizeFormatter.format(item.totalSize)
    val labelColor = if (isSelected) Color.Green else Color.White
    val sizeColor = when {
        item.totalSize >= 1_000_000_000 -> Color.Red
        item.totalSize >= 100_000_000 -> Color.Yellow
        item.totalSize >= 10_000_000 -> Color.Cyan
        else -> Color.White
    }

    val sizePart = size
    val availableForLabel = (innerWidth - sizePart.length - 3).coerceAtLeast(0)
    val label = truncateWithEllipsis("$selector $name", availableForLabel)

    val segments = mutableListOf<Segment>()
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
