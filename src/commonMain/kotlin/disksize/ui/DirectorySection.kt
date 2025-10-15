package disksize.ui

import com.jakewharton.mosaic.ui.Color
import disksize.domain.model.FileNode
import disksize.presentation.ExplorerState
import disksize.presentation.LoadingProgress
import disksize.util.SizeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PROGRESS_BAR_TARGET = 24

internal fun directorySection(state: ExplorerState, width: Int): List<FrameLine> {
    val innerWidth = width - 2
    val lines = mutableListOf<FrameLine>()
    lines += frameLine(width, listOf(Segment("Directories (Sort: ${state.sortOrder.label})", Color.Cyan)))

    when {
        state.isLoading -> lines += loadingLines(state, width, innerWidth)
        state.errorMessage != null -> {
            val message = state.errorMessage.take(innerWidth - 2)
            lines += frameLine(width, listOf(Segment("Error: $message", Color.Red)))
        }
        state.directories.isEmpty() -> {
            lines += frameLine(width, listOf(Segment("(no subdirectories found)", Color.Cyan)))
        }
        else -> {
            val totalSize = state.directories.sumOf { it.totalSize() }.coerceAtLeast(1L)
            state.directories.forEachIndexed { index, node ->
                lines += directoryLine(width, node, totalSize, index == state.selectedIndex)
            }
            if (state.warningCount > 0) {
                lines += frameLine(width, listOf(Segment("Warnings: ${state.warningCount} item(s) skipped", Color.Yellow)))
            }
        }
    }
    return lines
}

private fun loadingLines(state: ExplorerState, width: Int, innerWidth: Int): List<FrameLine> {
    val lines = mutableListOf<FrameLine>()
    val pathDisplay = shortenPath(state.currentPath, innerWidth - 12)
    lines += frameLine(width, listOf(
        Segment("Scanning ", Color.Cyan),
        Segment(state.spinnerFrame.toString(), Color.Yellow),
        Segment(" $pathDisplay", Color.Cyan)
    ))

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
            lines += frameLine(width, progressSegments)
        }
        lines += frameLine(width, listOf(Segment(progressCountsLabel(progress), Color.Cyan)))
        progress.currentDirectory?.let { dir ->
            val label = "Directory: "
            val available = (innerWidth - label.length).coerceAtLeast(0)
            val dirDisplay = shortenPath(dir, available)
            lines += frameLine(width, listOf(
                Segment(label, Color.Cyan),
                Segment(dirDisplay, Color.White)
            ))
        }
        progress.currentFile?.let { file ->
            val label = "File: "
            val available = (innerWidth - label.length).coerceAtLeast(0)
            val fileDisplay = shortenPath(file, available)
            lines += frameLine(width, listOf(
                Segment(label, Color.Cyan),
                Segment(fileDisplay, Color.White)
            ))
        }
    } else {
        lines += frameLine(width, listOf(Segment("Preparing directory statistics...", Color.Cyan)))
    }

    lines += frameLine(width, listOf(Segment("Collecting directory statistics...", Color.Cyan)))
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

private fun directoryLine(width: Int, node: FileNode, totalParentSize: Long, isSelected: Boolean): FrameLine {
    val innerWidth = width - 2
    val selector = if (isSelected) ">" else " "
    val nameWithType = if (node.isDirectory) "${node.name}/" else node.name
    val size = SizeFormatter.format(node.totalSize())
    val percentage = if (totalParentSize > 0) node.totalSize().toDouble() / totalParentSize * 100 else 0.0
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
        node.totalSize() >= 1_000_000_000 -> Color.Red
        node.totalSize() >= 100_000_000 -> Color.Yellow
        node.totalSize() >= 10_000_000 -> Color.Cyan
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
