package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import disksize.domain.model.FileNode
import disksize.presentation.ExplorerState
import disksize.util.SizeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_WIDTH = 48
private const val MIN_ROWS = 16
private const val PROGRESS_BAR_TARGET = 24

@Composable
fun MainScreen(
    state: ExplorerState,
    onMoveSelection: (Int) -> Unit,
    onOpenSelected: () -> Unit,
    onNavigateUp: () -> Unit,
    onCycleSort: () -> Unit,
    onQuit: () -> Unit
) {
    val terminalState = LocalTerminalState.current
    val frameWidth = max(MIN_WIDTH, terminalState?.size?.columns ?: 80)
    val frameRows = max(MIN_ROWS, terminalState?.size?.rows ?: 24)

    val screenLines = remember(state, frameWidth, frameRows) {
        buildScreenLines(state, frameWidth, frameRows)
    }

    Column(
        modifier = Modifier.onKeyEvent { event ->
            handleKey(
                event = event,
                moveSelection = onMoveSelection,
                openSelected = onOpenSelected,
                navigateUp = onNavigateUp,
                cycleSort = onCycleSort,
                quit = onQuit
            )
        }
    ) {
        screenLines.forEach { line -> Text(line) }
    }
}

private fun handleKey(
    event: KeyEvent,
    moveSelection: (Int) -> Unit,
    openSelected: () -> Unit,
    navigateUp: () -> Unit,
    cycleSort: () -> Unit,
    quit: () -> Unit
): Boolean {
    return when (event.key) {
        "ArrowDown", "j" -> {
            moveSelection(1)
            true
        }
        "ArrowUp", "k" -> {
            moveSelection(-1)
            true
        }
        "Enter", "ArrowRight", "l" -> {
            openSelected()
            true
        }
        "Backspace", "ArrowLeft", "h" -> {
            navigateUp()
            true
        }
        "s", "S" -> {
            cycleSort()
            true
        }
        "q", "Q" -> {
            quit()
            true
        }
        else -> false
    }
}

private fun buildScreenLines(state: ExplorerState, width: Int, rows: Int): List<String> {
    val lines = mutableListOf<String>()
    lines += topBorder(width)
    lines += frameLineCentered(width, "DiskSize - Disk Space Analyzer")
    lines += middleBorder(width)
    lines += frameLine(width, "Path: ${shortenPath(state.currentPath, width - 8)}")
    lines += blankLine(width)
    lines += statsLines(state, width)
    lines += blankLine(width)
    lines += directoryLines(state, width)

    val reservedForStatus = 3 // divider, status, bottom border
    val fillerCount = rows - (lines.size + reservedForStatus)
    if (fillerCount > 0) {
        repeat(fillerCount) { lines += blankLine(width) }
    }

    lines += middleBorder(width)
    lines += statusLine(state, width)
    lines += bottomBorder(width)
    return lines
}

private fun statsLines(state: ExplorerState, width: Int): List<String> {
    val totalSize = state.scanResult?.let { SizeFormatter.format(it.totalSize) } ?: "--"
    val fileCount = state.scanResult?.fileCount?.toString() ?: "--"
    val directoryCount = state.scanResult?.directoryCount?.toString() ?: "--"
    return listOf(
        frameLine(width, "Total Size: $totalSize"),
        frameLine(width, "Files: $fileCount"),
        frameLine(width, "Directories: $directoryCount")
    )
}

private fun directoryLines(state: ExplorerState, width: Int): List<String> {
    val innerWidth = width - 2
    val lines = mutableListOf<String>()
    lines += frameLine(width, "Directories (Sort: ${state.sortOrder.label})")

    when {
        state.isLoading -> {
            val pathLine = "Scanning ${state.spinnerFrame} ${shortenPath(state.currentPath, innerWidth - 12)}"
            val barWidth = max(4, innerWidth - "Progress: ".length - 2)
            lines += frameLine(width, pathLine)
            lines += frameLine(width, "Progress: ${loadingProgress(state.spinnerIndex, barWidth)}")
            lines += frameLine(width, "Collecting directory statistics...")
        }
        state.errorMessage != null -> {
            val message = state.errorMessage.take(innerWidth - 2)
            lines += frameLine(width, "Error: $message")
        }
        state.directories.isEmpty() -> {
            lines += frameLine(width, "(no subdirectories found)")
        }
        else -> {
            val totalSize = state.directories.sumOf { it.totalSize() }.coerceAtLeast(1L)
            state.directories.forEachIndexed { index, node ->
                lines += directoryLine(width, node, totalSize, index == state.selectedIndex)
            }
            if (state.warningCount > 0) {
                lines += frameLine(width, "Warnings: ${state.warningCount} item(s) skipped")
            }
        }
    }

    return lines
}

private fun directoryLine(width: Int, node: FileNode, totalParentSize: Long, isSelected: Boolean): String {
    val innerWidth = width - 2
    val selector = if (isSelected) ">" else " "
    val nameWithType = (if (node.isDirectory) "${node.name}/" else node.name)
    val size = SizeFormatter.format(node.totalSize())
    val percentage = if (totalParentSize > 0) node.totalSize().toDouble() / totalParentSize * 100 else 0.0
    val percentStr = formatPercentage(percentage)
    val sizePart = "$size (${percentStr}%)"

    val availableForBar = innerWidth - sizePart.length - 4
    val barWidth = max(0, minOf(PROGRESS_BAR_TARGET, availableForBar / 2))
    val availableForLabel = innerWidth - sizePart.length - barWidth - 3
    val truncatedLabel = truncateWithEllipsis("$selector $nameWithType", max(0, availableForLabel))

    val barSegment = if (barWidth > 0) {
        " " + usageBar(barWidth, percentage, isSelected)
    } else ""

    val content = "$truncatedLabel $sizePart$barSegment"
    return frameLine(width, content)
}

private fun usageBar(width: Int, percentage: Double, highlight: Boolean): String {
    if (width <= 0) return ""
    val filled = (percentage / 100.0 * width).roundToInt().coerceIn(0, width)
    val fillChar = if (highlight) '█' else '▓'
    val emptyChar = '░'
    return "".padEnd(filled, fillChar) + "".padEnd(width - filled, emptyChar)
}

private fun statusLine(state: ExplorerState, width: Int): String {
    val innerWidth = width - 2
    val content = when {
        state.isLoading -> {
            "Scanning ${state.spinnerFrame} ${shortenPath(state.currentPath, innerWidth - 12)}"
        }
        state.errorMessage != null -> {
            "Error: ${state.errorMessage.take(innerWidth - 7)}"
        }
        state.scanResult != null -> {
            val seconds = state.scanDurationMs / 1000.0
            val base = "Scan completed in ${formatDuration(seconds)}s"
            val warnings = if (state.warningCount > 0) " • ${state.warningCount} warning(s)" else ""
            val selected = state.selectedDirectory?.let {
                val size = SizeFormatter.format(it.totalSize())
                val name = shortenPath(it.name, innerWidth - base.length - warnings.length - 12)
                " • Selected: $name ($size)"
            } ?: ""
            base + warnings + selected
        }
        else -> "Idle"
    }
    return frameLine(width, content)
}

private fun topBorder(width: Int) = "╔" + "═".repeat(width - 2) + "╗"
private fun middleBorder(width: Int) = "╠" + "═".repeat(width - 2) + "╣"
private fun bottomBorder(width: Int) = "╚" + "═".repeat(width - 2) + "╝"
private fun blankLine(width: Int) = frameLine(width, "")

private fun frameLine(width: Int, content: String): String {
    val innerWidth = width - 2
    val padded = content.padEnd(innerWidth)
    return "║" + padded.take(innerWidth) + "║"
}

private fun frameLineCentered(width: Int, content: String): String {
    val innerWidth = width - 2
    val truncated = content.take(innerWidth)
    val padding = max(0, (innerWidth - truncated.length) / 2)
    val centered = " ".repeat(padding) + truncated + " ".repeat(innerWidth - padding - truncated.length)
    return "║" + centered + "║"
}

private fun loadingProgress(step: Int, barWidth: Int): String {
    if (barWidth <= 0) return "[]"
    val range = barWidth * 2
    val pulse = step % range
    val filled = if (pulse <= barWidth) pulse else range - pulse
    val safeFilled = filled.coerceIn(0, barWidth)
    val fill = "█".repeat(safeFilled)
    val rest = "░".repeat(barWidth - safeFilled)
    return "[" + fill + rest + "]"
}

private fun shortenPath(path: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (path.length <= maxLength) return path
    if (maxLength <= 3) return path.take(maxLength)
    return "..." + path.takeLast(maxLength - 3)
}

private fun truncateWithEllipsis(text: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (text.length <= maxLength) return text.padEnd(maxLength)
    return if (maxLength <= 3) {
        text.take(maxLength)
    } else {
        text.take(maxLength - 3) + "..."
    }
}

private fun formatPercentage(percentage: Double): String {
    val rounded = kotlin.math.round(percentage * 10) / 10.0
    val integerPart = rounded.toInt()
    val fractionalPart = rounded - integerPart
    val decimalDigit = kotlin.math.round(fractionalPart * 10).toInt()
    return "$integerPart.$decimalDigit"
}

private fun formatDuration(seconds: Double): String {
    val rounded = kotlin.math.round(seconds * 10) / 10.0
    val integerPart = rounded.toInt()
    val fractionalPart = rounded - integerPart
    val decimalDigit = kotlin.math.round(fractionalPart * 10).toInt()
    return "$integerPart.$decimalDigit"
}
