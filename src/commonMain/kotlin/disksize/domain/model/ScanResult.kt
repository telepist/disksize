package disksize.domain.model

/**
 * Result of a directory scan operation.
 *
 * @property rootPath The path that was scanned
 * @property totalSize Total size in bytes of all files in the directory tree
 * @property fileCount Total number of files found
 * @property directoryCount Total number of subdirectories found
 * @property rootNode The root file node containing the directory structure
 * @property scanDurationMs Time taken to complete the scan in milliseconds
 * @property errors List of errors encountered during scanning
 */
data class ScanResult(
    val rootPath: String,
    val totalSize: Long,
    val fileCount: Int,
    val directoryCount: Int,
    val rootNode: FileNode,
    val scanDurationMs: Long,
    val errors: List<ScanError>
) {
    /**
     * Check if the scan completed without any errors.
     */
    fun isSuccessful(): Boolean = errors.isEmpty()
}

/**
 * Error encountered during a directory scan.
 *
 * @property path Path where the error occurred
 * @property message Error message
 * @property type Type of error
 */
data class ScanError(
    val path: String,
    val message: String,
    val type: ErrorType
)

/**
 * Types of errors that can occur during scanning.
 */
enum class ErrorType {
    /** Permission denied when accessing file or directory */
    PERMISSION_DENIED,

    /** File or directory not found */
    NOT_FOUND,

    /** I/O error (disk error, network error, etc.) */
    IO_ERROR,

    /** Unknown or unexpected error */
    UNKNOWN
}
