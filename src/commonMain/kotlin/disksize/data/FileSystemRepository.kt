package disksize.data

import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for file system operations.
 * Platform-specific implementations will provide actual file system access.
 */
interface FileSystemRepository {
    data class DirectoryScanResult(
        val root: FileNode,
        val errors: List<ScanError>
    )

    /**
     * Scan a directory and return its structure with all children.
     *
     * @param path Absolute path to the directory to scan
     * @return Result containing the FileNode tree and any non-fatal errors, or a failure if the operation fails
     */
    fun scanDirectory(path: String): Flow<DirectoryScanUpdate>

    /**
     * Get basic information about a single file or directory without scanning children.
     *
     * @param path Absolute path to the file or directory
     * @return Result containing the FileNode (without children), or an error if the operation fails
     */
    suspend fun getFileInfo(path: String): Result<FileNode>

    /**
     * Check if a path exists in the file system.
     *
     * @param path Absolute path to check
     * @return true if the path exists, false otherwise
     */
    suspend fun exists(path: String): Boolean

    /**
     * Check if a path is accessible (not permission denied).
     *
     * @param path Absolute path to check
     * @return true if the path can be accessed, false otherwise
     */
    suspend fun isAccessible(path: String): Boolean
}

sealed interface DirectoryScanUpdate {
    data class Progress(val progress: ScanProgress) : DirectoryScanUpdate
    data class Complete(val result: FileSystemRepository.DirectoryScanResult) : DirectoryScanUpdate
}
