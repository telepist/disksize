package disksize.domain.usecase

import disksize.data.DirectoryScanUpdate
import disksize.data.FileSystemRepository
import disksize.domain.model.ScanResult
import disksize.domain.model.ScanStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

class ScanDirectoryUseCase(
    private val repository: FileSystemRepository
) {
    fun scan(path: String): Flow<ScanStatus> = flow {
        val startTime = TimeSource.Monotonic.markNow()

        repository.scanDirectory(path).collect { update ->
            when (update) {
                is DirectoryScanUpdate.Progress -> emit(ScanStatus.Progress(update.progress))
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
