package disksize

import com.jakewharton.mosaic.runMosaic
import disksize.data.PosixFileSystemRepository
import disksize.domain.usecase.ScanDirectoryUseCase
import disksize.ui.MainScreen
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import platform.posix.getenv

/**
 * Main entry point for DiskSize application.
 */
@OptIn(ExperimentalForeignApi::class)
fun main(args: Array<String>) {
    // Parse command-line arguments
    val targetPath = if (args.isNotEmpty()) {
        args[0]
    } else {
        // Default to current directory
        getCurrentDirectory()
    }

    println("DiskSize - Scanning directory: $targetPath")
    println()

    runBlocking {
        // Set up dependencies
        val repository = PosixFileSystemRepository()
        val scanUseCase = ScanDirectoryUseCase(repository)

        // Execute scan
        val result = scanUseCase.execute(targetPath)

        if (result.isFailure) {
            println("Error: ${result.exceptionOrNull()?.message}")
            return@runBlocking
        }

        val scanResult = result.getOrNull()!!

        // Display TUI
        runMosaic {
            MainScreen(scanResult)
        }
    }
}

/**
 * Get the current working directory.
 */
@OptIn(ExperimentalForeignApi::class)
private fun getCurrentDirectory(): String {
    val pwd = getenv("PWD")?.toKString()
    return pwd ?: "."
}
