package disksize.data.fake

import disksize.data.AdaptiveProgressTracker
import disksize.data.FileSystemRepository
import disksize.domain.model.ErrorType
import disksize.domain.model.FileNode
import disksize.domain.model.ScanError

class FakeFileSystemRepository : FileSystemRepository() {
    private val files = mutableMapOf<String, FileNode>()
    private val inaccessiblePaths = mutableSetOf<String>()

    fun addFile(node: FileNode) {
        files[node.path] = node
        // Recursively add all children to the files map
        if (node.isDirectory) {
            node.children.forEach { addFile(it) }
        }
    }

    fun markInaccessible(path: String) {
        inaccessiblePaths.add(path)
    }

    fun clear() {
        files.clear()
        inaccessiblePaths.clear()
    }

    override suspend fun exists(path: String): Boolean = path in files

    override suspend fun isAccessible(path: String): Boolean = path !in inaccessiblePaths && exists(path)

    override fun createFileNode(path: String): FileNode {
        if (path in inaccessiblePaths) throw Exception("Permission denied: $path")
        return files[path] ?: throw Exception("File not found: $path")
    }

    override fun deleteFile(path: String) {
        files.remove(path) ?: throw Exception("File not found: $path")
    }

    override fun deleteDirectoryRecursive(path: String): Int {
        val node = files[path] ?: throw Exception("Directory not found: $path")
        val itemsDeleted = countAndRemove(node)
        return itemsDeleted
    }

    private fun countAndRemove(node: FileNode): Int {
        var count = 1
        files.remove(node.path)

        if (node.isDirectory) {
            node.children.forEach { child ->
                count += countAndRemove(child)
            }
        }

        return count
    }

    override suspend fun scanDirectoryRecursive(
        path: String,
        errors: MutableList<ScanError>,
        tracker: AdaptiveProgressTracker,
        isRoot: Boolean
    ): FileNode {
        if (path in inaccessiblePaths) throw Exception("Permission denied: $path")
        val node = files[path] ?: throw Exception("Directory not found: $path")

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
                val sanitized = scanDirectoryRecursive(child.path, errors, tracker, isRoot = false)
                children += sanitized
                directoriesInDir++
            } else {
                tracker.onFileProcessed(child.path, child.size)
                children += child
                filesInDir++
            }
        }

        tracker.onDirectoryProcessed(node.path, isRoot, filesInDir, directoriesInDir)
        return calculateAggregates(node, children)
    }
}
