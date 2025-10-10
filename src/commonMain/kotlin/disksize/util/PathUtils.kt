package disksize.util

fun parentPath(path: String): String? {
    if (path.isEmpty() || path == "/") return null
    val trimmed = path.trimEnd('/')
    val separatorIndex = trimmed.lastIndexOf('/')
    return when {
        separatorIndex < 0 -> null
        separatorIndex == 0 -> "/"
        else -> trimmed.substring(0, separatorIndex)
    }
}

fun normalizePath(path: String): String {
    if (path.isEmpty()) return "."
    if (path == "/") return path
    val collapsed = collapseSeparators(path)
    val trimmed = collapsed.trimEnd('/')
    return trimmed.ifEmpty { "/" }
}

private fun collapseSeparators(path: String): String {
    val builder = StringBuilder(path.length)
    var previousSlash = false
    for (ch in path) {
        if (ch == '/') {
            if (!previousSlash) {
                builder.append(ch)
                previousSlash = true
            }
        } else {
            builder.append(ch)
            previousSlash = false
        }
    }
    return builder.toString()
}
