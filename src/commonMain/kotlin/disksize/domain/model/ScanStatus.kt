package disksize.domain.model

sealed interface ScanStatus {
    data class Progress(val value: ScanProgress) : ScanStatus
    data class Completed(val result: ScanResult) : ScanStatus
}
