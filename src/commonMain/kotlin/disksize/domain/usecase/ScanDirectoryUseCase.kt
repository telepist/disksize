package disksize.domain.usecase

import disksize.data.DirectoryScanUpdate
import disksize.data.FileSystemRepository
import disksize.domain.FileTreeStore
import disksize.domain.model.ScanResult
import disksize.domain.model.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.TimeSource

class ScanDirectoryUseCase(
    private val repository: FileSystemRepository,
    private val scanDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Scan a directory and push updates into the reactive [store].
     * The store's state flow will automatically update subscribers (UI).
     * Cancellation-safe: if the coroutine is cancelled mid-scan, the store
     * retains whatever partial state was last applied.
     */
    suspend fun scanInto(path: String, store: FileTreeStore) {
        store.reset(path)
        repository.scanDirectory(path).flowOn(scanDispatcher).collect { update ->
            when (update) {
                is DirectoryScanUpdate.Progress -> store.updateProgress(update.progress)
                is DirectoryScanUpdate.PartialTree -> {
                    currentCoroutineContext().ensureActive()
                    store.applyPartialTree(update.root, update.scannedPaths, update.errors)
                }
                is DirectoryScanUpdate.Complete -> {
                    currentCoroutineContext().ensureActive()
                    val startMark = store.state.value.scanStartTimeMark
                    val duration = startMark?.elapsedNow()?.inWholeMilliseconds ?: 0L
                    store.applyComplete(update.result.root, update.result.errors, duration)
                }
            }
        }
    }

    /** Flow-based scan for backward compatibility and testing. */
    fun scan(path: String): Flow<ScanStatus> = flow {
        val startTime = TimeSource.Monotonic.markNow()

        repository.scanDirectory(path).collect { update ->
            when (update) {
                is DirectoryScanUpdate.Progress -> emit(ScanStatus.Progress(update.progress))
                is DirectoryScanUpdate.PartialTree -> {
                    val rootNode = update.root

                    currentCoroutineContext().ensureActive()

                    val duration = startTime.elapsedNow().inWholeMilliseconds
                    val result = ScanResult(
                        rootPath = path,
                        totalSize = rootNode.totalSize(),
                        fileCount = rootNode.fileCount(),
                        directoryCount = rootNode.directoryCount(),
                        rootNode = rootNode,
                        scanDurationMs = duration,
                        errors = update.errors
                    )
                    emit(ScanStatus.PartialResult(result, update.scannedPaths))
                }
                is DirectoryScanUpdate.Complete -> {
                    val rootNode = update.result.root
                    val repoErrors = update.result.errors

                    val totalSize = rootNode.totalSize()
                    val fileCount = rootNode.fileCount()
                    val directoryCount = rootNode.directoryCount()

                    currentCoroutineContext().ensureActive()

                    val duration = startTime.elapsedNow().inWholeMilliseconds
                    val result = ScanResult(
                        rootPath = path,
                        totalSize = totalSize,
                        fileCount = fileCount,
                        directoryCount = directoryCount,
                        rootNode = rootNode,
                        scanDurationMs = duration,
                        errors = repoErrors
                    )
                    emit(ScanStatus.Completed(result))
                }
            }
        }
    }
}
