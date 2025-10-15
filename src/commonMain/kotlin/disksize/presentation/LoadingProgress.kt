package disksize.presentation

import disksize.domain.model.ScanProgress

/**
 * UI-friendly representation of scan progress.
 */
data class LoadingProgress(
    val processedFiles: Int,
    val totalFiles: Int,
    val processedDirectories: Int,
    val totalDirectories: Int,
    val currentDirectory: String? = null,
    val currentFile: String? = null
) {
    val processedItems: Int
        get() = processedFiles + processedDirectories

    val totalItems: Int
        get() = totalFiles + totalDirectories

    val completionFraction: Double
        get() = when {
            totalItems <= 0 -> 0.0
            processedItems <= 0 -> 0.0
            else -> processedItems.toDouble() / totalItems.toDouble()
        }

    companion object {
        fun fromDomain(progress: ScanProgress): LoadingProgress {
            return LoadingProgress(
                processedFiles = progress.processedFiles,
                totalFiles = progress.totalFiles,
                processedDirectories = progress.processedDirectories,
                totalDirectories = progress.totalDirectories,
                currentDirectory = progress.currentDirectory,
                currentFile = progress.currentFile
            )
        }
    }
}
