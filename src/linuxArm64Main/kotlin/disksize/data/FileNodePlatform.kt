package disksize.data

import disksize.domain.model.FileNode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformCreateFileNode(path: String): FileNode = memScoped {
    val statBuf = alloc<stat>()

    if (stat(path, statBuf.ptr) != 0) {
        throw Exception("Cannot stat file: $path")
    }

    val name = path.substringAfterLast('/')
    val size = statBuf.st_size
    val isDirectory = (statBuf.st_mode.toInt() and S_IFMT) == S_IFDIR
    val timespec = statBuf.st_mtim
    val lastModified = timespec.tv_sec.toLong() * 1000 + timespec.tv_nsec.toLong() / 1_000_000

    FileNode(
        path = path,
        name = name.ifEmpty { path },
        size = size,
        isDirectory = isDirectory,
        children = emptyList(),
        lastModified = lastModified
    )
}
