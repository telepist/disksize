package disksize.domain.model

sealed interface ScanStatus {
    data class Progress(val value: ScanProgress) : ScanStatus
    data class PartialResult(
        val result: ScanResult,
        val scannedPaths: Set<String>
    ) : ScanStatus
    data class Completed(val result: ScanResult) : ScanStatus
}
