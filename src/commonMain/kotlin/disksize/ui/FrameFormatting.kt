package disksize.ui

import com.jakewharton.mosaic.ui.Color
import kotlin.math.round
import kotlin.math.roundToInt

internal fun formatPercentage(percentage: Double): String {
    val rounded = round(percentage * 10) / 10.0
    val integerPart = rounded.toInt()
    val fractionalPart = rounded - integerPart
    val decimalDigit = round(fractionalPart * 10).toInt()
    return "$integerPart.$decimalDigit"
}

internal fun formatDuration(seconds: Double): String {
    val rounded = round(seconds * 10) / 10.0
    val integerPart = rounded.toInt()
    val fractionalPart = rounded - integerPart
    val decimalDigit = round(fractionalPart * 10).toInt()
    return "$integerPart.$decimalDigit"
}

internal fun determinateProgressSegment(fraction: Double, width: Int): Segment {
    if (width <= 0) return Segment("")
    val inner = (width - 2).coerceAtLeast(1)
    val clamped = fraction.coerceIn(0.0, 1.0)
    val filled = (clamped * inner).roundToInt().coerceIn(0, inner)
    val text = buildString {
        append('[')
        repeat(filled) { append('█') }
        repeat(inner - filled) { append('░') }
        append(']')
    }
    return Segment(text, Color.Green)
}
