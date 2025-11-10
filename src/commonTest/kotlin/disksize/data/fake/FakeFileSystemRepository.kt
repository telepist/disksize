package disksize.data.fake

import disksize.data.DirectoryScanUpdate
import disksize.data.FileSystemRepository
import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.collections.ArrayDeque
import kotlin.math.max

class FakeFileSystemRepository : FileSystemRepository {
    private val files = mutableMapOf<String, FileNode>()
    private val inaccessiblePaths = mutableSetOf<String>()

    fun addFile(node: FileNode) {
        files[node.path] = node
    }

    fun markInaccessible(path: String) {
        inaccessiblePaths.add(path)
    }

    fun clear() {
        files.clear()
        inaccessiblePaths.clear()
    }

    override fun scanDirectory(path: String): Flow<DirectoryScanUpdate> = flow {
        if (path in inaccessiblePaths) throw Exception("Permission denied: $path")
        val root = files[path] ?: throw Exception("Directory not found: $path")
        if (!root.isDirectory) throw Exception("Not a directory: $path")

        val errors = mutableListOf<ScanError>()

        val tracker = AdaptiveProgressTracker(
            emitProgress = { progress -> emit(DirectoryScanUpdate.Progress(progress)) },
            batchSize = 100,
            minIntervalMs = 50
        )
        val sanitized = traverseAndSanitize(root, tracker, errors, isRoot = true)
        tracker.onComplete()
        emit(DirectoryScanUpdate.Complete(FileSystemRepository.DirectoryScanResult(sanitized, errors)))
    }

    override suspend fun getFileInfo(path: String): Result<FileNode> {
        if (path in inaccessiblePaths) return Result.failure(Exception("Permission denied: $path"))
        val node = files[path] ?: return Result.failure(Exception("File not found: $path"))
        return Result.success(node.copy(children = emptyList()))
    }

    override suspend fun exists(path: String): Boolean = path in files

    override suspend fun isAccessible(path: String): Boolean = path !in inaccessiblePaths && exists(path)

    override suspend fun delete(path: String): Result<FileSystemRepository.DeletionStats> {
        val node = files[path] ?: return Result.failure(Exception("File not found: $path"))
        if (path in inaccessiblePaths) return Result.failure(Exception("Permission denied: $path"))

        val itemsDeleted = countItems(node)
        val bytesFreed = node.totalSize()

        // Remove the node and all its children from the files map
        removeNode(node)

        return Result.success(FileSystemRepository.DeletionStats(
            itemsDeleted = itemsDeleted,
            bytesFreed = bytesFreed
        ))
    }

    private fun countItems(node: FileNode): Int {
        return if (node.isDirectory) {
            1 + node.children.sumOf { countItems(it) }
        } else {
            1
        }
    }

    private fun removeNode(node: FileNode) {
        files.remove(node.path)
        if (node.isDirectory) {
            node.children.forEach { removeNode(it) }
        }
    }

    private suspend fun traverseAndSanitize(
        node: FileNode,
        tracker: AdaptiveProgressTracker,
        errors: MutableList<ScanError>,
        isRoot: Boolean
    ): FileNode {
        if (!node.isDirectory) {
            tracker.onFileProcessed(node.path, node.size)
            return node.copy(children = emptyList())
        }

        tracker.startDirectory(node.path, isRoot)

        val children = mutableListOf<FileNode>()
        var filesInDir = 0
        var directoriesInDir = 0

        for (child in node.children) {
            if (child.path in inaccessiblePaths) {
                errors += ScanError(
                    path = child.path,
                    message = "Permission denied",
                    type = ErrorType.PERMISSION_DENIED
                )
                continue
            }

            if (child.isDirectory) {
                val sanitized = traverseAndSanitize(child, tracker, errors, isRoot = false)
                children += sanitized
                directoriesInDir++
            } else {
                tracker.onFileProcessed(child.path, child.size)
                children += child
                filesInDir++
            }
        }

        tracker.onDirectoryProcessed(node.path, isRoot, filesInDir, directoriesInDir)
        return node.copy(children = children)
    }

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
