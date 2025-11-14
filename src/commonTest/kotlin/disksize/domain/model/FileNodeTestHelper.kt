package disksize.domain.model

/**
 * Test helper to create FileNode instances with correct cached aggregates.
 * This simulates what the repository does during scanning.
 */
fun createFileNode(
    path: String,
    name: String,
    size: Long,
    isDirectory: Boolean,
    isSymlink: Boolean = false,
    children: List<FileNode> = emptyList(),
    lastModified: Long = 0L
): FileNode {
    // Calculate aggregates from children (like the repository does)
    var totalSize = size
    var totalFiles = if (isDirectory) 0 else 1
    var totalDirs = 0

    for (child in children) {
        totalSize += child.cachedTotalSize
        totalFiles += child.cachedFileCount
        totalDirs += child.cachedDirectoryCount
        if (child.isDirectory) {
            totalDirs += 1
        }
    }

    return FileNode(
        path = path,
        name = name,
        size = size,
        isDirectory = isDirectory,
        isSymlink = isSymlink,
        children = children,
        lastModified = lastModified,
        cachedTotalSize = totalSize,
        cachedFileCount = totalFiles,
        cachedDirectoryCount = totalDirs
    )
}
