package disksize.presentation

import disksize.domain.model.FileNode
import disksize.domain.model.FileTreeState
import disksize.domain.model.ScanPhase
import disksize.domain.model.ScanResult

/**
 * UI-facing state for the directory explorer.
 * Derived from [FileTreeState] (reactive tree model) + [UiSelections] (local UI state).
 */
data class ExplorerState(
    // From FileTreeState
    val currentPath: String,
    val scanResult: ScanResult? = null,
    val isLoading: Boolean = false,
    val isScanInProgress: Boolean = false,
    val scannedPaths: Set<String> = emptySet(),
    val loadingProgress: LoadingProgress? = null,
    val loadingDirectoryPath: String? = null,
    val scanStartTimeMark: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null,

    // UI-local
    val errorMessage: String? = null,
    val selectedIndex: Int = 0,
    val sortOrder: SortOrder = SortOrder.SIZE_DESC,
    val spinnerIndex: Int = 0,
    val confirmDeleteItem: BrowserItem? = null,
    val isDeletingInProgress: Boolean = false,
    val expandedPaths: Set<String> = emptySet(),

    // Derived
    val browserItems: List<BrowserItem> = emptyList(),
    val childDirectoryTotalSize: Long = 0L
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

    internal val scanningDirBaseBytes: Long
        get() = _scanningDirBaseBytes

    // Internal backing field set during derivation
    internal var _scanningDirBaseBytes: Long = 0L
}

/**
 * Mutable UI-local selections that drive the view independently of the tree model.
 */
data class UiSelections(
    val selectedIndex: Int = 0,
    val sortOrder: SortOrder = SortOrder.SIZE_DESC,
    val expandedPaths: Set<String> = emptySet(),
    val confirmDeleteItem: BrowserItem? = null,
    val isDeletingInProgress: Boolean = false,
    val errorMessage: String? = null,
    val spinnerIndex: Int = 0
)

/**
 * Derive the full [ExplorerState] from the reactive tree state and UI selections.
 * Called whenever either input changes.
 */
fun deriveExplorerState(tree: FileTreeState, ui: UiSelections): ExplorerState {
    val rootNode = tree.rootNode
    val isLoading = tree.scanPhase == ScanPhase.LOADING
    val isScanInProgress = tree.scanPhase == ScanPhase.LOADING || tree.scanPhase == ScanPhase.SCANNING
    val progress = tree.scanProgress?.let { LoadingProgress.fromDomain(it) }
    val loadingDirPath = tree.scanProgress?.currentDirectory
    val scanningDirBaseBytes = tree.lastPartialScannedBytes

    val errorMessage = ui.errorMessage ?: tree.errorMessage

    val scanResult = rootNode?.let {
        ScanResult(
            rootPath = tree.rootPath,
            totalSize = it.totalSize(),
            fileCount = it.fileCount(),
            directoryCount = it.directoryCount(),
            rootNode = it,
            scanDurationMs = tree.scanDurationMs,
            errors = tree.errors
        )
    }

    val scanningDirLiveBytes = ((progress?.scannedBytes ?: 0L) - scanningDirBaseBytes).coerceAtLeast(0L)

    val items = if (rootNode != null) {
        buildBrowserItems(
            rootNode, ui.sortOrder, ui.expandedPaths,
            isScanInProgress, tree.scannedPaths,
            loadingDirPath, scanningDirLiveBytes
        )
    } else {
        emptyList()
    }
    val totalChildSize = childDirectoryTotalSize(items)

    val boundedIndex = if (items.isEmpty()) 0 else ui.selectedIndex.coerceIn(0, items.lastIndex)

    return ExplorerState(
        currentPath = tree.rootPath,
        scanResult = scanResult,
        isLoading = isLoading,
        isScanInProgress = isScanInProgress,
        scannedPaths = tree.scannedPaths,
        loadingProgress = progress,
        loadingDirectoryPath = loadingDirPath,
        scanStartTimeMark = tree.scanStartTimeMark,
        errorMessage = errorMessage,
        selectedIndex = boundedIndex,
        sortOrder = ui.sortOrder,
        spinnerIndex = ui.spinnerIndex,
        confirmDeleteItem = ui.confirmDeleteItem,
        isDeletingInProgress = ui.isDeletingInProgress,
        expandedPaths = ui.expandedPaths,
        browserItems = items,
        childDirectoryTotalSize = totalChildSize
    ).also { it._scanningDirBaseBytes = scanningDirBaseBytes }
}

// ── UI selection helpers ──

fun ExplorerState.findParentIndex(currentIndex: Int): Int? {
    if (browserItems.isEmpty()) return null
    val current = browserItems.getOrNull(currentIndex) ?: return null
    if (current.depth == 0) return null
    for (i in currentIndex - 1 downTo 0) {
        if (browserItems[i].depth < current.depth) return i
    }
    return null
}

// ── Data types ──

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

// ── Browser item building ──

internal fun childDirectoryTotalSize(items: List<BrowserItem>): Long {
    return items.filter { it.kind == BrowserItemKind.DIRECTORY && it.depth == 0 }.sumOf(BrowserItem::totalSize)
}

internal fun buildBrowserItems(
    root: FileNode,
    sortOrder: SortOrder,
    expandedPaths: Set<String> = emptySet(),
    isScanInProgress: Boolean = false,
    scannedPaths: Set<String> = emptySet(),
    loadingDirectoryPath: String? = null,
    scanningDirLiveBytes: Long = 0L
): List<BrowserItem> {
    val result = mutableListOf<BrowserItem>()
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

private val SPINNER_FRAMES = charArrayOf(
    '\u2839', '\u28B8', '\u28F0', '\u28E4', '\u28C6', '\u2847', '\u280F', '\u281B',
) // ⠹⢸⣰⣤⣆⡇⠏⠛ – 4 braille dots circling clockwise

/** True when [this] path is a direct or nested child of [parent] (handles both `/` and `\`). */
internal fun String.isSubPathOf(parent: String): Boolean =
    startsWith("$parent/") || startsWith("$parent\\")
