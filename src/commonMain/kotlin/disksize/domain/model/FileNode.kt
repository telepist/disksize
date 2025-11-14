package disksize.domain.model

/**
 * Represents a file or directory in the file system.
 *
 * @property path Full path to the file or directory
 * @property name Name of the file or directory (without path)
 * @property size Size in bytes (for directories, this is typically 0 or metadata size)
 * @property isDirectory Whether this node represents a directory
 * @property isSymlink Whether this node is a symbolic link (not followed)
 * @property children Child nodes (files and subdirectories), empty for files
 * @property lastModified Last modification timestamp (Unix epoch milliseconds)
 * @property cachedTotalSize Cached total size including all children (calculated during scan)
 * @property cachedFileCount Cached count of files including all descendants (calculated during scan)
 * @property cachedDirectoryCount Cached count of directories in descendants (calculated during scan)
 */
data class FileNode(
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val children: List<FileNode>,
    val lastModified: Long,
    val cachedTotalSize: Long = size,
    val cachedFileCount: Int = if (isDirectory) 0 else 1,
    val cachedDirectoryCount: Int = 0
) {
    /**
     * Calculate the total size of this node including all children recursively.
     * For files, returns the file size.
     * For directories, returns the sum of all contained files and subdirectories.
     * Uses cached value calculated during scan for O(1) performance.
     */
    fun totalSize(): Long {
        return cachedTotalSize
    }

    /**
     * Check if this directory is empty (has no children).
     * Always returns false for files.
     */
    fun isEmpty(): Boolean {
        return isDirectory && children.isEmpty()
    }

    /**
     * Count total number of files in this node and all children recursively.
     * For files, returns 1.
     * For directories, returns the sum of all files in children.
     * Uses cached value calculated during scan for O(1) performance.
     */
    fun fileCount(): Int {
        return cachedFileCount
    }

    /**
     * Count total number of directories in this node's children recursively.
     * Does not count the node itself.
     * For files, returns 0.
     * Uses cached value calculated during scan for O(1) performance.
     */
    fun directoryCount(): Int {
        return cachedDirectoryCount
    }

    /**
     * Create a copy of this node with updated children and recalculated aggregates.
     * Use this instead of copy(children = ...) to ensure cached aggregates stay consistent.
     */
    fun withChildren(newChildren: List<FileNode>): FileNode {
        // Recalculate aggregates from new children
        var totalSize = size
        var totalFiles = if (isDirectory) 0 else 1
        var totalDirs = 0

        for (child in newChildren) {
            totalSize += child.cachedTotalSize
            totalFiles += child.cachedFileCount
            totalDirs += child.cachedDirectoryCount
            if (child.isDirectory) {
                totalDirs += 1
            }
        }

        return copy(
            children = newChildren,
            cachedTotalSize = totalSize,
            cachedFileCount = totalFiles,
            cachedDirectoryCount = totalDirs
        )
    }
}
