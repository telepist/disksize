package disksize.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import disksize.domain.model.FileNode
import disksize.presentation.ExplorerState
import disksize.util.SizeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_WIDTH = 48
private const val MIN_ROWS = 16
private const val PROGRESS_BAR_TARGET = 24

private data class Segment(val text: String, val color: Color? = null)
private data class FrameLine(val segments: List<Segment>)

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

    val frameLines = remember(state, frameWidth, frameRows) {
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
        frameLines.forEach { line ->
            Row {
                line.segments.forEach { segment ->
                    if (segment.color != null) {
                        Text(segment.text, color = segment.color)
                    } else {
                        Text(segment.text)
                    }
                }
            }
        }
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
        "ArrowDown", "j" -> { moveSelection(1); true }
        "ArrowUp", "k" -> { moveSelection(-1); true }
        "Enter", "ArrowRight", "l" -> { openSelected(); true }
        "Backspace", "ArrowLeft", "h" -> { navigateUp(); true }
        "s", "S" -> { cycleSort(); true }
        "q", "Q" -> { quit(); true }
        else -> false
    }
}

private fun buildScreenLines(state: ExplorerState, width: Int, rows: Int): List<FrameLine> {
    val lines = mutableListOf<FrameLine>()
    lines += topBorder(width)
    lines += frameLineCentered(width, "DiskSize - Disk Space Analyzer", Color.Cyan)
    lines += middleBorder(width)
    lines += frameLine(width, listOf(
        Segment("Path: ", Color.Cyan),
        Segment(shortenPath(state.currentPath, width - 10), Color.White)
    ))
    lines += blankLine(width)
    lines += statsLines(state, width)
    lines += blankLine(width)
    lines += directoryLines(state, width)

    val reservedForStatus = 3
    val fillerCount = rows - (lines.size + reservedForStatus)
    if (fillerCount > 0) {
        repeat(fillerCount) { lines += blankLine(width) }
    }

    lines += middleBorder(width)
    lines += statusLine(state, width)
    lines += bottomBorder(width)
    return lines
}

private fun statsLines(state: ExplorerState, width: Int): List<FrameLine> {
    val totalSize = state.scanResult?.let { SizeFormatter.format(it.totalSize) } ?: "--"
    val fileCount = state.scanResult?.fileCount?.toString() ?: "--"
    val directoryCount = state.scanResult?.directoryCount?.toString() ?: "--"
    return listOf(
        frameLine(width, listOf(Segment("Total Size: ", Color.Cyan), Segment(totalSize, Color.Green))),
        frameLine(width, listOf(Segment("Files: ", Color.Cyan), Segment(fileCount, Color.Green))),
        frameLine(width, listOf(Segment("Directories: ", Color.Cyan), Segment(directoryCount, Color.Green)))
    )
}

private fun directoryLines(state: ExplorerState, width: Int): List<FrameLine> {
    val innerWidth = width - 2
    val lines = mutableListOf<FrameLine>()
    lines += frameLine(width, listOf(Segment("Directories (Sort: ${state.sortOrder.label})", Color.Cyan)))

    when {
        state.isLoading -> {
            val scanningText = "Scanning ${state.spinnerFrame} ${shortenPath(state.currentPath, innerWidth - 12)}"
            val barWidth = max(6, innerWidth - "Progress: ".length)
            lines += frameLine(width, listOf(
                Segment("Scanning ", Color.Cyan),
                Segment(state.spinnerFrame.toString(), Color.Yellow),
                Segment(" ${shortenPath(state.currentPath, innerWidth - 12)}", Color.Cyan)
            ))
            lines += frameLine(width, listOf(
                Segment("Progress: ", Color.Cyan),
                loadingProgressSegment(state.spinnerIndex, barWidth)
            ))
            lines += frameLine(width, listOf(Segment("Collecting directory statistics...", Color.Cyan)))
        }
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

private fun directoryLine(width: Int, node: FileNode, totalParentSize: Long, isSelected: Boolean): FrameLine {
    val innerWidth = width - 2
    val selector = if (isSelected) ">" else " "
    val nameWithType = if (node.isDirectory) "${node.name}/" else node.name
    val size = SizeFormatter.format(node.totalSize())
    val percentage = if (totalParentSize > 0) node.totalSize().toDouble() / totalParentSize * 100 else 0.0
    val percentStr = formatPercentage(percentage)
    val sizePart = "$size (${percentStr}%)"

    val availableForBar = innerWidth - sizePart.length - 4
    val barWidth = max(0, minOf(PROGRESS_BAR_TARGET, availableForBar / 2))
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

private fun statusLine(state: ExplorerState, width: Int): FrameLine {
    val innerWidth = width - 2
    val segments = mutableListOf<Segment>()
    when {
        state.isLoading -> {
            segments += Segment("Scanning ", Color.Cyan)
            segments += Segment(state.spinnerFrame.toString(), Color.Yellow)
            segments += Segment(" ${shortenPath(state.currentPath, innerWidth - 10)}", Color.Cyan)
        }
        state.errorMessage != null -> {
            segments += Segment("Error: ${state.errorMessage.take(innerWidth - 7)}", Color.Red)
        }
        state.scanResult != null -> {
            val seconds = state.scanDurationMs / 1000.0
            val base = "Scan completed in ${formatDuration(seconds)}s"
            val baseSegment = Segment(base, Color.Green)
            segments += baseSegment
            if (state.warningCount > 0) {
                segments += Segment(" • ${state.warningCount} warning(s)", Color.Yellow)
            }
            state.selectedDirectory?.let {
                val size = SizeFormatter.format(it.totalSize())
                val name = shortenPath(it.name, innerWidth - base.length - 25)
                segments += Segment(" • Selected: $name ($size)", Color.Cyan)
            }
        }
        else -> segments += Segment("Idle", Color.Cyan)
    }
    segments += Segment("  q: Quit", Color.Yellow)
    return frameLine(width, segments)
}

private fun topBorder(width: Int) = FrameLine(listOf(Segment("╔" + "═".repeat(width - 2) + "╗", Color.Cyan)))
private fun middleBorder(width: Int) = FrameLine(listOf(Segment("╠" + "═".repeat(width - 2) + "╣", Color.Cyan)))
private fun bottomBorder(width: Int) = FrameLine(listOf(Segment("╚" + "═".repeat(width - 2) + "╝", Color.Cyan)))
private fun blankLine(width: Int) = frameLine(width, emptyList())

private fun frameLineCentered(width: Int, content: String, color: Color): FrameLine {
    val innerWidth = width - 2
    val truncated = content.take(innerWidth)
    val padding = max(0, (innerWidth - truncated.length) / 2)
    val centered = " ".repeat(padding) + truncated + " ".repeat(innerWidth - padding - truncated.length)
    return frameLine(width, listOf(Segment(centered, color)))
}

private fun frameLine(width: Int, segments: List<Segment>): FrameLine {
    val innerWidth = width - 2
    if (innerWidth <= 0) return FrameLine(listOf(Segment("")))

    val trimmed = mutableListOf<Segment>()
    var consumed = 0
    for (segment in segments) {
        if (consumed >= innerWidth) break
        val remaining = innerWidth - consumed
        val text = segment.text.take(remaining)
        consumed += text.length
        trimmed += Segment(text, segment.color)
    }

    if (trimmed.isEmpty()) {
        trimmed += Segment(" ".repeat(innerWidth))
    } else {
        val currentLen = trimmed.sumOf { it.text.length }
        when {
            currentLen < innerWidth -> {
                trimmed += Segment(" ".repeat(innerWidth - currentLen))
            }
            currentLen > innerWidth -> {
                val overflow = currentLen - innerWidth
                val last = trimmed.removeLast()
                trimmed += Segment(last.text.dropLast(overflow), last.color)
            }
        }
    }

    return FrameLine(listOf(Segment("║", Color.Cyan)) + trimmed + Segment("║", Color.Cyan))
}

private fun loadingProgressSegment(step: Int, width: Int): Segment {
    val inner = (width - 2).coerceAtLeast(1)
    val range = inner * 2
    val pulse = step % range
    val filled = if (pulse <= inner) pulse else range - pulse
    val safeFilled = filled.coerceIn(0, inner)
    val text = buildString {
        append('[')
        repeat(safeFilled) { append('█') }
        repeat(inner - safeFilled) { append('░') }
        append(']')
    }
    return Segment(text, Color.Yellow)
}

private fun shortenPath(path: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (path.length <= maxLength) return path.padEnd(maxLength)
    if (maxLength <= 3) return path.take(maxLength)
    val suffix = path.takeLast(maxLength - 3)
    return ("..." + suffix).padEnd(maxLength)
}

private fun truncateWithEllipsis(text: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (text.length <= maxLength) return text.padEnd(maxLength)
    if (maxLength <= 3) return text.take(maxLength)
    return (text.take(maxLength - 3) + "...").padEnd(maxLength)
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
