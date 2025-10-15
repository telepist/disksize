package disksize.ui

internal fun shortenPath(path: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (path.length <= maxLength) return path.padEnd(maxLength)
    if (maxLength <= 3) return path.take(maxLength)
    val suffix = path.takeLast(maxLength - 3)
    return ("..." + suffix).padEnd(maxLength)
}

internal fun truncateWithEllipsis(text: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    if (text.length <= maxLength) return text.padEnd(maxLength)
    if (maxLength <= 3) return text.take(maxLength)
    return (text.take(maxLength - 3) + "...").padEnd(maxLength)
}
