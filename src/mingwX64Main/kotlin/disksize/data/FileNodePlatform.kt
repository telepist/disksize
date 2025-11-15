package disksize.data

import disksize.domain.model.FileNode
import kotlinx.cinterop.*
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
internal fun platformCreateFileNode(path: String): FileNode = memScoped {
    val findData = alloc<WIN32_FIND_DATAW>()

    // Normalize path for Windows
    val normalizedPath = WindowsPathUtils.normalize(path)

    // Use FindFirstFile to get file information (works for both files and directories)
    val handle = FindFirstFileW(normalizedPath, findData.ptr)

    if (handle == INVALID_HANDLE_VALUE) {
        throw Exception("Cannot get file info: $path")
    }

    FindClose(handle)

    // Extract file information
    val name = WindowsPathUtils.extractName(path)

    val fileSize = (findData.nFileSizeHigh.toLong() shl 32) or findData.nFileSizeLow.toLong()

    val attrs = findData.dwFileAttributes
    val isDirectory = (attrs and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u
    val isSymlink = (attrs and FILE_ATTRIBUTE_REPARSE_POINT.toUInt()) != 0u

    // Convert FILETIME to milliseconds since epoch
    // FILETIME is in 100-nanosecond intervals since January 1, 1601
    val fileTime = (findData.ftLastWriteTime.dwHighDateTime.toLong() shl 32) or
                   findData.ftLastWriteTime.dwLowDateTime.toLong()

    // Convert Windows FILETIME to Unix timestamp (milliseconds)
    // Windows epoch starts at 1601-01-01, Unix epoch at 1970-01-01
    // Difference is 11644473600 seconds
    val windowsEpochDifference = 116444736000000000L
    val lastModified = (fileTime - windowsEpochDifference) / 10000L

    FileNode(
        path = path,
        name = name,
        size = fileSize,
        isDirectory = isDirectory,
        isSymlink = isSymlink,
        children = emptyList(),
        lastModified = lastModified
    )
}
