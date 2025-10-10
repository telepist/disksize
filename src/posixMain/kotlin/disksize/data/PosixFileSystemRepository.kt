package disksize.data

import disksize.domain.model.FileNode
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

    override suspend fun scanDirectory(path: String): Result<FileNode> {
        return try {
            val node = scanDirectoryRecursive(path)
            Result.success(node)
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

    private fun scanDirectoryRecursive(path: String): FileNode {
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
                    val child = scanDirectoryRecursive(childPath)
                    children.add(child)
                } catch (_: Exception) {
                    // MVP1 silently skips inaccessible entries
                    continue
                }
            }
        } finally {
            closedir(dir)
        }

        return node.copy(children = children)
    }

    private fun createFileNode(path: String): FileNode = memScoped {
        val statBuf = alloc<stat>()

        if (stat(path, statBuf.ptr) != 0) {
            throw Exception("Cannot stat file: $path")
        }

        val name = path.substringAfterLast('/')
        val size = statBuf.st_size
        val isDirectory = (statBuf.st_mode.toInt() and S_IFMT) == S_IFDIR

        FileNode(
            path = path,
            name = name.ifEmpty { path },
            size = size,
            isDirectory = isDirectory,
            children = emptyList(),
            lastModified = 0L
        )
    }
}

private fun resolveChildPath(parent: String, childName: String): String = when {
    parent.isEmpty() || parent == "/" -> "/$childName"
    parent.endsWith("/") -> parent + childName
    else -> "$parent/$childName"
}
