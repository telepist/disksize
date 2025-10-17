package disksize.presentation

import disksize.domain.model.FileNode
import disksize.domain.model.ScanProgress
import disksize.domain.model.ScanResult

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
    val directoryItems: List<DirectoryItem> = emptyList(),
    val childDirectoryTotalSize: Long = 0L
) {
    val spinnerFrame: Char
        get() = SPINNER_FRAMES[spinnerIndex % SPINNER_FRAMES.size]

    val totalSize: Long = scanResult?.totalSize ?: 0L
    val fileCount: Int = scanResult?.fileCount ?: 0
    val directoryCount: Int = scanResult?.directoryCount ?: 0
    val scanDurationMs: Long = scanResult?.scanDurationMs ?: 0L
    val selectedDirectory: FileNode? = directoryItems.getOrNull(selectedIndex)?.node
    val warningCount: Int = scanResult?.errors?.size ?: 0
}

fun ExplorerState.withSelection(newIndex: Int): ExplorerState {
    if (directoryItems.isEmpty()) return copy(selectedIndex = 0)
    val bounded = newIndex.coerceIn(0, directoryItems.lastIndex)
    return copy(selectedIndex = bounded)
}

fun ExplorerState.resetSelection(): ExplorerState = copy(selectedIndex = 0)

fun ExplorerState.withScanResult(scanResult: ScanResult): ExplorerState {
    val previousSelectedPath = directoryItems.getOrNull(selectedIndex)?.node?.path
    val items = buildDirectoryItems(scanResult.rootNode, sortOrder)
    val totalChildSize = items.sumOf(DirectoryItem::totalSize)
    val updated = copy(
        currentPath = scanResult.rootPath,
        scanResult = scanResult,
        isLoading = false,
        errorMessage = null,
        spinnerIndex = 0,
        loadingProgress = null,
        directoryItems = items,
        childDirectoryTotalSize = totalChildSize
    )
    val newItems = updated.directoryItems
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
        directoryItems = emptyList(),
        childDirectoryTotalSize = 0L
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
        directoryItems = emptyList(),
        childDirectoryTotalSize = 0L
    )
}

fun ExplorerState.withNextSortOrder(): ExplorerState {
    val newOrder = sortOrder.next()
    val selectedPath = directoryItems.getOrNull(selectedIndex)?.node?.path
    val resortedItems = sortDirectoryItems(directoryItems, newOrder)
    val totalChildSize = resortedItems.sumOf(DirectoryItem::totalSize)
    val newIndex = selectedPath?.let { path ->
        resortedItems.indexOfFirst { it.node.path == path }
    }?.takeIf { it >= 0 } ?: 0
    return copy(
        sortOrder = newOrder,
        directoryItems = resortedItems,
        childDirectoryTotalSize = totalChildSize,
        selectedIndex = newIndex
    )
}

fun ExplorerState.tickSpinner(): ExplorerState = copy(spinnerIndex = spinnerIndex + 1)

fun ExplorerState.withProgress(progress: ScanProgress): ExplorerState {
    return copy(loadingProgress = LoadingProgress.fromDomain(progress))
}

data class DirectoryItem(
    val node: FileNode,
    val totalSize: Long
)

private fun buildDirectoryItems(root: FileNode, sortOrder: SortOrder): List<DirectoryItem> {
    val children = root.children.filter(FileNode::isDirectory)
    val items = children.map { child ->
        DirectoryItem(node = child, totalSize = child.totalSize())
    }
    return sortDirectoryItems(items, sortOrder)
}

private fun sortDirectoryItems(items: List<DirectoryItem>, sortOrder: SortOrder): List<DirectoryItem> {
    return when (sortOrder) {
        SortOrder.SIZE_DESC -> items.sortedWith(
            compareByDescending<DirectoryItem> { it.totalSize }
                .thenBy { it.node.name.lowercase() }
        )
        SortOrder.NAME_ASC -> items.sortedBy { it.node.name.lowercase() }
        SortOrder.DATE_DESC -> items.sortedWith(
            compareByDescending<DirectoryItem> { it.node.lastModified }
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
