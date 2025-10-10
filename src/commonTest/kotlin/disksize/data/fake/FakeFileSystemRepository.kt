package disksize.data.fake

import disksize.data.FileSystemRepository
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
import disksize.domain.model.ErrorType

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

    override suspend fun scanDirectory(path: String): Result<FileSystemRepository.DirectoryScanResult> {
        if (path in inaccessiblePaths) {
            return Result.failure(Exception("Permission denied: $path"))
        }

        val node = files[path]
            ?: return Result.failure(Exception("Directory not found: $path"))

        if (!node.isDirectory) {
            return Result.failure(Exception("Not a directory: $path"))
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

        return Result.success(
            FileSystemRepository.DirectoryScanResult(
                root = node.copy(children = sanitizedChildren),
                errors = errors
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
