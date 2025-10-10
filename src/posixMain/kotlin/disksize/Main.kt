package disksize

import com.jakewharton.mosaic.runMosaic
import disksize.data.PosixFileSystemRepository
import disksize.domain.usecase.ScanDirectoryUseCase
import disksize.ui.MainScreen
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import platform.posix.PATH_MAX
import platform.posix.getcwd

/**
 * Shared entry point for POSIX native targets (macOS, Linux).
 */
@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>) {
    val targetPath = if (args.isNotEmpty()) {
        args[0]
    } else {
        getCurrentDirectory()
    }

    println("DiskSize - Scanning directory: $targetPath")
    println()

    runBlocking {
        val repository = PosixFileSystemRepository()
        val scanUseCase = ScanDirectoryUseCase(repository)

        val result = scanUseCase.execute(targetPath)

        if (result.isFailure) {
            println("Error: ${result.exceptionOrNull()?.message}")
            return@runBlocking
        }

        val scanResult = result.getOrNull()!!

        runMosaic {
            MainScreen(scanResult)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun getCurrentDirectory(): String = memScoped {
    val bufferSize = PATH_MAX + 1
    val buffer = allocArray<ByteVar>(bufferSize)
    val result = getcwd(buffer, bufferSize.toULong())
    result?.toKString() ?: "."
}
