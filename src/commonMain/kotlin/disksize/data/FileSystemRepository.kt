package disksize.data

import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

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

        tracker.startDirectory(rootNode.path, isRoot = true)

        // List immediate children without recursing
        val immediateChildren = listDirectoryChildren(path, errors)

        // Separate files/symlinks from directories, track files immediately
        val children = immediateChildren.toMutableList()
        val scannedPaths = mutableSetOf<String>()
        val dirIndices = mutableListOf<Int>()
        var filesInDir = 0
        var directoriesInDir = 0

        for ((index, child) in children.withIndex()) {
            if (child.isDirectory && !child.isSymlink) {
                dirIndices += index
                directoriesInDir++
            } else {
                tracker.onFileProcessed(child.path, child.size)
                scannedPaths += child.path
                filesInDir++
            }
        }

        // Emit initial partial tree (files resolved, dirs are placeholders)
        val initialRoot = calculateAggregates(rootNode, children)
        emit(DirectoryScanUpdate.PartialTree(initialRoot, scannedPaths.toSet(), errors.toList()))

        // Scan each child directory one by one, throttling partial tree emissions
        val partialClock = TimeSource.Monotonic.markNow()
        var lastPartialEmitMs = partialClock.elapsedNow().inWholeMilliseconds
        for ((i, dirIndex) in dirIndices.withIndex()) {
            val placeholder = children[dirIndex]

            val scannedChild = scanDirectoryRecursive(
                placeholder.path, errors, tracker, isRoot = false,
                onSubdirScanned = { partialNode ->
                    // Callback from recursive scan: a subdirectory within this
                    // depth-0 directory completed. Update the tree and emit if
                    // enough time has passed.
                    children[dirIndex] = partialNode
                    val cbNow = partialClock.elapsedNow().inWholeMilliseconds
                    if (cbNow - lastPartialEmitMs >= PARTIAL_TREE_MIN_INTERVAL_MS) {
                        val updatedRoot = calculateAggregates(rootNode, children)
                        emit(DirectoryScanUpdate.PartialTree(updatedRoot, scannedPaths.toSet(), errors.toList()))
                        lastPartialEmitMs = cbNow
                    }
                },
                scannedPaths = scannedPaths
            )
            children[dirIndex] = scannedChild
            scannedPaths += placeholder.path

            val now = partialClock.elapsedNow().inWholeMilliseconds
            val isLast = i == dirIndices.lastIndex
            if (isLast || now - lastPartialEmitMs >= PARTIAL_TREE_MIN_INTERVAL_MS) {
                val updatedRoot = calculateAggregates(rootNode, children)
                emit(DirectoryScanUpdate.PartialTree(updatedRoot, scannedPaths.toSet(), errors.toList()))
                lastPartialEmitMs = now
            }
        }

        // Root directory processing complete
        tracker.onDirectoryProcessed(rootNode.path, isRoot = true, filesInDir = filesInDir, directoriesInDir = directoriesInDir)
        tracker.onComplete()
        val finalRoot = calculateAggregates(rootNode, children)
        emit(DirectoryScanUpdate.Complete(DirectoryScanResult(finalRoot, errors)))
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

        Result.success(
            DeletionStats(
                itemsDeleted = itemsDeleted,
                bytesFreed = totalSize
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Classification entrypoint used by higher layers (use cases, presenters)
     * and by platform repositories themselves when emitting [ScanError]s.
     *
     * Platform-specific repositories may override this to incorporate native
     * error codes (errno, GetLastError, etc.) while still falling back to the
     * shared message-based heuristics in [classifyErrorMessage].
     */
    open fun classifyError(error: Throwable): ErrorType =
        classifyErrorMessage(error.message)

    /**
     * Default message-based error classification shared across platforms.
     *
     * This provides a stable fallback when platform error codes are not
     * available or not recognised.
     */
    open fun classifyErrorMessage(message: String?): ErrorType {
        if (message == null) return ErrorType.UNKNOWN

        val lower = message.lowercase()

        // Permission / access errors (Unix and Windows)
        if (
            containsAny(
                lower,
                "permission denied",
                "access denied",
                "operation not permitted",
                "read-only file system"
            )
        ) {
            return ErrorType.PERMISSION_DENIED
        }

        // Not-found style errors (Unix and Windows)
        if (
            containsAny(
                lower,
                "not found",
                "no such file",
                "cannot find the file",
                "cannot find the path"
            )
        ) {
            return ErrorType.NOT_FOUND
        }

        // If we recognised the message at all but it didn't match above, treat as IO_ERROR.
        return ErrorType.IO_ERROR
    }

    /**
     * List immediate children of a directory without recursing.
     * Files get real sizes, directories get metadata size with empty children.
     */
    protected abstract suspend fun listDirectoryChildren(
        path: String,
        errors: MutableList<ScanError>
    ): List<FileNode>

    /**
     * Recursively scan a directory and build the file tree.
     *
     * Uses a two-pass pattern: first lists all immediate children (so they are
     * visible in the tree as placeholders), then scans each child directory.
     *
     * @param onSubdirScanned Called after each child directory is fully scanned,
     *   with the partially-built node for this directory. Implementations should
     *   also wrap this callback for recursive calls so that inner completions
     *   propagate aggregated partial state upward.
     * @param scannedPaths Shared set tracking all directories that have been
     *   fully scanned. Each directory path is added after its recursive scan
     *   completes. Used by the UI to show dimmed placeholders for unscanned dirs.
     */
    protected abstract suspend fun scanDirectoryRecursive(
        path: String,
        errors: MutableList<ScanError>,
        tracker: AdaptiveProgressTracker,
        isRoot: Boolean,
        onSubdirScanned: (suspend (FileNode) -> Unit)? = null,
        scannedPaths: MutableSet<String>? = null
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

/** Minimum interval between PartialTree emissions to avoid overwhelming the UI. */
private const val PARTIAL_TREE_MIN_INTERVAL_MS = 200L

private fun containsAny(haystack: String, vararg needles: String): Boolean =
    needles.any { it in haystack }

sealed interface DirectoryScanUpdate {
    data class Progress(val progress: ScanProgress) : DirectoryScanUpdate
    data class PartialTree(
        val root: FileNode,
        val scannedPaths: Set<String>,
        val errors: List<ScanError>
    ) : DirectoryScanUpdate
    data class Complete(val result: FileSystemRepository.DirectoryScanResult) : DirectoryScanUpdate
}
