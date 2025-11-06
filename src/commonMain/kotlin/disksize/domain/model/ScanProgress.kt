package disksize.domain.model

/**
 * Represents incremental progress information reported during a scan.
 * Progress is indeterminate - we report stats as we scan without estimating totals.
 *
 * @property processedFiles Number of files that have been scanned so far.
 * @property processedDirectories Number of directories that have been scanned so far (excluding the root).
 * @property scannedBytes Total bytes scanned so far.
 * @property bytesPerSecond Current scanning throughput in bytes per second.
 * @property currentDirectory Directory path currently being traversed, if any.
 */
data class ScanProgress(
    val processedFiles: Int,
    val processedDirectories: Int,
    val scannedBytes: Long,
    val bytesPerSecond: Long,
    val currentDirectory: String? = null
)
