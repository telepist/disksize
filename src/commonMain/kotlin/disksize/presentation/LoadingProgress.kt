package disksize.presentation

import disksize.domain.model.ScanProgress

/**
 * UI-friendly representation of scan progress.
 * Progress is indeterminate - shows stats without estimating completion.
 */
data class LoadingProgress(
    val processedFiles: Int,
    val processedDirectories: Int,
    val scannedBytes: Long,
    val bytesPerSecond: Long,
    val currentDirectory: String? = null
) {
    companion object {
        fun fromDomain(progress: ScanProgress): LoadingProgress {
            return LoadingProgress(
                processedFiles = progress.processedFiles,
                processedDirectories = progress.processedDirectories,
                scannedBytes = progress.scannedBytes,
                bytesPerSecond = progress.bytesPerSecond,
                currentDirectory = progress.currentDirectory
            )
        }
    }
}
