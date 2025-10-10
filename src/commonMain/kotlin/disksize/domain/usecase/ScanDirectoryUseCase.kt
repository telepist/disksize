package disksize.domain.usecase

import disksize.data.FileSystemRepository
import disksize.domain.model.ScanResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.TimeSource

/**
 * Use case for scanning a directory and collecting size information.
 *
 * This use case orchestrates the directory scanning process, calculates statistics,
 * and handles errors gracefully.
 */
class ScanDirectoryUseCase(
    private val repository: FileSystemRepository
) {
    /**
     * Execute the directory scan.
     *
     * @param path Absolute path to the directory to scan
     * @return Result containing ScanResult with statistics and file tree, or an error
     */
    suspend fun execute(path: String): Result<ScanResult> {
        val startTime = TimeSource.Monotonic.markNow()

        return try {
            val scanResult = repository.scanDirectory(path)

            if (scanResult.isFailure) {
                return Result.failure(
                    scanResult.exceptionOrNull() ?: Exception("Failed to scan directory")
                )
            }

            val (rootNode, repoErrors) = scanResult.getOrNull()!!

            val totalSize = rootNode.totalSize()
            val fileCount = rootNode.fileCount()
            val directoryCount = rootNode.directoryCount()

            currentCoroutineContext().ensureActive()

            val duration = startTime.elapsedNow().inWholeMilliseconds

            Result.success(
                ScanResult(
                    rootPath = path,
                    totalSize = totalSize,
                    fileCount = fileCount,
                    directoryCount = directoryCount,
                    rootNode = rootNode,
                    scanDurationMs = duration,
                    errors = repoErrors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
