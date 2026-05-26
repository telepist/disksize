package disksize.domain

import disksize.domain.model.FileNode
import disksize.domain.model.FileTreeState
import disksize.domain.model.ScanError
import disksize.domain.model.ScanPhase
import disksize.domain.model.ScanProgress
import disksize.presentation.isSubPathOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.TimeSource

/**
 * Reactive store for the file tree model.
 *
 * All mutations to the file tree (scan updates, deletions) go through this store.
 * The UI observes [state] and derives its view from it.
 */
class FileTreeStore {
    private val _state = MutableStateFlow(FileTreeState(rootPath = ""))
    val state: StateFlow<FileTreeState> = _state

    /** Paths deleted during an active scan — filtered from incoming scan snapshots. */
    private val deletedDuringScan = mutableSetOf<String>()

    /** Reset the store for a new scan of the given path. */
    fun reset(path: String) {
        deletedDuringScan.clear()
        _state.value = FileTreeState(
            rootPath = path,
            scanPhase = ScanPhase.LOADING,
            scanStartTimeMark = TimeSource.Monotonic.markNow()
        )
    }

    /** Update scan progress (file count, bytes, current directory). */
    fun updateProgress(progress: ScanProgress) {
        _state.update { it.copy(scanProgress = progress) }
    }

    /** Apply a partial tree snapshot from the scanner. */
    fun applyPartialTree(root: FileNode, scannedPaths: Set<String>, errors: List<ScanError>) {
        _state.update { current ->
            current.copy(
                rootNode = filterDeletedPaths(root),
                scanPhase = ScanPhase.SCANNING,
                scannedPaths = scannedPaths,
                errors = errors,
                lastPartialScannedBytes = current.scanProgress?.scannedBytes ?: 0L
            )
        }
    }

    /** Apply the final completed tree from the scanner. */
    fun applyComplete(root: FileNode, errors: List<ScanError>, durationMs: Long) {
        val filtered = filterDeletedPaths(root)
        deletedDuringScan.clear()
        _state.update { current ->
            current.copy(
                rootNode = filtered,
                scanPhase = ScanPhase.COMPLETED,
                scannedPaths = emptySet(),
                scanProgress = null,
                errors = errors,
                scanDurationMs = durationMs,
                lastPartialScannedBytes = 0L
            )
        }
    }

    /** Remove a node from the tree (e.g., after successful deletion). */
    fun removeNode(path: String) {
        val phase = _state.value.scanPhase
        if (phase == ScanPhase.LOADING || phase == ScanPhase.SCANNING) {
            deletedDuringScan.add(path)
        }
        _state.update { current ->
            current.copy(rootNode = current.rootNode?.let { removeNodeFromTree(it, path) })
        }
    }

    /** Set an error state (e.g., scan failed). */
    fun setError(message: String) {
        _state.update { current ->
            current.copy(
                scanPhase = ScanPhase.ERROR,
                errorMessage = message,
                scanProgress = null
            )
        }
    }

    /** Restore a previously saved state (e.g., from navigation history). */
    fun restore(savedState: FileTreeState) {
        deletedDuringScan.clear()
        _state.value = savedState
    }

    /** Mark that an in-place subtree refresh has started for [path]. Tree contents are preserved. */
    fun beginSubtreeRefresh(path: String) {
        _state.update { it.copy(refreshingPath = path) }
    }

    /** Replace the subtree at [path] with [newNode] and clear refresh-related transient state. */
    fun completeSubtreeRefresh(path: String, newNode: FileNode) {
        _state.update { current ->
            val root = current.rootNode
            val updatedRoot = when {
                root == null -> null
                root.path == path -> newNode
                else -> replaceInTree(root, path, newNode) ?: root
            }
            current.copy(rootNode = updatedRoot, refreshingPath = null, scanProgress = null)
        }
    }

    /** Clear refresh-related transient state without touching the tree (failure or cancellation). */
    fun clearSubtreeRefresh() {
        _state.update { it.copy(refreshingPath = null, scanProgress = null) }
    }

    private fun filterDeletedPaths(root: FileNode): FileNode {
        if (deletedDuringScan.isEmpty()) return root
        return stripPaths(root, deletedDuringScan)
    }
}

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

private fun stripPaths(node: FileNode, paths: Set<String>): FileNode {
    if (!node.isDirectory) return node
    val updatedChildren = node.children
        .filterNot { child -> paths.any { dp -> child.path == dp || child.path.isSubPathOf(dp) } }
        .map { stripPaths(it, paths) }
    return node.withChildren(updatedChildren)
}

/**
 * Walk [node] looking for [targetPath] and replace that subtree with [replacement].
 * Rebuilds the spine using [FileNode.withChildren] so cached aggregates stay consistent.
 * Returns null if [targetPath] is not found under [node].
 */
internal fun replaceInTree(node: FileNode, targetPath: String, replacement: FileNode): FileNode? {
    if (node.path == targetPath) return replacement
    if (!node.isDirectory) return null
    if (targetPath != node.path && !targetPath.isSubPathOf(node.path)) return null

    var changed = false
    val newChildren = node.children.map { child ->
        if (changed) return@map child
        if (child.path == targetPath) {
            changed = true
            replacement
        } else if (child.isDirectory && (targetPath == child.path || targetPath.isSubPathOf(child.path))) {
            val updated = replaceInTree(child, targetPath, replacement)
            if (updated != null) {
                changed = true
                updated
            } else child
        } else child
    }
    return if (changed) node.withChildren(newChildren) else null
}
