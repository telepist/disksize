package disksize.domain.model

/**
 * Result of a file or directory deletion operation.
 */
sealed interface DeletionResult {
    /**
     * Deletion completed successfully.
     *
     * @property path The path that was deleted
     * @property itemsDeleted Number of items deleted (1 for file, N for directory with contents)
     * @property bytesFreed Total bytes freed by the deletion
     */
    data class Success(
        val path: String,
        val itemsDeleted: Int,
        val bytesFreed: Long
    ) : DeletionResult

    /**
     * Deletion failed with an error.
     *
     * @property path The path that failed to delete
     * @property message Human-readable error message
     * @property errorType Classification of the error
     */
    data class Failure(
        val path: String,
        val message: String,
        val errorType: ErrorType
    ) : DeletionResult
}
