package disksize.data

/**
 * Utilities for Windows path handling and normalization.
 * Centralizes common path operations to avoid duplication.
 */
internal object WindowsPathUtils {

    /**
     * Normalizes a path by replacing forward slashes with backslashes.
     */
    fun normalize(path: String): String = path.replace('/', '\\')

    /**
     * Creates a search path suitable for FindFirstFileW/FindNextFileW.
     * Adds the wildcard pattern (*) to the normalized path.
     */
    fun toSearchPath(path: String): String {
        val normalized = normalize(path)
        return if (normalized.endsWith("\\")) {
            "${normalized}*"
        } else {
            "$normalized\\*"
        }
    }

    /**
     * Joins a parent path with a child name, handling normalization and separators.
     */
    fun joinPath(parent: String, childName: String): String {
        val normalizedParent = normalize(parent)
        return when {
            normalizedParent.isEmpty() -> childName
            normalizedParent.endsWith("\\") -> normalizedParent + childName
            else -> "$normalizedParent\\$childName"
        }
    }

    /**
     * Extracts the file/directory name from a path.
     * Returns the portion after the last backslash, or the full path if no separator exists.
     */
    fun extractName(path: String): String {
        val normalized = normalize(path)
        return normalized.substringAfterLast('\\').ifEmpty { normalized }
    }
}
