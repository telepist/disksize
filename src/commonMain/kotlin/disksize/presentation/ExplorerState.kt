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
    val isScanInProgress: Boolean = false,
    val scannedPaths: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val selectedIndex: Int = 0,
    val sortOrder: SortOrder = SortOrder.SIZE_DESC,
    val spinnerIndex: Int = 0,
    val loadingProgress: LoadingProgress? = null,
    val browserItems: List<BrowserItem> = emptyList(),
    val childDirectoryTotalSize: Long = 0L,
    val loadingDirectoryPath: String? = null,
    val confirmDeleteItem: BrowserItem? = null,
    val isDeletingInProgress: Boolean = false,
    val scanStartTimeMark: TimeSource.Monotonic.ValueTimeMark? = null,
    val expandedPaths: Set<String> = emptySet(),
    val scanningDirBaseBytes: Long = 0L
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
    val scanningDirLiveBytes: Long
        get() = ((loadingProgress?.scannedBytes ?: 0L) - scanningDirBaseBytes).coerceAtLeast(0L)
}

fun ExplorerState.withSelection(newIndex: Int): ExplorerState {
    if (browserItems.isEmpty()) return copy(selectedIndex = 0)
    val bounded = newIndex.coerceIn(0, browserItems.lastIndex)
    return copy(selectedIndex = bounded)
}

fun ExplorerState.resetSelection(): ExplorerState = copy(selectedIndex = 0)

fun ExplorerState.withScanResult(scanResult: ScanResult): ExplorerState {
    val previousSelectedPath = browserItems.getOrNull(selectedIndex)?.node?.path
    val items = buildBrowserItems(scanResult.rootNode, sortOrder, expandedPaths)
    val totalChildSize = childDirectoryTotalSize(items)
    val updated = copy(
        currentPath = scanResult.rootPath,
        scanResult = scanResult,
        isLoading = false,
        isScanInProgress = false,
        scannedPaths = emptySet(),
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

fun ExplorerState.withPartialScanResult(scanResult: ScanResult, scannedPaths: Set<String>): ExplorerState {
    val previousSelectedPath = browserItems.getOrNull(selectedIndex)?.node?.path
    val items = buildBrowserItems(
        scanResult.rootNode, sortOrder, expandedPaths,
        isScanInProgress = true, scannedPaths = scannedPaths,
        loadingDirectoryPath = loadingDirectoryPath,
        scanningDirLiveBytes = scanningDirLiveBytes
    )

    val totalChildSize = childDirectoryTotalSize(items)
    val updated = copy(
        currentPath = scanResult.rootPath,
        scanResult = scanResult,
        isLoading = false,
        isScanInProgress = true,
        scannedPaths = scannedPaths,
        errorMessage = null,
        browserItems = items,
        childDirectoryTotalSize = totalChildSize,
        loadingDirectoryPath = null,
        scanningDirBaseBytes = loadingProgress?.scannedBytes ?: 0L
    )
    val newIndex = previousSelectedPath?.let { path ->
        updated.browserItems.indexOfFirst { it.node.path == path }
    }?.takeIf { it >= 0 } ?: updated.selectedIndex.coerceIn(0, (updated.browserItems.size - 1).coerceAtLeast(0))
    return updated.copy(selectedIndex = newIndex)
}

fun ExplorerState.withLoading(path: String): ExplorerState {
    return copy(
        currentPath = path,
        isLoading = true,
        isScanInProgress = true,
        scannedPaths = emptySet(),
        errorMessage = null,
        scanResult = null,
        selectedIndex = 0,
        spinnerIndex = 0,
        loadingProgress = null,
        browserItems = emptyList(),
        childDirectoryTotalSize = 0L,
        loadingDirectoryPath = null,
        scanStartTimeMark = TimeSource.Monotonic.markNow(),
        expandedPaths = emptySet()
    )
}

fun ExplorerState.withError(message: String): ExplorerState {
    return copy(
        isLoading = false,
        isScanInProgress = false,
        scannedPaths = emptySet(),
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
    val root = scanResult?.rootNode ?: return copy(sortOrder = newOrder)
    val resortedItems = buildBrowserItems(root, newOrder, expandedPaths, isScanInProgress, scannedPaths, loadingDirectoryPath, scanningDirLiveBytes)
    val totalChildSize = childDirectoryTotalSize(resortedItems)
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
    val base = copy(
        loadingProgress = updatedProgress,
        loadingDirectoryPath = directoryPath
    )

    // Re-sort items so the scanning directory's position reflects its live
    // bytes rather than the stale tree size from the last partial result.
    val root = base.scanResult?.rootNode
    if (root == null || !base.isScanInProgress || base.sortOrder != SortOrder.SIZE_DESC) return base
    if (base.scanningDirLiveBytes <= 0L) return base

    val selectedPath = base.browserItems.getOrNull(base.selectedIndex)?.node?.path
    val items = buildBrowserItems(
        root, base.sortOrder, base.expandedPaths,
        isScanInProgress = true, scannedPaths = base.scannedPaths,
        loadingDirectoryPath = base.loadingDirectoryPath,
        scanningDirLiveBytes = base.scanningDirLiveBytes
    )
    val totalChildSize = childDirectoryTotalSize(items)
    val newIndex = selectedPath?.let { path ->
        items.indexOfFirst { it.node.path == path }
    }?.takeIf { it >= 0 } ?: base.selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    return base.copy(
        browserItems = items,
        childDirectoryTotalSize = totalChildSize,
        selectedIndex = newIndex
    )
}

fun ExplorerState.withToggleExpand(path: String): ExplorerState {
    val root = scanResult?.rootNode ?: return this
    val newExpanded = if (path in expandedPaths) {
        // Collapse: remove path and all descendants
        expandedPaths.filterNot { p -> p == path || p.isSubPathOf(path) }.toSet()
    } else {
        expandedPaths + path
    }
    val selectedPath = browserItems.getOrNull(selectedIndex)?.node?.path
    val items = buildBrowserItems(root, sortOrder, newExpanded, isScanInProgress, scannedPaths, loadingDirectoryPath, scanningDirLiveBytes)
    val totalChildSize = childDirectoryTotalSize(items)
    val newIndex = selectedPath?.let { p ->
        items.indexOfFirst { it.node.path == p }
    }?.takeIf { it >= 0 } ?: 0
    return copy(
        expandedPaths = newExpanded,
        browserItems = items,
        childDirectoryTotalSize = totalChildSize,
        selectedIndex = newIndex
    )
}

fun ExplorerState.findParentIndex(currentIndex: Int): Int? {
    if (browserItems.isEmpty()) return null
    val current = browserItems.getOrNull(currentIndex) ?: return null
    if (current.depth == 0) return null
    for (i in currentIndex - 1 downTo 0) {
        if (browserItems[i].depth < current.depth) return i
    }
    return null
}

fun ExplorerState.withConfirmDelete(item: BrowserItem): ExplorerState {
    return copy(confirmDeleteItem = item)
}

fun ExplorerState.cancelConfirmDelete(): ExplorerState {
    return copy(confirmDeleteItem = null, isDeletingInProgress = false)
}

fun ExplorerState.startDeleting(): ExplorerState {
    return copy(isDeletingInProgress = true)
}

fun ExplorerState.withItemDeleted(deletedPath: String): ExplorerState {
    // Remove deleted path and any children from expandedPaths
    val updatedExpandedPaths = expandedPaths.filterNot { p ->
        p == deletedPath || p.isSubPathOf(deletedPath)
    }.toSet()

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
        buildBrowserItems(result.rootNode, sortOrder, updatedExpandedPaths, isScanInProgress, scannedPaths, loadingDirectoryPath, scanningDirLiveBytes)
    } ?: emptyList()

    val totalChildSize = childDirectoryTotalSize(updatedItems)

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
        confirmDeleteItem = null,
        isDeletingInProgress = false,
        expandedPaths = updatedExpandedPaths
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
        buildBrowserItems(result.rootNode, sortOrder, expandedPaths, isScanInProgress, scannedPaths, loadingDirectoryPath, scanningDirLiveBytes)
    } ?: emptyList()

    val totalChildSize = childDirectoryTotalSize(updatedItems)

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
            if (child.path == pathToUpdate || pathToUpdate.isSubPathOf(child.path)) {
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
    val kind: BrowserItemKind,
    val depth: Int = 0,
    val treePrefix: String = "",
    val parentTotalSize: Long = 0L,
    val isExpanded: Boolean = false,
    val isScanned: Boolean = true,
    val isScanning: Boolean = false
)

enum class BrowserItemKind { DIRECTORY, FILE }

private fun childDirectoryTotalSize(items: List<BrowserItem>): Long {
    return items.filter { it.kind == BrowserItemKind.DIRECTORY && it.depth == 0 }.sumOf(BrowserItem::totalSize)
}

private fun buildBrowserItems(
    root: FileNode,
    sortOrder: SortOrder,
    expandedPaths: Set<String> = emptySet(),
    isScanInProgress: Boolean = false,
    scannedPaths: Set<String> = emptySet(),
    loadingDirectoryPath: String? = null,
    scanningDirLiveBytes: Long = 0L
): List<BrowserItem> {
    val result = mutableListOf<BrowserItem>()
    // Use live bytes for the scanning directory's effective size so that
    // both sorting and parentTotalSize reflect the in-progress size.
    val parentTotalSize = root.children.filter(FileNode::isDirectory).sumOf { child ->
        val isScanning = isScanInProgress && loadingDirectoryPath != null &&
            (loadingDirectoryPath == child.path || loadingDirectoryPath.isSubPathOf(child.path))
        if (isScanning && scanningDirLiveBytes > 0) {
            scanningDirLiveBytes.coerceAtLeast(child.totalSize())
        } else {
            child.totalSize()
        }
    }
    flattenChildren(root, sortOrder, expandedPaths, depth = 0, ancestorIsLast = emptyList(), parentTotalSize = parentTotalSize, isScanInProgress = isScanInProgress, scannedPaths = scannedPaths, loadingDirectoryPath = loadingDirectoryPath, scanningDirLiveBytes = scanningDirLiveBytes, result = result)
    return result
}

private fun flattenChildren(
    parent: FileNode,
    sortOrder: SortOrder,
    expandedPaths: Set<String>,
    depth: Int,
    ancestorIsLast: List<Boolean>,
    parentTotalSize: Long,
    isScanInProgress: Boolean,
    scannedPaths: Set<String>,
    loadingDirectoryPath: String?,
    scanningDirLiveBytes: Long = 0L,
    result: MutableList<BrowserItem>
) {
    val directories = parent.children.filter(FileNode::isDirectory).map { child ->
        // At depth 0, use live bytes for the scanning directory so sorting reflects
        // the in-progress size rather than the stale tree size.
        val isScanning = depth == 0 && isScanInProgress && loadingDirectoryPath != null &&
            (loadingDirectoryPath == child.path || loadingDirectoryPath.isSubPathOf(child.path))
        val effectiveSize = if (isScanning && scanningDirLiveBytes > 0) {
            scanningDirLiveBytes.coerceAtLeast(child.totalSize())
        } else {
            child.totalSize()
        }
        BrowserItem(node = child, totalSize = effectiveSize, kind = BrowserItemKind.DIRECTORY)
    }
    val files = parent.children.filterNot(FileNode::isDirectory).map { file ->
        BrowserItem(node = file, totalSize = file.totalSize(), kind = BrowserItemKind.FILE)
    }
    val sortedDirs = sortBrowserItems(directories, sortOrder)
    val sortedFiles = sortBrowserItems(files, sortOrder)
    val allItems = sortedDirs + sortedFiles
    val totalChildren = allItems.size

    for ((index, item) in allItems.withIndex()) {
        val isLast = index == totalChildren - 1
        val prefix = if (depth == 0) "" else buildTreePrefix(ancestorIsLast, isLast)
        val isExpanded = item.kind == BrowserItemKind.DIRECTORY && item.node.path in expandedPaths
        val isScanned = when {
            item.kind != BrowserItemKind.DIRECTORY -> true
            !isScanInProgress -> true
            else -> item.node.path in scannedPaths
        }
        val isScanning = when {
            item.kind != BrowserItemKind.DIRECTORY -> false
            !isScanInProgress -> false
            item.node.path in scannedPaths -> false
            loadingDirectoryPath == null -> false
            else -> loadingDirectoryPath == item.node.path || loadingDirectoryPath.isSubPathOf(item.node.path)
        }
        val itemWithTree = item.copy(
            depth = depth,
            treePrefix = prefix,
            parentTotalSize = if (item.kind == BrowserItemKind.DIRECTORY) parentTotalSize else 0L,
            isExpanded = isExpanded,
            isScanned = isScanned,
            isScanning = isScanning
        )
        result += itemWithTree

        if (isExpanded) {
            val childDirTotalSize = item.node.children.filter(FileNode::isDirectory).sumOf { it.totalSize() }
            flattenChildren(
                parent = item.node,
                sortOrder = sortOrder,
                expandedPaths = expandedPaths,
                depth = depth + 1,
                ancestorIsLast = ancestorIsLast + isLast,
                parentTotalSize = childDirTotalSize,
                isScanInProgress = isScanInProgress,
                scannedPaths = scannedPaths,
                loadingDirectoryPath = loadingDirectoryPath,
                scanningDirLiveBytes = scanningDirLiveBytes,
                result = result
            )
        }
    }
}

internal fun buildTreePrefix(ancestorIsLast: List<Boolean>, isLast: Boolean): String {
    val sb = StringBuilder()
    // Skip the first entry in ancestorIsLast - it corresponds to the item's own parent level
    // which is already covered by the connector character
    for (i in 0 until ancestorIsLast.size - 1) {
        sb.append(if (ancestorIsLast[i]) "    " else "│   ")
    }
    sb.append(if (isLast) "└── " else "├── ")
    return sb.toString()
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

private val SPINNER_FRAMES = charArrayOf(
    '\u2839', '\u28B8', '\u28F0', '\u28E4', '\u28C6', '\u2847', '\u280F', '\u281B',
) // ⠹⢸⣰⣤⣆⡇⠏⠛ – 4 braille dots circling clockwise

/** True when [this] path is a direct or nested child of [parent] (handles both `/` and `\`). */
internal fun String.isSubPathOf(parent: String): Boolean =
    startsWith("$parent/") || startsWith("$parent\\")
