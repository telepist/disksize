package disksize

import com.jakewharton.mosaic.runMosaic
import disksize.data.WindowsFileSystemRepository
import disksize.domain.usecase.DeleteFileUseCase
import disksize.domain.usecase.ScanDirectoryUseCase
import disksize.ui.DiskSizeApp
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.windows.GetCurrentDirectoryW
import platform.windows.MAX_PATH

/**
 * Entry point for Windows native target.
 */
@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>) {
    val targetPath = if (args.isNotEmpty()) {
        args[0]
    } else {
        getCurrentDirectory()
    }

    runBlocking {
        val repository = WindowsFileSystemRepository()
        val scanUseCase = ScanDirectoryUseCase(repository)
        val deleteUseCase = DeleteFileUseCase(repository)

        runMosaic {
            DiskSizeApp(
                initialPath = targetPath,
                scanDirectoryUseCase = scanUseCase,
                deleteFileUseCase = deleteUseCase
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun getCurrentDirectory(): String = memScoped {
    val bufferSize = MAX_PATH.toInt()
    val buffer: CPointer<UShortVar> = allocArray(bufferSize)
    val length = GetCurrentDirectoryW(bufferSize.toUInt(), buffer)
    if (length == 0u) {
        "."
    } else {
        buffer.toKString()
    }
}
