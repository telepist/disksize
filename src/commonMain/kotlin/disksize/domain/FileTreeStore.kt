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
