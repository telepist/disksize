package disksize.data

import disksize.domain.model.ScanProgress

/**
 * Adaptive progress tracker for directory scanning operations.
 * Emits progress updates at configurable intervals to balance responsiveness and performance.
 *
 * @param emitProgress Callback to emit progress updates
 * @param batchSize Number of files to process before checking if an update should be emitted
 * @param minIntervalMs Minimum time interval in milliseconds between progress updates
 */
internal class AdaptiveProgressTracker(
    private val emitProgress: suspend (ScanProgress) -> Unit,
    private val batchSize: Int = 100,
    private val minIntervalMs: Long = 50
) {
    private val startTime = kotlin.time.TimeSource.Monotonic.markNow()

    private var processedFiles = 0
    private var processedDirectories = 0
    private var scannedBytes = 0L

    private var filesProcessedSinceLastEmit = 0
    private var lastEmitTime = 0L

    private var currentPath: String? = null

    suspend fun startDirectory(path: String, isRoot: Boolean) {
        currentPath = path
        emitIfBatchReady()
    }

    suspend fun onFileProcessed(path: String, size: Long) {
        processedFiles++
        scannedBytes += size
        filesProcessedSinceLastEmit++
        currentPath = path
        emitIfBatchReady()
    }

    suspend fun onDirectoryProcessed(
        path: String,
        isRoot: Boolean,
        filesInDir: Int,
        directoriesInDir: Int
    ) {
        if (!isRoot) {
            processedDirectories++
        }
        currentPath = path
        emitIfBatchReady()
    }

    private suspend fun emitIfBatchReady() {
        val now = startTime.elapsedNow().inWholeMilliseconds
        val timeSinceEmit = now - lastEmitTime

        if (filesProcessedSinceLastEmit >= batchSize ||
            timeSinceEmit >= minIntervalMs) {
            emit()
            filesProcessedSinceLastEmit = 0
            lastEmitTime = now
        }
    }

    private suspend fun emit() {
        val elapsed = startTime.elapsedNow().inWholeMilliseconds
        val bytesPerSecond = if (elapsed > 0) {
            (scannedBytes * 1000) / elapsed
        } else {
            0L
        }

        emitProgress(ScanProgress(
            processedFiles = processedFiles,
            processedDirectories = processedDirectories,
            scannedBytes = scannedBytes,
            bytesPerSecond = bytesPerSecond,
            currentDirectory = currentPath
        ))
    }

    suspend fun onComplete() {
        // Force final emit with current stats
        emit()
    }
}

/**
 * Classify an exception into an appropriate error type based on its message.
 */
internal fun classifyError(exception: Exception): disksize.domain.model.ErrorType {
    val message = exception.message?.lowercase() ?: return disksize.domain.model.ErrorType.UNKNOWN
    return when {
        "permission" in message && "denied" in message -> disksize.domain.model.ErrorType.PERMISSION_DENIED
        "access" in message && "denied" in message -> disksize.domain.model.ErrorType.PERMISSION_DENIED
        "not found" in message -> disksize.domain.model.ErrorType.NOT_FOUND
        "no such file" in message -> disksize.domain.model.ErrorType.NOT_FOUND
        else -> disksize.domain.model.ErrorType.IO_ERROR
    }
}
