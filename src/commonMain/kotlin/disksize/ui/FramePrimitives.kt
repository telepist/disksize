package disksize.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text

internal object Theme {
    val title = Color(0, 190, 190)
    val pathText = Color(200, 200, 210)
    val separator = Color(60, 60, 70)
    val directoryName = Color(0, 190, 190)
    val fileName = Color(180, 180, 190)
    val sizeHuge = Color(255, 100, 100)      // >= 1GB
    val sizeLarge = Color(255, 180, 50)       // >= 100MB
    val sizeMedium = Color(0, 190, 190)       // >= 10MB
    val sizeSmall = Color(160, 160, 170)      // < 10MB
    val barFilled = Color(180, 80, 200)
    val barEmpty = Color(40, 40, 50)
    val barSelected = Color(0, 220, 200)
    val selectedBg = Color(0, 35, 40)
    val treeConnector = Color(70, 70, 80)
    val keyHint = Color(120, 120, 130)
    val keyLabel = Color(200, 200, 210)
    val statusSuccess = Color(80, 220, 120)
    val statusWarning = Color(255, 180, 50)
    val statusError = Color(255, 100, 100)
    val spinner = Color(255, 180, 50)
    val dim = Color(100, 100, 100)
}

internal data class Segment(val text: String, val color: Color? = null, val background: Color? = null)

internal data class FrameLine(val segments: List<Segment>)

@Composable
internal fun FrameLineRow(frameLine: FrameLine) {
    Row {
        frameLine.segments.forEach { segment ->
            Text(
                segment.text,
                color = segment.color ?: Color.Unspecified,
                background = segment.background ?: Color.Unspecified,
            )
        }
    }
}

@Composable
internal fun FrameLines(lines: List<FrameLine>) {
    lines.forEach { FrameLineRow(it) }
}

internal fun horizontalRule(width: Int): FrameLine {
    val ruleWidth = (width - 1).coerceAtLeast(0)
    return FrameLine(listOf(Segment(" " + "─".repeat(ruleWidth), Theme.separator)))
}

internal fun blankLine(width: Int): FrameLine = frameLine(width, emptyList())

internal fun frameLineCentered(width: Int, content: String, color: Color): FrameLine {
    val innerWidth = (width - 1).coerceAtLeast(0)
    if (innerWidth == 0) return frameLine(width, emptyList())
    val truncated = content.take(innerWidth)
    val padding = ((innerWidth - truncated.length) / 2).coerceAtLeast(0)
    val rightPadding = (innerWidth - padding - truncated.length).coerceAtLeast(0)
    val centered = buildString {
        append(" ".repeat(padding))
        append(truncated)
        append(" ".repeat(rightPadding))
    }
    return frameLine(width, listOf(Segment(centered, color)))
}

internal fun frameLine(width: Int, segments: List<Segment>, background: Color? = null): FrameLine {
    val innerWidth = width - 1
    if (innerWidth <= 0) return FrameLine(listOf(Segment("")))

    val trimmed = mutableListOf<Segment>()
    var consumed = 0
    for (segment in segments) {
        if (consumed >= innerWidth) break
        val remaining = innerWidth - consumed
        val text = segment.text.take(remaining)
        consumed += text.length
        trimmed += Segment(text, segment.color, segment.background ?: background)
    }

    if (trimmed.isEmpty()) {
        trimmed += Segment(" ".repeat(innerWidth), background = background)
    } else {
        val currentLen = trimmed.sumOf { it.text.length }
        when {
            currentLen < innerWidth -> {
                trimmed += Segment(" ".repeat(innerWidth - currentLen), background = background)
            }
            currentLen > innerWidth -> {
                val overflow = currentLen - innerWidth
                val last = trimmed.removeLast()
                trimmed += Segment(last.text.dropLast(overflow), last.color, last.background)
            }
        }
    }

    return FrameLine(listOf(Segment(" ", background = background)) + trimmed)
}

// Dialog border functions with rounded corners
internal fun dialogTop(width: Int): FrameLine {
    val innerWidth = (width - 2).coerceAtLeast(0)
    return FrameLine(listOf(Segment("╭" + "─".repeat(innerWidth) + "╮", Theme.separator)))
}

internal fun dialogBottom(width: Int): FrameLine {
    val innerWidth = (width - 2).coerceAtLeast(0)
    return FrameLine(listOf(Segment("╰" + "─".repeat(innerWidth) + "╯", Theme.separator)))
}

internal fun dialogFrame(width: Int, segments: List<Segment>, background: Color? = null): FrameLine {
    val innerWidth = width - 2
    if (innerWidth <= 0) return FrameLine(listOf(Segment("")))

    val trimmed = mutableListOf<Segment>()
    var consumed = 0
    for (segment in segments) {
        if (consumed >= innerWidth) break
        val remaining = innerWidth - consumed
        val text = segment.text.take(remaining)
        consumed += text.length
        trimmed += Segment(text, segment.color, segment.background ?: background)
    }

    if (trimmed.isEmpty()) {
        trimmed += Segment(" ".repeat(innerWidth), background = background)
    } else {
        val currentLen = trimmed.sumOf { it.text.length }
        when {
            currentLen < innerWidth -> {
                trimmed += Segment(" ".repeat(innerWidth - currentLen), background = background)
            }
            currentLen > innerWidth -> {
                val overflow = currentLen - innerWidth
                val last = trimmed.removeLast()
                trimmed += Segment(last.text.dropLast(overflow), last.color, last.background)
            }
        }
    }

    return FrameLine(listOf(Segment("│", Theme.separator)) + trimmed + Segment("│", Theme.separator))
}

internal fun dialogFrameCentered(width: Int, content: String, color: Color): FrameLine {
    val innerWidth = (width - 2).coerceAtLeast(0)
    if (innerWidth == 0) return dialogFrame(width, emptyList())
    val truncated = content.take(innerWidth)
    val padding = ((innerWidth - truncated.length) / 2).coerceAtLeast(0)
    val rightPadding = (innerWidth - padding - truncated.length).coerceAtLeast(0)
    val centered = buildString {
        append(" ".repeat(padding))
        append(truncated)
        append(" ".repeat(rightPadding))
    }
    return dialogFrame(width, listOf(Segment(centered, color)))
}

internal fun dialogBlankLine(width: Int): FrameLine = dialogFrame(width, emptyList())
