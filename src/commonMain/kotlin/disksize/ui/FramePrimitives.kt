package disksize.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text

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

internal fun topBorder(width: Int): FrameLine {
    val innerWidth = (width - 2).coerceAtLeast(0)
    return FrameLine(listOf(Segment("╔" + "═".repeat(innerWidth) + "╗", Color.Cyan)))
}

internal fun middleBorder(width: Int): FrameLine {
    val innerWidth = (width - 2).coerceAtLeast(0)
    return FrameLine(listOf(Segment("╠" + "═".repeat(innerWidth) + "╣", Color.Cyan)))
}

internal fun bottomBorder(width: Int): FrameLine {
    val innerWidth = (width - 2).coerceAtLeast(0)
    return FrameLine(listOf(Segment("╚" + "═".repeat(innerWidth) + "╝", Color.Cyan)))
}

internal fun blankLine(width: Int): FrameLine = frameLine(width, emptyList())

internal fun frameLineCentered(width: Int, content: String, color: Color): FrameLine {
    val innerWidth = (width - 2).coerceAtLeast(0)
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

    return FrameLine(listOf(Segment("║", Color.Cyan)) + trimmed + Segment("║", Color.Cyan))
}
