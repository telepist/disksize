package disksize.data

import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError
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

/**
 * POSIX-based file system repository implementation shared by macOS and Linux targets.
 */
@OptIn(ExperimentalForeignApi::class)
class PosixFileSystemRepository : FileSystemRepository {

    override suspend fun scanDirectory(path: String): Result<FileSystemRepository.DirectoryScanResult> {
        return try {
            val errors = mutableListOf<ScanError>()
            val node = scanDirectoryRecursive(path, errors)
            Result.success(
                FileSystemRepository.DirectoryScanResult(
                    root = node,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    private fun scanDirectoryRecursive(
        path: String,
        errors: MutableList<ScanError>
    ): FileNode {
        val node = createFileNode(path)

        if (!node.isDirectory) {
            return node
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
                    val child = scanDirectoryRecursive(childPath, errors)
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
    }

    private fun createFileNode(path: String): FileNode = platformCreateFileNode(path)
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
