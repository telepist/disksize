package disksize.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text

internal data class Segment(val text: String, val color: Color? = null)

internal data class FrameLine(val segments: List<Segment>)

@Composable
internal fun FrameLineRow(frameLine: FrameLine) {
    Row {
        frameLine.segments.forEach { segment ->
            if (segment.color != null) {
                Text(segment.text, color = segment.color)
            } else {
                Text(segment.text)
            }
        }
    }
}

@Composable
internal fun FrameLines(lines: List<FrameLine>) {
    lines.forEach { FrameLineRow(it) }
}

internal fun topBorder(width: Int): FrameLine =
    FrameLine(listOf(Segment("╔" + "═".repeat(width - 2) + "╗", Color.Cyan)))

internal fun middleBorder(width: Int): FrameLine =
    FrameLine(listOf(Segment("╠" + "═".repeat(width - 2) + "╣", Color.Cyan)))

internal fun bottomBorder(width: Int): FrameLine =
    FrameLine(listOf(Segment("╚" + "═".repeat(width - 2) + "╝", Color.Cyan)))

internal fun blankLine(width: Int): FrameLine = frameLine(width, emptyList())

internal fun frameLineCentered(width: Int, content: String, color: Color): FrameLine {
    val innerWidth = width - 2
    val truncated = content.take(innerWidth)
    val padding = ((innerWidth - truncated.length) / 2).coerceAtLeast(0)
    val centered = buildString {
        append(" ".repeat(padding))
        append(truncated)
        append(" ".repeat(innerWidth - padding - truncated.length))
    }
    return frameLine(width, listOf(Segment(centered, color)))
}

internal fun frameLine(width: Int, segments: List<Segment>): FrameLine {
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
