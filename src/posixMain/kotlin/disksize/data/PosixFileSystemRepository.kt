package disksize.data

import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.access
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.rmdir
import platform.posix.stat
import platform.posix.unlink
import kotlin.Exception
import kotlin.collections.ArrayDeque
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
class PosixFileSystemRepository : FileSystemRepository {

    override fun scanDirectory(path: String): Flow<DirectoryScanUpdate> = flow {
        val errors = mutableListOf<ScanError>()

        val tracker = AdaptiveProgressTracker(
            emitProgress = { progress -> emit(DirectoryScanUpdate.Progress(progress)) },
            batchSize = 100,
            minIntervalMs = 50
        )
        val node = scanDirectoryRecursive(path, errors, tracker, isRoot = true)
        tracker.onComplete()
        emit(DirectoryScanUpdate.Complete(FileSystemRepository.DirectoryScanResult(node, errors)))
    }

    private suspend fun scanDirectoryRecursive(
        path: String,
        errors: MutableList<ScanError>,
        tracker: AdaptiveProgressTracker,
        isRoot: Boolean
    ): FileNode {
        val baseNode = createFileNode(path)

        if (!baseNode.isDirectory) {
            tracker.onFileProcessed(baseNode.path, baseNode.size)
            return baseNode.copy(children = emptyList())
        }

        tracker.startDirectory(baseNode.path, isRoot)

        val children = mutableListOf<FileNode>()
        val dir = opendir(path)

        if (dir == null) {
            tracker.onDirectoryProcessed(baseNode.path, isRoot, filesInDir = 0, directoriesInDir = 0)
            throw Exception("Cannot open directory: $path")
        }

        var filesInDir = 0
        var directoriesInDir = 0

        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue

                val childPath = resolveChildPath(path, name)

                try {
                    val childNode = createFileNode(childPath)

                    // Skip symlinks - they are not followed and counted only by their link size
                    if (childNode.isSymlink) {
                        tracker.onFileProcessed(childNode.path, childNode.size)
                        children.add(childNode)
                        filesInDir++
                        continue
                    }

                    if (childNode.isDirectory) {
                        val sanitized = scanDirectoryRecursive(childPath, errors, tracker, isRoot = false)
                        children.add(sanitized)
                        directoriesInDir++
                    } else {
                        tracker.onFileProcessed(childNode.path, childNode.size)
                        children.add(childNode)
                        filesInDir++
                    }
                } catch (e: Exception) {
                    errors += ScanError(
                        path = childPath,
                        message = e.message ?: "Unable to access $childPath",
                        type = classifyError(e)
                    )
                    continue
                }
            }
        } finally {
            closedir(dir)
        }

        tracker.onDirectoryProcessed(baseNode.path, isRoot, filesInDir, directoriesInDir)

        // Calculate aggregates for this directory from its children
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

    override suspend fun getFileInfo(path: String): Result<FileNode> = try {
        Result.success(createFileNode(path))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun exists(path: String): Boolean = memScoped {
        val statBuf = alloc<stat>()
        stat(path, statBuf.ptr) == 0
    }

    override suspend fun isAccessible(path: String): Boolean = access(path, platform.posix.R_OK) == 0

    override suspend fun delete(path: String): Result<FileSystemRepository.DeletionStats> = try {
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

        Result.success(FileSystemRepository.DeletionStats(
            itemsDeleted = itemsDeleted,
            bytesFreed = totalSize
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun deleteFile(path: String) {
        if (unlink(path) != 0) {
            throw Exception("Failed to delete file: $path")
        }
    }

    private fun deleteDirectoryRecursive(path: String): Int {
        var deletedCount = 0

        // Open directory to read contents
        val dir = opendir(path)
        if (dir == null) {
            throw Exception("Cannot open directory for deletion: $path")
        }

        try {
            // Delete all children first
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue

                val childPath = resolveChildPath(path, name)

                try {
                    val childNode = createFileNode(childPath)
                    if (childNode.isDirectory && !childNode.isSymlink) {
                        // Recursively delete subdirectory
                        deletedCount += deleteDirectoryRecursive(childPath)
                    } else {
                        // Delete file or symlink
                        deleteFile(childPath)
                        deletedCount++
                    }
                } catch (e: Exception) {
                    // Continue with other files even if one fails
                    throw Exception("Failed to delete child: $childPath - ${e.message}")
                }
            }
        } finally {
            closedir(dir)
        }

        // Finally, delete the directory itself
        if (rmdir(path) != 0) {
            throw Exception("Failed to delete directory: $path")
        }

        return deletedCount + 1  // +1 for the directory itself
    }

    private fun createFileNode(path: String): FileNode = platformCreateFileNode(path)

}

private class AdaptiveProgressTracker(
    private val emitProgress: suspend (ScanProgress) -> Unit,
    private val batchSize: Int = 100,
    private val minIntervalMs: Long = 50
) {
    private val startTime = kotlin.time.TimeSource.Monotonic.markNow()

    private var processedFiles = 0
    private var processedDirectories = 0
    private var scannedBytes = 0L

    private var filesProcessedSinceLastEmit = 0
    private var lastEmitTime = 0L

    private var currentPath: String? = null

    suspend fun startDirectory(path: String, isRoot: Boolean) {
        currentPath = path
        emitIfBatchReady()
    }

    suspend fun onFileProcessed(path: String, size: Long) {
        processedFiles++
        scannedBytes += size
        filesProcessedSinceLastEmit++
        currentPath = path
        emitIfBatchReady()
    }

    suspend fun onDirectoryProcessed(
        path: String,
        isRoot: Boolean,
        filesInDir: Int,
        directoriesInDir: Int
    ) {
        if (!isRoot) {
            processedDirectories++
        }
        currentPath = path
        emitIfBatchReady()
    }

    private suspend fun emitIfBatchReady() {
        val now = startTime.elapsedNow().inWholeMilliseconds
        val timeSinceEmit = now - lastEmitTime

        if (filesProcessedSinceLastEmit >= batchSize ||
            timeSinceEmit >= minIntervalMs) {
            emit()
            filesProcessedSinceLastEmit = 0
            lastEmitTime = now
        }
    }

    private suspend fun emit() {
        val elapsed = startTime.elapsedNow().inWholeMilliseconds
        val bytesPerSecond = if (elapsed > 0) {
            (scannedBytes * 1000) / elapsed
        } else {
            0L
        }

        emitProgress(ScanProgress(
            processedFiles = processedFiles,
            processedDirectories = processedDirectories,
            scannedBytes = scannedBytes,
            bytesPerSecond = bytesPerSecond,
            currentDirectory = currentPath
        ))
    }

    suspend fun onComplete() {
        // Force final emit with current stats
        emit()
    }
}

private fun resolveChildPath(parent: String, childName: String): String = when {
    parent.isEmpty() || parent == "/" -> "/$childName"
    parent.endsWith("/") -> parent + childName
    else -> "$parent/$childName"
}

private fun classifyError(exception: Exception): ErrorType {
    val message = exception.message?.lowercase() ?: return ErrorType.UNKNOWN
    return when {
        "permission" in message && "denied" in message -> ErrorType.PERMISSION_DENIED
        "not found" in message -> ErrorType.NOT_FOUND
        "no such file" in message -> ErrorType.NOT_FOUND
        else -> ErrorType.IO_ERROR
    }
}

internal expect fun platformCreateFileNode(path: String): FileNode
