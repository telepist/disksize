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
import platform.posix.EACCES
import platform.posix.ENOENT
import platform.posix.EPERM
import platform.posix.access
import platform.posix.closedir
import platform.posix.errno
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.rmdir
import platform.posix.stat
import platform.posix.unlink
import kotlin.Exception
import kotlin.collections.ArrayDeque
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
class PosixFileSystemRepository : FileSystemRepository() {

    override fun classifyError(error: Throwable): ErrorType {
        val code = errno
        return when (code) {
            EACCES, EPERM -> ErrorType.PERMISSION_DENIED
            ENOENT -> ErrorType.NOT_FOUND
            else -> super.classifyError(error)
        }
    }

    override suspend fun listDirectoryChildren(
        path: String,
        errors: MutableList<ScanError>
    ): List<FileNode> {
        val children = mutableListOf<FileNode>()
        val dir = opendir(path) ?: throw Exception("Cannot open directory: $path")

        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue

                val childPath = resolveChildPath(path, name)
                try {
                    val childNode = createFileNode(childPath)
                    children.add(childNode)
                } catch (e: Exception) {
                    errors += ScanError(
                        path = childPath,
                        message = e.message ?: "Unable to access $childPath",
                        type = classifyError(e)
                    )
                }
            }
        } finally {
            closedir(dir)
        }

        return children
    }

    override suspend fun scanDirectoryRecursive(
        path: String,
        errors: MutableList<ScanError>,
        tracker: AdaptiveProgressTracker,
        isRoot: Boolean,
        onSubdirScanned: (suspend (FileNode) -> Unit)?,
        scannedPaths: MutableSet<String>?
    ): FileNode {
        val baseNode = createFileNode(path)

        if (!baseNode.isDirectory) {
            tracker.onFileProcessed(baseNode.path, baseNode.size)
            return baseNode.copy(children = emptyList())
        }

        tracker.startDirectory(baseNode.path, isRoot)

        // --- Phase 1: list all immediate children ---
        val children = mutableListOf<FileNode>()
        val dirIndices = mutableListOf<Int>()
        var filesInDir = 0
        var directoriesInDir = 0

        val dir = opendir(path)
        if (dir == null) {
            tracker.onDirectoryProcessed(baseNode.path, isRoot, filesInDir = 0, directoriesInDir = 0)
            throw Exception("Cannot open directory: $path")
        }

        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue

                val childPath = resolveChildPath(path, name)

                try {
                    val childNode = createFileNode(childPath)

                    if (childNode.isDirectory && !childNode.isSymlink) {
                        dirIndices += children.size
                        children.add(childNode.copy(
                            children = emptyList(),
                            cachedTotalSize = childNode.size,
                            cachedFileCount = 0,
                            cachedDirectoryCount = 0
                        ))
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
                }
            }
        } finally {
            closedir(dir)
        }

        // Emit initial aggregate with all children visible (dirs as placeholders)
        onSubdirScanned?.invoke(calculateAggregates(baseNode, children))

        // --- Phase 2: scan each child directory ---
        for (dirIndex in dirIndices) {
            val placeholder = children[dirIndex]

            val wrappedCallback: (suspend (FileNode) -> Unit)? =
                if (onSubdirScanned != null) {
                    { innerPartialNode ->
                        children[dirIndex] = innerPartialNode
                        onSubdirScanned(calculateAggregates(baseNode, children))
                    }
                } else null

            val scannedChild = scanDirectoryRecursive(
                placeholder.path, errors, tracker, isRoot = false, wrappedCallback, scannedPaths
            )
            children[dirIndex] = scannedChild
            scannedPaths?.add(placeholder.path)
            onSubdirScanned?.invoke(calculateAggregates(baseNode, children))
        }

        tracker.onDirectoryProcessed(baseNode.path, isRoot, filesInDir, directoriesInDir)

        return calculateAggregates(baseNode, children)
    }

    override suspend fun exists(path: String): Boolean = memScoped {
        val statBuf = alloc<stat>()
        stat(path, statBuf.ptr) == 0
    }

    override suspend fun isAccessible(path: String): Boolean = access(path, platform.posix.R_OK) == 0

    override fun deleteFile(path: String) {
        if (unlink(path) != 0) {
            throw Exception("Failed to delete file: $path")
        }
    }

    override fun deleteDirectoryRecursive(path: String): Int {
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

    override fun createFileNode(path: String): FileNode = platformCreateFileNode(path)
}

private fun resolveChildPath(parent: String, childName: String): String = when {
    parent.isEmpty() || parent == "/" -> "/$childName"
    parent.endsWith("/") -> parent + childName
    else -> "$parent/$childName"
}

internal expect fun platformCreateFileNode(path: String): FileNode
