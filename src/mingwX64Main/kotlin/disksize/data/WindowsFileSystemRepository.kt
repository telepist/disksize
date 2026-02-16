package disksize.data

import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.cinterop.*
import platform.windows.*
import kotlin.Exception

@OptIn(ExperimentalForeignApi::class)
class WindowsFileSystemRepository : FileSystemRepository() {

    override fun classifyError(error: Throwable): ErrorType {
        val code = GetLastError().toInt()
        return when (code) {
            ERROR_ACCESS_DENIED -> ErrorType.PERMISSION_DENIED
            ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND -> ErrorType.NOT_FOUND
            else -> super.classifyError(error)
        }
    }

    override suspend fun listDirectoryChildren(
        path: String,
        errors: MutableList<ScanError>
    ): List<FileNode> {
        val children = mutableListOf<FileNode>()
        val searchPath = WindowsPathUtils.toSearchPath(path)

        memScoped {
            val findData = alloc<WIN32_FIND_DATAW>()
            val handle = FindFirstFileW(searchPath, findData.ptr)

            if (handle == INVALID_HANDLE_VALUE) {
                throw Exception("Cannot open directory: $path")
            }

            try {
                do {
                    val name = findData.cFileName.toKString()
                    if (name == "." || name == "..") continue

                    val childPath = WindowsPathUtils.joinPath(path, name)
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
                } while (FindNextFileW(handle, findData.ptr) != 0)
            } finally {
                FindClose(handle)
            }
        }

        return children
    }

    override suspend fun scanDirectoryRecursive(
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
        var filesInDir = 0
        var directoriesInDir = 0

        val searchPath = WindowsPathUtils.toSearchPath(path)

        memScoped {
            val findData = alloc<WIN32_FIND_DATAW>()
            val handle = FindFirstFileW(searchPath, findData.ptr)

            if (handle == INVALID_HANDLE_VALUE) {
                tracker.onDirectoryProcessed(baseNode.path, isRoot, filesInDir = 0, directoriesInDir = 0)
                val exception = Exception("Cannot open directory: $path")
                errors += ScanError(
                    path = path,
                    message = exception.message ?: "Cannot open directory: $path",
                    type = classifyError(exception)
                )
                return baseNode.copy(children = emptyList())
            }

            try {
                do {
                    val name = findData.cFileName.toKString()
                    if (name == "." || name == "..") continue

                    val childPath = WindowsPathUtils.joinPath(path, name)

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
                } while (FindNextFileW(handle, findData.ptr) != 0)
            } finally {
                FindClose(handle)
            }
        }

        tracker.onDirectoryProcessed(baseNode.path, isRoot, filesInDir, directoriesInDir)

        return calculateAggregates(baseNode, children)
    }

    override suspend fun exists(path: String): Boolean = memScoped {
        val attrs = GetFileAttributesW(path)
        attrs != INVALID_FILE_ATTRIBUTES
    }

    override suspend fun isAccessible(path: String): Boolean {
        // On Windows, if we can get attributes, the file is accessible
        return exists(path)
    }

    override fun deleteFile(path: String) {
        if (DeleteFileW(path) == 0) {
            throw Exception("Failed to delete file: $path")
        }
    }

    override fun deleteDirectoryRecursive(path: String): Int {
        var deletedCount = 0

        val searchPath = WindowsPathUtils.toSearchPath(path)

        memScoped {
            val findData = alloc<WIN32_FIND_DATAW>()
            val handle = FindFirstFileW(searchPath, findData.ptr)

            if (handle == INVALID_HANDLE_VALUE) {
                throw Exception("Cannot open directory for deletion: $path")
            }

            try {
                // Delete all children first
                do {
                    val name = findData.cFileName.toKString()
                    if (name == "." || name == "..") continue

                    val childPath = WindowsPathUtils.joinPath(path, name)

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
                        // Include partial deletion count in error message
                        throw Exception("Failed to delete child: $childPath - ${e.message} ($deletedCount items deleted before failure)")
                    }
                } while (FindNextFileW(handle, findData.ptr) != 0)
            } finally {
                FindClose(handle)
            }
        }

        // Finally, delete the directory itself
        if (RemoveDirectoryW(path) == 0) {
            throw Exception("Failed to delete directory: $path")
        }

        return deletedCount + 1  // +1 for the directory itself
    }

    override fun createFileNode(path: String): FileNode = platformCreateFileNode(path)
}
