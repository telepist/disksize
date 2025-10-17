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
    val browserItems: List<BrowserItem> = emptyList(),
    val childDirectoryTotalSize: Long = 0L,
    val loadingDirectoryPath: String? = null
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
        loadingDirectoryPath = null
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
