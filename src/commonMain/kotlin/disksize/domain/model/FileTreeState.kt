package disksize.domain.model

import kotlin.time.TimeSource

/**
 * Reactive state of the file tree model.
 * Owned by [disksize.domain.FileTreeStore] and observed by the UI.
 */
data class FileTreeState(
    val rootPath: String,
    val rootNode: FileNode? = null,
    val scanPhase: ScanPhase = ScanPhase.IDLE,
    val scannedPaths: Set<String> = emptySet(),
    val scanProgress: ScanProgress? = null,
    val scanDurationMs: Long = 0,
    val errors: List<ScanError> = emptyList(),
    val errorMessage: String? = null,
    val scanStartTimeMark: TimeSource.Monotonic.ValueTimeMark? = null,
    /** Bytes scanned at the time of the last partial tree emission. */
    val lastPartialScannedBytes: Long = 0L
)

enum class ScanPhase { IDLE, LOADING, SCANNING, COMPLETED, ERROR }
