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
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.access
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.stat
import kotlin.Exception
import kotlin.collections.ArrayDeque

/**
 * POSIX-based file system repository implementation shared by macOS and Linux targets.
 */
@OptIn(ExperimentalForeignApi::class)
class PosixFileSystemRepository : FileSystemRepository {

    override fun scanDirectory(path: String): Flow<DirectoryScanUpdate> = flow {
        val errors = mutableListOf<ScanError>()
        val totals = countDirectoryEntries(path)
        val tracker = ProgressTracker(
            totalFiles = totals.files,
            totalDirectories = totals.directories,
            emitProgress = { progress ->
                emit(DirectoryScanUpdate.Progress(progress))
            }
        )
        val node = scanDirectoryRecursive(
            path = path,
            errors = errors,
            tracker = tracker,
            isRoot = true
        )
        emit(
            DirectoryScanUpdate.Complete(
                FileSystemRepository.DirectoryScanResult(
                    root = node,
                    errors = errors
                )
            )
        )
    }

    override suspend fun getFileInfo(path: String): Result<FileNode> {
        return try {
            val node = createFileNode(path)
            Result.success(node)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(path: String): Boolean = memScoped {
        val statBuf = alloc<stat>()
        stat(path, statBuf.ptr) == 0
    }

    override suspend fun isAccessible(path: String): Boolean {
        return access(path, platform.posix.R_OK) == 0
    }

    private suspend fun scanDirectoryRecursive(
        path: String,
        errors: MutableList<ScanError>,
        tracker: ProgressTracker,
        isRoot: Boolean
    ): FileNode {
        val node = createFileNode(path)

        if (node.isDirectory) {
            if (isRoot) {
                tracker.onRootEntered(node.path)
            } else {
                tracker.onDirectoryProcessed(node.path)
            }

            val children = mutableListOf<FileNode>()
            val dir = opendir(path) ?: throw Exception("Cannot open directory: $path")

            try {
                while (true) {
                    val entry = readdir(dir) ?: break
                    val name = entry.pointed.d_name.toKString()

                    if (name == "." || name == "..") continue

                    val childPath = resolveChildPath(path, name)

                    try {
                        val child = scanDirectoryRecursive(
                            path = childPath,
                            errors = errors,
                            tracker = tracker,
                            isRoot = false
                        )
                        children.add(child)
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

            return node.copy(children = children)
        } else {
            tracker.onFileProcessed(node.path)
            return node
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun countDirectoryEntries(path: String): DirectoryTotals {
        val rootNode = createFileNode(path)
        if (!rootNode.isDirectory) {
            return DirectoryTotals(files = 0, directories = 0)
        }

        var fileCount = 0
        var directoryCount = 0
        val stack = ArrayDeque<Pair<String, Boolean>>()
        stack.add(path to true)

        while (stack.isNotEmpty()) {
            val (currentPath, isRoot) = stack.removeLast()
            val dir = opendir(currentPath)

            if (dir == null) {
                if (isRoot) {
                    throw Exception("Cannot open directory: $currentPath")
                } else {
                    continue
                }
            }

            try {
                while (true) {
                    val entry = readdir(dir) ?: break
                    val name = entry.pointed.d_name.toKString()
                    if (name == "." || name == "..") continue

                    val childPath = resolveChildPath(currentPath, name)
                    val childNode = try {
                        createFileNode(childPath)
                    } catch (_: Exception) {
                        continue
                    }

                    if (childNode.isDirectory) {
                        directoryCount++
                        stack.add(childPath to false)
                    } else {
                        fileCount++
                    }
                }
            } finally {
                closedir(dir)
            }
        }

        return DirectoryTotals(files = fileCount, directories = directoryCount)
    }

    private fun createFileNode(path: String): FileNode = platformCreateFileNode(path)
}

private data class DirectoryTotals(
    val files: Int,
    val directories: Int
)

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
