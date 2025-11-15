package disksize.domain.usecase

import disksize.data.FileSystemRepository
import disksize.domain.model.DeletionResult
import disksize.domain.model.ErrorType

/**
 * Use case for deleting files and directories.
 *
 * Handles deletion with proper error reporting and statistics tracking.
 */
class DeleteFileUseCase(
    private val repository: FileSystemRepository
) {
    /**
     * Delete a file or directory at the given path.
     *
     * @param path Absolute path to delete
     * @return DeletionResult indicating success or failure
     */
    suspend fun delete(path: String): DeletionResult {
        // First check if the path exists
        if (!repository.exists(path)) {
            return DeletionResult.Failure(
                path = path,
                message = "File or directory not found",
                errorType = ErrorType.NOT_FOUND
            )
        }

        // Attempt deletion
        return repository.delete(path).fold(
            onSuccess = { stats ->
                DeletionResult.Success(
                    path = path,
                    itemsDeleted = stats.itemsDeleted,
                    bytesFreed = stats.bytesFreed
                )
            },
            onFailure = { error ->
                val errorType = repository.classifyError(error)
                val message = error.message ?: when (errorType) {
                    ErrorType.PERMISSION_DENIED -> "Permission denied: $path"
                    ErrorType.NOT_FOUND -> "File or directory not found: $path"
                    else -> "Failed to delete: $path"
                }
                DeletionResult.Failure(
                    path = path,
                    message = message,
                    errorType = errorType
                )
            }
        )
    }
}
