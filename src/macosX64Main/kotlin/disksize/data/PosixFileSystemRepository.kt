package disksize.data

import disksize.domain.model.FileNode
import kotlinx.cinterop.*
import platform.posix.*

/**
 * POSIX-based file system repository implementation for macOS and Linux.
 * Uses native POSIX APIs for file system access.
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

    override suspend fun exists(path: String): Boolean {
        return memScoped {
            val statBuf = alloc<stat>()
            stat(path, statBuf.ptr) == 0
        }
    }

    override suspend fun isAccessible(path: String): Boolean {
        return access(path, R_OK) == 0
    }

    private fun scanDirectoryRecursive(path: String): FileNode {
        val node = createFileNode(path)

        if (!node.isDirectory) {
            return node
        }

        // Scan children
        val children = mutableListOf<FileNode>()
        val dir = opendir(path) ?: throw Exception("Cannot open directory: $path")

        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()

                // Skip . and ..
                if (name == "." || name == "..") continue

                val childPath = "$path/$name"

                try {
                    val child = scanDirectoryRecursive(childPath)
                    children.add(child)
                } catch (e: Exception) {
                    // Skip inaccessible files/directories
                    // In MVP 1, we silently skip errors
                    continue
                }
            }
        } finally {
            closedir(dir)
        }

        return node.copy(children = children)
    }

    private fun createFileNode(path: String): FileNode {
        return memScoped {
            val statBuf = alloc<stat>()

            if (stat(path, statBuf.ptr) != 0) {
                throw Exception("Cannot stat file: $path")
            }

            val name = path.substringAfterLast('/')
            val size = statBuf.st_size
            val isDirectory = (statBuf.st_mode.toInt() and S_IFMT) == S_IFDIR
            // For MVP 1, use simple timestamp - st_mtimespec is macOS-specific
            val lastModified = 0L

            FileNode(
                path = path,
                name = name.ifEmpty { path },
                size = size,
                isDirectory = isDirectory,
                children = emptyList(),
                lastModified = lastModified
            )
        }
    }
}
