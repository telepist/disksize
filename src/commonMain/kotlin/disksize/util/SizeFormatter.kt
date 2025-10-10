package disksize.util

import kotlin.math.abs

/**
 * Utility object for formatting file sizes in human-readable format.
 */
object SizeFormatter {
    private const val BYTES_PER_KB = 1024L
    private const val BYTES_PER_MB = BYTES_PER_KB * 1024
    private const val BYTES_PER_GB = BYTES_PER_MB * 1024
    private const val BYTES_PER_TB = BYTES_PER_GB * 1024

    /**
     * Format a size in bytes to a human-readable string.
     *
     * Uses binary units (1 KB = 1024 bytes).
     * Displays one decimal place for values >= 1 KB.
     *
     * Examples:
     * - 0 -> "0 B"
     * - 1023 -> "1023 B"
     * - 1024 -> "1.0 KB"
     * - 1536 -> "1.5 KB"
     * - 1048576 -> "1.0 MB"
     * - 1073741824 -> "1.0 GB"
     * - 1099511627776 -> "1.0 TB"
     *
     * @param bytes Size in bytes
     * @return Formatted string with unit (B, KB, MB, GB, TB)
     */
    fun format(bytes: Long): String {
        return when {
            bytes < 0 -> format(abs(bytes)) // Handle negative values
            bytes < BYTES_PER_KB -> "$bytes B"
            bytes < BYTES_PER_MB -> formatWithDecimal(bytes, BYTES_PER_KB, "KB")
            bytes < BYTES_PER_GB -> formatWithDecimal(bytes, BYTES_PER_MB, "MB")
            bytes < BYTES_PER_TB -> formatWithDecimal(bytes, BYTES_PER_GB, "GB")
            else -> formatWithDecimal(bytes, BYTES_PER_TB, "TB")
        }
    }

    /**
     * Format a value with one decimal place and a unit.
     * Uses floating point arithmetic with proper rounding.
     */
    private fun formatWithDecimal(bytes: Long, divisor: Long, unit: String): String {
        val value = bytes.toDouble() / divisor
        // Round to 1 decimal place
        val rounded = kotlin.math.round(value * 10) / 10.0
        // Format with one decimal place by converting the rounded value
        val integerPart = rounded.toInt()
        val fractionalPart = rounded - integerPart
        val decimalDigit = kotlin.math.round(fractionalPart * 10).toInt()
        return "$integerPart.$decimalDigit $unit"
    }
}
