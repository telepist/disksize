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
                val message = error.message ?: "Failed to delete: $path"
                val errorType = classifyError(message)
                DeletionResult.Failure(
                    path = path,
                    message = message,
                    errorType = errorType
                )
            }
        )
    }

    private fun classifyError(message: String): ErrorType {
        val lowerMessage = message.lowercase()
        return when {
            "permission" in lowerMessage && "denied" in lowerMessage -> ErrorType.PERMISSION_DENIED
            "not found" in lowerMessage -> ErrorType.NOT_FOUND
            "no such file" in lowerMessage -> ErrorType.NOT_FOUND
            else -> ErrorType.IO_ERROR
        }
    }
}
