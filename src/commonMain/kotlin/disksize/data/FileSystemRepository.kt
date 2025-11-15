package disksize.data

import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Abstract base repository for file system operations.
 * Platform-specific implementations provide platform-specific file system access.
 */
abstract class FileSystemRepository {
    data class DirectoryScanResult(
        val root: FileNode,
        val errors: List<ScanError>
    )

    data class DeletionStats(
        val itemsDeleted: Int,
        val bytesFreed: Long
    )

    /**
     * Scan a directory and return its structure with all children.
     *
     * @param path Absolute path to the directory to scan
     * @return Flow emitting progress updates and final result
     */
    fun scanDirectory(path: String): Flow<DirectoryScanUpdate> = flow {
        // Validate the path exists and is accessible
        val rootNode = createFileNode(path)
        if (!rootNode.isDirectory) {
            throw Exception("Not a directory: $path")
        }

        val errors = mutableListOf<ScanError>()

        val tracker = AdaptiveProgressTracker(
            emitProgress = { progress -> emit(DirectoryScanUpdate.Progress(progress)) },
            batchSize = 100,
            minIntervalMs = 50
        )
        val node = scanDirectoryRecursive(path, errors, tracker, isRoot = true)
        tracker.onComplete()
        emit(DirectoryScanUpdate.Complete(DirectoryScanResult(node, errors)))
    }

    /**
     * Get basic information about a single file or directory without scanning children.
     *
     * @param path Absolute path to the file or directory
     * @return Result containing the FileNode (without children), or an error if the operation fails
     */
    suspend fun getFileInfo(path: String): Result<FileNode> = try {
        val node = createFileNode(path)
        Result.success(node.copy(children = emptyList()))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Delete a file or directory.
     * For directories, this will recursively delete all contents.
     *
     * @param path Absolute path to the file or directory to delete
     * @return Result containing the number of items deleted and bytes freed, or an error
     */
    suspend fun delete(path: String): Result<DeletionStats> = try {
        // First, get the file info to know what we're deleting
        val node = createFileNode(path)
        val totalSize = node.totalSize()

        // Perform deletion
        val itemsDeleted = if (node.isDirectory) {
            deleteDirectoryRecursive(path)
        } else {
            deleteFile(path)
            1
        }

        Result.success(DeletionStats(
            itemsDeleted = itemsDeleted,
            bytesFreed = totalSize
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Recursively scan a directory and build the file tree.
     */
    protected abstract suspend fun scanDirectoryRecursive(
        path: String,
        errors: MutableList<ScanError>,
        tracker: AdaptiveProgressTracker,
        isRoot: Boolean
    ): FileNode

    /**
     * Create a FileNode for the given path with platform-specific metadata.
     */
    protected abstract fun createFileNode(path: String): FileNode

    /**
     * Check if a path exists in the file system.
     */
    abstract suspend fun exists(path: String): Boolean

    /**
     * Check if a path is accessible (not permission denied).
     */
    abstract suspend fun isAccessible(path: String): Boolean

    /**
     * Delete a single file (not a directory).
     */
    protected abstract fun deleteFile(path: String)

    /**
     * Recursively delete a directory and all its contents.
     * @return Number of items deleted (including the directory itself)
     */
    protected abstract fun deleteDirectoryRecursive(path: String): Int

    /**
     * Calculate aggregate statistics for a directory from its children.
     */
    protected fun calculateAggregates(
        baseNode: FileNode,
        children: List<FileNode>
    ): FileNode {
        var totalSize = baseNode.size
        var totalFiles = 0
        var totalDirs = 0

        for (child in children) {
            totalSize += child.cachedTotalSize
            totalFiles += child.cachedFileCount
            totalDirs += child.cachedDirectoryCount
            if (child.isDirectory) {
                totalDirs += 1  // Count the directory itself
            }
        }

        return baseNode.copy(
            children = children,
            cachedTotalSize = totalSize,
            cachedFileCount = totalFiles,
            cachedDirectoryCount = totalDirs
        )
    }
}

sealed interface DirectoryScanUpdate {
    data class Progress(val progress: ScanProgress) : DirectoryScanUpdate
    data class Complete(val result: FileSystemRepository.DirectoryScanResult) : DirectoryScanUpdate
}
