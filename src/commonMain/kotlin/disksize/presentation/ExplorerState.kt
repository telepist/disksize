package disksize.presentation

import disksize.domain.model.FileNode
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
    val spinnerIndex: Int = 0
) {
    val spinnerFrame: Char
        get() = SPINNER_FRAMES[spinnerIndex % SPINNER_FRAMES.size]

    val directories: List<FileNode> =
        scanResult
            ?.rootNode
            ?.children
            ?.filter(FileNode::isDirectory)
            ?.let { children ->
                when (sortOrder) {
                    SortOrder.SIZE_DESC -> children.sortedWith(
                        compareByDescending<FileNode> { it.totalSize() }
                            .thenBy { it.name.lowercase() }
                    )
                    SortOrder.NAME_ASC -> children.sortedBy { it.name.lowercase() }
                    SortOrder.DATE_DESC -> children.sortedWith(
                        compareByDescending<FileNode> { it.lastModified }
                            .thenBy { it.name.lowercase() }
                    )
                }
            }
            ?: emptyList()

    val totalSize: Long = scanResult?.totalSize ?: 0L
    val fileCount: Int = scanResult?.fileCount ?: 0
    val directoryCount: Int = scanResult?.directoryCount ?: 0
    val scanDurationMs: Long = scanResult?.scanDurationMs ?: 0L
    val selectedDirectory: FileNode? = directories.getOrNull(selectedIndex)
    val warningCount: Int = scanResult?.errors?.size ?: 0
}

fun ExplorerState.withSelection(newIndex: Int): ExplorerState {
    if (directories.isEmpty()) return copy(selectedIndex = 0)
    val bounded = newIndex.coerceIn(0, directories.lastIndex)
    return copy(selectedIndex = bounded)
}

fun ExplorerState.resetSelection(): ExplorerState = copy(selectedIndex = 0)

fun ExplorerState.withScanResult(scanResult: ScanResult): ExplorerState {
    val previousSelectedPath = directories.getOrNull(selectedIndex)?.path
    val updated = copy(
        scanResult = scanResult,
        isLoading = false,
        errorMessage = null,
        spinnerIndex = 0
    )
    val newDirectories = updated.directories
    val newIndex = previousSelectedPath?.let { path ->
        newDirectories.indexOfFirst { it.path == path }
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
        spinnerIndex = 0
    )
}

fun ExplorerState.withError(message: String): ExplorerState {
    return copy(
        isLoading = false,
        errorMessage = message,
        scanResult = null,
        selectedIndex = 0,
        spinnerIndex = 0
    )
}

fun ExplorerState.withNextSortOrder(): ExplorerState {
    val newOrder = sortOrder.next()
    val selectedPath = directories.getOrNull(selectedIndex)?.path
    val updated = copy(sortOrder = newOrder)
    val newDirectories = updated.directories
    val newIndex = selectedPath?.let { path ->
        newDirectories.indexOfFirst { it.path == path }
    }?.takeIf { it >= 0 } ?: 0
    return updated.copy(selectedIndex = newIndex)
}

fun ExplorerState.tickSpinner(): ExplorerState = copy(spinnerIndex = spinnerIndex + 1)

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
