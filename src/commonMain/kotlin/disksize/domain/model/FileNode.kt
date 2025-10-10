package disksize.domain.model

/**
 * Represents a file or directory in the file system.
 *
 * @property path Full path to the file or directory
 * @property name Name of the file or directory (without path)
 * @property size Size in bytes (for directories, this is typically 0 or metadata size)
 * @property isDirectory Whether this node represents a directory
 * @property children Child nodes (files and subdirectories), empty for files
 * @property lastModified Last modification timestamp (Unix epoch milliseconds)
 */
data class FileNode(
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val children: List<FileNode>,
    val lastModified: Long
) {
    /**
     * Calculate the total size of this node including all children recursively.
     * For files, returns the file size.
     * For directories, returns the sum of all contained files and subdirectories.
     */
    fun totalSize(): Long {
        return if (isDirectory) {
            size + children.sumOf { it.totalSize() }
        } else {
            size
        }
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
     */
    fun fileCount(): Int {
        return if (isDirectory) {
            children.sumOf { it.fileCount() }
        } else {
            1
        }
    }

    /**
     * Count total number of directories in this node's children recursively.
     * Does not count the node itself.
     * For files, returns 0.
     */
    fun directoryCount(): Int {
        return if (isDirectory) {
            children.count { it.isDirectory } +
                children.filter { it.isDirectory }.sumOf { it.directoryCount() }
        } else {
            0
        }
    }
}
