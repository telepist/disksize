package disksize.data.fake

import disksize.data.DirectoryScanUpdate
import disksize.data.FileSystemRepository
import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake implementation of FileSystemRepository for testing.
 * Allows programmatic setup of file system structure.
 */
class FakeFileSystemRepository : FileSystemRepository {
    private val files = mutableMapOf<String, FileNode>()
    private val inaccessiblePaths = mutableSetOf<String>()

    /**
     * Add a file or directory to the fake file system.
     */
    fun addFile(node: FileNode) {
        files[node.path] = node
    }

    /**
     * Mark a path as inaccessible (permission denied).
     */
    fun markInaccessible(path: String) {
        inaccessiblePaths.add(path)
    }

    /**
     * Clear all files from the fake file system.
     */
    fun clear() {
        files.clear()
        inaccessiblePaths.clear()
    }

    override fun scanDirectory(path: String): Flow<DirectoryScanUpdate> = flow {
        if (path in inaccessiblePaths) {
            throw Exception("Permission denied: $path")
        }

        val node = files[path] ?: throw Exception("Directory not found: $path")

        if (!node.isDirectory) {
            throw Exception("Not a directory: $path")
        }

        val errors = mutableListOf<ScanError>()
        val sanitizedChildren = node.children.filter { child ->
            val denied = child.path in inaccessiblePaths
            if (denied) {
                errors += ScanError(
                    path = child.path,
                    message = "Permission denied",
                    type = ErrorType.PERMISSION_DENIED
                )
            }
            !denied
        }

        val sanitizedRoot = node.copy(children = sanitizedChildren)
        val totalFiles = sanitizedRoot.fileCount()
        val totalDirectories = sanitizedRoot.directoryCount()
        val tracker = ProgressTracker(
            totalFiles = totalFiles,
            totalDirectories = totalDirectories,
            emitProgress = { progress ->
                emit(DirectoryScanUpdate.Progress(progress))
            }
        )

        tracker.onRootEntered(sanitizedRoot.path)
        for (child in sanitizedRoot.children) {
            traverseForProgress(child, tracker)
        }

        emit(
            DirectoryScanUpdate.Complete(
                FileSystemRepository.DirectoryScanResult(
                    root = sanitizedRoot,
                    errors = errors
                )
            )
        )
    }

    override suspend fun getFileInfo(path: String): Result<FileNode> {
        if (path in inaccessiblePaths) {
            return Result.failure(Exception("Permission denied: $path"))
        }

        val node = files[path]
            ?: return Result.failure(Exception("File not found: $path"))

        // Return node without children
        return Result.success(
            node.copy(children = emptyList())
        )
    }

    override suspend fun exists(path: String): Boolean {
        return path in files
    }

    override suspend fun isAccessible(path: String): Boolean {
        return path !in inaccessiblePaths && exists(path)
    }
}

private class ProgressTracker(
    private val totalFiles: Int,
    private val totalDirectories: Int,
    private val emitProgress: suspend (ScanProgress) -> Unit
) {
    private var processedFiles: Int = 0
    private var processedDirectories: Int = 0
    private var currentDirectory: String? = null
    private var currentFile: String? = null

    private suspend fun emit() {
        emitProgress(
            ScanProgress(
                processedFiles = processedFiles,
                totalFiles = totalFiles,
                processedDirectories = processedDirectories,
                totalDirectories = totalDirectories,
                currentDirectory = currentDirectory,
                currentFile = currentFile
            )
        )
    }

    suspend fun onRootEntered(path: String) {
        currentDirectory = path
        currentFile = null
        emit()
    }

    suspend fun onDirectoryProcessed(path: String) {
        processedDirectories++
        currentDirectory = path
        currentFile = null
        emit()
    }

    suspend fun onFileProcessed(path: String) {
        processedFiles++
        currentFile = path
        emit()
    }
}

private suspend fun traverseForProgress(node: FileNode, tracker: ProgressTracker) {
    if (node.isDirectory) {
        tracker.onDirectoryProcessed(node.path)
        node.children.forEach { child ->
            traverseForProgress(child, tracker)
        }
    } else {
        tracker.onFileProcessed(node.path)
    }
}
