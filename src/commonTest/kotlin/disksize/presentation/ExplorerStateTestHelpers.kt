package disksize.presentation

import disksize.domain.model.FileNode
import disksize.domain.model.FileTreeState
import disksize.domain.model.ScanPhase
import disksize.domain.model.ScanProgress
import disksize.domain.model.ScanResult

/**
 * Test-only helpers that recreate the old ExplorerState mutation API
 * using the new derivation-based approach.
 */

/** Extract UiSelections from an ExplorerState. */
fun ExplorerState.toUiSelections(): UiSelections = UiSelections(
    selectedIndex = selectedIndex,
    sortOrder = sortOrder,
    expandedPaths = expandedPaths,
    confirmDeleteItem = confirmDeleteItem,
    isDeletingInProgress = isDeletingInProgress,
    errorMessage = errorMessage,
    spinnerIndex = spinnerIndex
)

/** Extract a FileTreeState from an ExplorerState. */
fun ExplorerState.toFileTreeState(): FileTreeState = FileTreeState(
    rootPath = currentPath,
    rootNode = scanResult?.rootNode,
    scanPhase = when {
        isLoading -> ScanPhase.LOADING
        isScanInProgress -> ScanPhase.SCANNING
        scanResult != null -> ScanPhase.COMPLETED
        errorMessage != null -> ScanPhase.ERROR
        else -> ScanPhase.IDLE
    },
    scannedPaths = scannedPaths,
    scanProgress = when {
        loadingProgress != null -> ScanProgress(
            processedFiles = loadingProgress!!.processedFiles,
            processedDirectories = loadingProgress!!.processedDirectories,
            scannedBytes = loadingProgress!!.scannedBytes,
            bytesPerSecond = loadingProgress!!.bytesPerSecond,
            currentDirectory = loadingDirectoryPath
        )
        loadingDirectoryPath != null -> ScanProgress(
            processedFiles = 0, processedDirectories = 0,
            scannedBytes = 0, bytesPerSecond = 0,
            currentDirectory = loadingDirectoryPath
        )
        else -> null
    },
    scanDurationMs = scanDurationMs,
    errors = scanResult?.errors ?: emptyList(),
    scanStartTimeMark = scanStartTimeMark,
    lastPartialScannedBytes = _scanningDirBaseBytes
)

/** Create an ExplorerState from a completed scan result. */
fun ExplorerState.withScanResult(scanResult: ScanResult): ExplorerState {
    val tree = FileTreeState(
        rootPath = scanResult.rootPath,
        rootNode = scanResult.rootNode,
        scanPhase = ScanPhase.COMPLETED,
        scanDurationMs = scanResult.scanDurationMs,
        errors = scanResult.errors
    )
    return deriveExplorerState(tree, toUiSelections())
}

/** Create an ExplorerState from a partial scan result. */
fun ExplorerState.withPartialScanResult(scanResult: ScanResult, scannedPaths: Set<String>): ExplorerState {
    val tree = toFileTreeState().copy(
        rootPath = scanResult.rootPath,
        rootNode = scanResult.rootNode,
        scanPhase = ScanPhase.SCANNING,
        scannedPaths = scannedPaths,
        errors = scanResult.errors,
        lastPartialScannedBytes = loadingProgress?.scannedBytes ?: 0L
    )
    return deriveExplorerState(tree, toUiSelections())
}

/** Set loading state. */
fun ExplorerState.withLoading(path: String): ExplorerState {
    val tree = FileTreeState(
        rootPath = path,
        scanPhase = ScanPhase.LOADING,
        scanStartTimeMark = kotlin.time.TimeSource.Monotonic.markNow()
    )
    return deriveExplorerState(tree, UiSelections())
}

/** Set error state. */
fun ExplorerState.withError(message: String): ExplorerState {
    val tree = FileTreeState(
        rootPath = currentPath,
        scanPhase = ScanPhase.ERROR,
        errorMessage = message
    )
    return deriveExplorerState(tree, toUiSelections())
}

/** Apply progress update. */
fun ExplorerState.withProgress(progress: ScanProgress): ExplorerState {
    val currentDir = progress.currentDirectory ?: loadingDirectoryPath
    val tree = toFileTreeState().copy(
        scanProgress = ScanProgress(
            processedFiles = progress.processedFiles,
            processedDirectories = progress.processedDirectories,
            scannedBytes = progress.scannedBytes,
            bytesPerSecond = progress.bytesPerSecond,
            currentDirectory = currentDir
        )
    )
    return deriveExplorerState(tree, toUiSelections())
}

/** Toggle expand/collapse a directory. */
fun ExplorerState.withToggleExpand(path: String): ExplorerState {
    val newExpanded = if (path in expandedPaths) {
        expandedPaths.filterNot { p -> p == path || p.isSubPathOf(path) }.toSet()
    } else {
        expandedPaths + path
    }
    // Preserve selected item by path
    val selectedPath = browserItems.getOrNull(selectedIndex)?.node?.path
    val tree = toFileTreeState()
    val newUi = toUiSelections().copy(expandedPaths = newExpanded)
    val derived = deriveExplorerState(tree, newUi)
    val newIndex = selectedPath?.let { p ->
        derived.browserItems.indexOfFirst { it.node.path == p }
    }?.takeIf { it >= 0 } ?: 0
    return deriveExplorerState(tree, newUi.copy(selectedIndex = newIndex))
}

/** Change sort order. */
fun ExplorerState.withNextSortOrder(): ExplorerState {
    val newOrder = sortOrder.next()
    val selectedPath = browserItems.getOrNull(selectedIndex)?.node?.path
    val tree = toFileTreeState()
    val newUi = toUiSelections().copy(sortOrder = newOrder)
    val derived = deriveExplorerState(tree, newUi)
    val newIndex = selectedPath?.let { p ->
        derived.browserItems.indexOfFirst { it.node.path == p }
    }?.takeIf { it >= 0 } ?: 0
    return deriveExplorerState(tree, newUi.copy(selectedIndex = newIndex))
}

/** Change selection. */
fun ExplorerState.withSelection(newIndex: Int): ExplorerState {
    if (browserItems.isEmpty()) return deriveExplorerState(toFileTreeState(), toUiSelections().copy(selectedIndex = 0))
    val bounded = newIndex.coerceIn(0, browserItems.lastIndex)
    return deriveExplorerState(toFileTreeState(), toUiSelections().copy(selectedIndex = bounded))
}

/** Reset selection to 0. */
fun ExplorerState.resetSelection(): ExplorerState =
    deriveExplorerState(toFileTreeState(), toUiSelections().copy(selectedIndex = 0))

/** Remove a deleted node from the tree. */
fun ExplorerState.withItemDeleted(deletedPath: String): ExplorerState {
    val updatedExpandedPaths = expandedPaths.filterNot { p ->
        p == deletedPath || p.isSubPathOf(deletedPath)
    }.toSet()

    val updatedRoot = scanResult?.rootNode?.let { removeNodeFromTree(it, deletedPath) }
    val tree = toFileTreeState().copy(rootNode = updatedRoot)
    val newUi = toUiSelections().copy(
        expandedPaths = updatedExpandedPaths,
        confirmDeleteItem = null,
        isDeletingInProgress = false
    )
    val derived = deriveExplorerState(tree, newUi)
    val boundedIndex = if (derived.browserItems.isEmpty()) 0
        else selectedIndex.coerceIn(0, derived.browserItems.lastIndex)
    return deriveExplorerState(tree, newUi.copy(selectedIndex = boundedIndex))
}

/** Update a node in the tree. */
fun ExplorerState.withNodeUpdated(pathToUpdate: String, newNode: FileNode): ExplorerState {
    val updatedRoot = scanResult?.rootNode?.let { updateNodeInTree(it, pathToUpdate, newNode) }
    val tree = toFileTreeState().copy(rootNode = updatedRoot)
    return deriveExplorerState(tree, toUiSelections())
}

/** Advance spinner. */
fun ExplorerState.tickSpinner(): ExplorerState =
    deriveExplorerState(toFileTreeState(), toUiSelections().copy(spinnerIndex = spinnerIndex + 1))

// ── Tree manipulation helpers ──

private fun removeNodeFromTree(node: FileNode, pathToRemove: String): FileNode {
    if (node.path == pathToRemove) return node
    if (node.isDirectory) {
        val updatedChildren = node.children
            .filterNot { it.path == pathToRemove }
            .map { removeNodeFromTree(it, pathToRemove) }
        return node.withChildren(updatedChildren)
    }
    return node
}

private fun updateNodeInTree(node: FileNode, pathToUpdate: String, newNode: FileNode): FileNode {
    if (node.path == pathToUpdate) return newNode
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
