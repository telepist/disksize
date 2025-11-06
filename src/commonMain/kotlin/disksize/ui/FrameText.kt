package disksize.ui

internal fun shortenPath(path: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (path.length <= maxLength) return path.padEnd(maxLength)
    if (maxLength <= 3) return path.take(maxLength)

    // Show ellipsis in the middle: beginning...end
    // Reserve 3 characters for "..."
    val ellipsis = "..."
    val availableLength = maxLength - ellipsis.length
    val prefixLength = availableLength / 2
    val suffixLength = availableLength - prefixLength

    val prefix = path.take(prefixLength)
    val suffix = path.takeLast(suffixLength)

    return (prefix + ellipsis + suffix).padEnd(maxLength)
}

internal fun truncateWithEllipsis(text: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (text.length <= maxLength) return text.padEnd(maxLength)
    if (maxLength <= 3) return text.take(maxLength)
    return (text.take(maxLength - 3) + "...").padEnd(maxLength)
}
