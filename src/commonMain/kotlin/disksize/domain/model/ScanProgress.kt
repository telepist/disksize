package disksize.domain.model

/**
 * Represents incremental progress information reported during a scan.
 *
 * @property processedFiles Number of files that have been scanned so far.
 * @property totalFiles Total number of files expected to be scanned.
 * @property processedDirectories Number of directories that have been scanned so far (excluding the root).
 * @property totalDirectories Total number of directories expected to be scanned (excluding the root).
 * @property currentDirectory Directory path currently being traversed, if any.
 * @property currentFile File path currently being processed, if any.
 */
data class ScanProgress(
    val processedFiles: Int,
    val totalFiles: Int,
    val processedDirectories: Int,
    val totalDirectories: Int,
    val currentDirectory: String? = null,
    val currentFile: String? = null
) {
    val processedItems: Int
        get() = processedFiles + processedDirectories

    val totalItems: Int
        get() = totalFiles + totalDirectories
}
