package disksize.presentation

import disksize.domain.model.FileNode
import disksize.domain.model.ScanProgress
import disksize.domain.model.ScanResult
import kotlin.time.TimeSource

/**
 * UI-facing state for the directory explorer.
 */
data class ExplorerState(
    val currentPath: String,
    val scanResult: ScanResult? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedIndex: Int = 0,
    val sortOrder: SortOrder = SortOrder.SIZE_DESC,
    val spinnerIndex: Int = 0,
    val loadingProgress: LoadingProgress? = null,
    val browserItems: List<BrowserItem> = emptyList(),
    val childDirectoryTotalSize: Long = 0L,
    val loadingDirectoryPath: String? = null,
    val confirmDeleteItem: BrowserItem? = null,
    val scanStartTimeMark: TimeSource.Monotonic.ValueTimeMark? = null
) {
    val spinnerFrame: Char
        get() = SPINNER_FRAMES[spinnerIndex % SPINNER_FRAMES.size]

    val totalSize: Long = scanResult?.totalSize ?: 0L
    val fileCount: Int = scanResult?.fileCount ?: 0
    val directoryCount: Int = scanResult?.directoryCount ?: 0
    val scanDurationMs: Long = scanResult?.scanDurationMs ?: 0L
    val selectedItem: BrowserItem? = browserItems.getOrNull(selectedIndex)
    val selectedDirectory: FileNode? = selectedItem?.takeIf { it.kind == BrowserItemKind.DIRECTORY }?.node
    val warningCount: Int = scanResult?.errors?.size ?: 0
}

fun ExplorerState.withSelection(newIndex: Int): ExplorerState {
    if (browserItems.isEmpty()) return copy(selectedIndex = 0)
    val bounded = newIndex.coerceIn(0, browserItems.lastIndex)
    return copy(selectedIndex = bounded)
}

fun ExplorerState.resetSelection(): ExplorerState = copy(selectedIndex = 0)

fun ExplorerState.withScanResult(scanResult: ScanResult): ExplorerState {
    val previousSelectedPath = browserItems.getOrNull(selectedIndex)?.node?.path
    val items = buildBrowserItems(scanResult.rootNode, sortOrder)
    val totalChildSize = items.filter { it.kind == BrowserItemKind.DIRECTORY }.sumOf(BrowserItem::totalSize)
    val updated = copy(
        currentPath = scanResult.rootPath,
        scanResult = scanResult,
        isLoading = false,
        errorMessage = null,
        spinnerIndex = 0,
        loadingProgress = null,
        browserItems = items,
        childDirectoryTotalSize = totalChildSize,
        loadingDirectoryPath = null
    )
    val newItems = updated.browserItems
    val newIndex = previousSelectedPath?.let { path ->
        newItems.indexOfFirst { it.node.path == path }
    }?.takeIf { it >= 0 } ?: 0
    return updated.copy(selectedIndex = newIndex)
}

fun ExplorerState.withLoading(path: String): ExplorerState {
    return copy(
        currentPath = path,
        isLoading = true,
        errorMessage = null,
        scanResult = null,
        selectedIndex = 0,
        spinnerIndex = 0,
        loadingProgress = null,
        browserItems = emptyList(),
        childDirectoryTotalSize = 0L,
        loadingDirectoryPath = null,
        scanStartTimeMark = TimeSource.Monotonic.markNow()
    )
}

fun ExplorerState.withError(message: String): ExplorerState {
    return copy(
        isLoading = false,
        errorMessage = message,
        scanResult = null,
        selectedIndex = 0,
        spinnerIndex = 0,
        loadingProgress = null,
        browserItems = emptyList(),
        childDirectoryTotalSize = 0L,
        loadingDirectoryPath = null
    )
}

fun ExplorerState.withNextSortOrder(): ExplorerState {
    val newOrder = sortOrder.next()
    val selectedPath = browserItems.getOrNull(selectedIndex)?.node?.path
    val resortedItems = sortBrowserItems(browserItems, newOrder)
    val totalChildSize = resortedItems.filter { it.kind == BrowserItemKind.DIRECTORY }.sumOf(BrowserItem::totalSize)
    val newIndex = selectedPath?.let { path ->
        resortedItems.indexOfFirst { it.node.path == path }
    }?.takeIf { it >= 0 } ?: 0
    return copy(
        sortOrder = newOrder,
        browserItems = resortedItems,
        childDirectoryTotalSize = totalChildSize,
        selectedIndex = newIndex
    )
}

fun ExplorerState.tickSpinner(): ExplorerState = copy(spinnerIndex = spinnerIndex + 1)

fun ExplorerState.withProgress(progress: ScanProgress): ExplorerState {
    val updatedProgress = LoadingProgress.fromDomain(progress)
    val directoryPath = progress.currentDirectory ?: loadingDirectoryPath
    return copy(
        loadingProgress = updatedProgress,
        loadingDirectoryPath = directoryPath
    )
}

fun ExplorerState.withConfirmDelete(item: BrowserItem): ExplorerState {
    return copy(confirmDeleteItem = item)
}

fun ExplorerState.cancelConfirmDelete(): ExplorerState {
    return copy(confirmDeleteItem = null)
}

fun ExplorerState.withItemDeleted(deletedPath: String): ExplorerState {
    // Update the scan result to remove the deleted node from the tree
    val updatedScanResult = scanResult?.let { result ->
        val updatedRoot = removeNodeFromTree(result.rootNode, deletedPath)
        result.copy(
            rootNode = updatedRoot,
            totalSize = updatedRoot.totalSize(),
            fileCount = updatedRoot.fileCount(),
            directoryCount = updatedRoot.directoryCount()
        )
    }

    // Rebuild browser items from the updated tree so they reference the new nodes
    val updatedItems = updatedScanResult?.let { result ->
        buildBrowserItems(result.rootNode, sortOrder)
    } ?: emptyList()

    val totalChildSize = updatedItems.filter { it.kind == BrowserItemKind.DIRECTORY }.sumOf(BrowserItem::totalSize)

    // Adjust selected index if needed
    val newIndex = if (updatedItems.isEmpty()) {
        0
    } else {
        selectedIndex.coerceIn(0, updatedItems.lastIndex)
    }

    return copy(
        scanResult = updatedScanResult,
        browserItems = updatedItems,
        childDirectoryTotalSize = totalChildSize,
        selectedIndex = newIndex,
        confirmDeleteItem = null
    )
}

fun ExplorerState.withNodeUpdated(pathToUpdate: String, newNode: FileNode): ExplorerState {
    // Update the scan result to replace the node at the given path
    val updatedScanResult = scanResult?.let { result ->
        if (result.rootPath == pathToUpdate) {
            // The root itself was updated
            result.copy(
                rootNode = newNode,
                totalSize = newNode.totalSize(),
                fileCount = newNode.fileCount(),
                directoryCount = newNode.directoryCount()
            )
        } else {
            // Update a node somewhere in the tree
            val updatedRoot = updateNodeInTree(result.rootNode, pathToUpdate, newNode)
            result.copy(
                rootNode = updatedRoot,
                totalSize = updatedRoot.totalSize(),
                fileCount = updatedRoot.fileCount(),
                directoryCount = updatedRoot.directoryCount()
            )
        }
    }

    // Rebuild browser items from the updated tree so they reference the new nodes
    val updatedItems = updatedScanResult?.let { result ->
        buildBrowserItems(result.rootNode, sortOrder)
    } ?: emptyList()

    val totalChildSize = updatedItems.filter { it.kind == BrowserItemKind.DIRECTORY }.sumOf(BrowserItem::totalSize)

    return copy(
        scanResult = updatedScanResult,
        browserItems = updatedItems,
        childDirectoryTotalSize = totalChildSize
    )
}

private fun updateNodeInTree(node: FileNode, pathToUpdate: String, newNode: FileNode): FileNode {
    // If this is the node to update, replace it
    if (node.path == pathToUpdate) {
        return newNode
    }

    // If this is a directory, recursively update children
    if (node.isDirectory) {
        val updatedChildren = node.children.map { child ->
            if (child.path == pathToUpdate || pathToUpdate.startsWith("${child.path}/")) {
                updateNodeInTree(child, pathToUpdate, newNode)
            } else {
                child
            }
        }
        return node.withChildren(updatedChildren)
    }

    return node
}

private fun removeNodeFromTree(node: FileNode, pathToRemove: String): FileNode {
    // If this is the node to remove, return it as empty (shouldn't happen at root)
    if (node.path == pathToRemove) {
        return node
    }

    // If this is a directory, filter out the deleted child
    if (node.isDirectory) {
        val updatedChildren = node.children
            .filterNot { it.path == pathToRemove }
            .map { removeNodeFromTree(it, pathToRemove) }
        return node.withChildren(updatedChildren)
    }

    return node
}

data class BrowserItem(
    val node: FileNode,
    val totalSize: Long,
    val kind: BrowserItemKind
)

enum class BrowserItemKind { DIRECTORY, FILE }

private fun buildBrowserItems(root: FileNode, sortOrder: SortOrder): List<BrowserItem> {
    val directories = root.children.filter(FileNode::isDirectory).map { child ->
        BrowserItem(node = child, totalSize = child.totalSize(), kind = BrowserItemKind.DIRECTORY)
    }
    val files = root.children.filterNot(FileNode::isDirectory).map { file ->
        BrowserItem(node = file, totalSize = file.totalSize(), kind = BrowserItemKind.FILE)
    }
    val sortedDirs = sortBrowserItems(directories, sortOrder)
    val sortedFiles = sortBrowserItems(files, sortOrder)
    return sortedDirs + sortedFiles
}

private fun sortBrowserItems(items: List<BrowserItem>, sortOrder: SortOrder): List<BrowserItem> {
    return when (sortOrder) {
        SortOrder.SIZE_DESC -> items.sortedWith(
            compareByDescending<BrowserItem> { it.totalSize }
                .thenBy { it.node.name.lowercase() }
        )
        SortOrder.NAME_ASC -> items.sortedBy { it.node.name.lowercase() }
        SortOrder.DATE_DESC -> items.sortedWith(
            compareByDescending<BrowserItem> { it.node.lastModified }
                .thenBy { it.node.name.lowercase() }
        )
    }
}

enum class SortOrder(val label: String) {
    SIZE_DESC("Size ↓"),
    NAME_ASC("Name ↑"),
    DATE_DESC("Date ↓");

    fun next(): SortOrder = when (this) {
        SIZE_DESC -> NAME_ASC
        NAME_ASC -> DATE_DESC
        DATE_DESC -> SIZE_DESC
    }
}

private val SPINNER_FRAMES = "|/-\\".toCharArray()
