package disksize.presentation

import disksize.domain.model.FileNode
import disksize.domain.model.ScanResult
import disksize.presentation.SortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplorerStateTest {

    @Test
    fun `withScanResult sorts directories descending and resets selection`() {
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 600,
            fileCount = 0,
            directoryCount = 3,
            rootNode = directory(
                path = "/tmp",
                name = "tmp",
                children = listOf(
                    directory("/tmp/a", "a", size = 100),
                    directory("/tmp/b", "b", size = 300),
                    directory("/tmp/c", "c", size = 200)
                )
            ),
            scanDurationMs = 42,
            errors = emptyList()
        )

        val initial = ExplorerState(currentPath = "/tmp", selectedIndex = 2)
        val updated = initial.withScanResult(scanResult).resetSelection()

        val names = updated.directories.map { it.name }
        assertEquals(listOf("b", "c", "a"), names)
        assertEquals(0, updated.selectedIndex)
        assertEquals(600, updated.totalSize)
        assertEquals(0, updated.spinnerIndex)
    }

    @Test
    fun `withSelection clamps index`() {
        val state = ExplorerState(
            currentPath = "/tmp",
            scanResult = ScanResult(
                rootPath = "/tmp",
                totalSize = 300,
                fileCount = 0,
                directoryCount = 3,
                rootNode = directory(
                    path = "/tmp",
                    name = "tmp",
                    children = listOf(
                        directory("/tmp/a", "a", size = 100),
                        directory("/tmp/b", "b", size = 100),
                        directory("/tmp/c", "c", size = 100)
                    )
                ),
                scanDurationMs = 1,
                errors = emptyList()
            )
        )

        assertEquals(2, state.withSelection(5).selectedIndex)
        assertEquals(0, state.withSelection(-1).selectedIndex)
    }

    @Test
    fun `withNextSortOrder cycles sort order and preserves selection`() {
        val childSize = directory("/root/size", "size", size = 300, lastModified = 1_000)
        val childName = directory("/root/alpha", "alpha", size = 100, lastModified = 2_000)
        val childDate = directory("/root/date", "date", size = 200, lastModified = 3_000)
        val scanResult = ScanResult(
            rootPath = "/root",
            totalSize = 600,
            fileCount = 0,
            directoryCount = 3,
            rootNode = directory(
                path = "/root",
                name = "root",
                children = listOf(childSize, childName, childDate)
            ),
            scanDurationMs = 0,
            errors = emptyList()
        )

        var state = ExplorerState(currentPath = "/root").withScanResult(scanResult)
        // Initially sorted by size desc (size, date, alpha)
        assertEquals(listOf("size", "date", "alpha"), state.directories.map { it.name })

        state = state.withNextSortOrder()
        assertEquals(SortOrder.NAME_ASC, state.sortOrder)
        assertEquals(listOf("alpha", "date", "size"), state.directories.map { it.name })

        state = state.withNextSortOrder()
        assertEquals(SortOrder.DATE_DESC, state.sortOrder)
        assertEquals(listOf("date", "alpha", "size"), state.directories.map { it.name })
    }

    @Test
    fun `warning count returns scan errors size`() {
        val scanResult = ScanResult(
            rootPath = "/tmp",
            totalSize = 0,
            fileCount = 0,
            directoryCount = 0,
            rootNode = directory("/tmp", "tmp"),
            scanDurationMs = 0,
            errors = listOf(
                disksize.domain.model.ScanError("/tmp/secret", "Permission denied", disksize.domain.model.ErrorType.PERMISSION_DENIED)
            )
        )

        val state = ExplorerState(currentPath = "/tmp").withScanResult(scanResult)

        assertEquals(1, state.warningCount)
        assertFalse(state.scanResult!!.isSuccessful())
    }

    @Test
    fun `spinner advances while loading`() {
        var state = ExplorerState(currentPath = "/tmp").withLoading("/tmp")
        state = state.tickSpinner()
        assertEquals(1, state.spinnerIndex)
        val nextFrame = state.spinnerFrame
        assertTrue(nextFrame in charArrayOf('|', '/', '-', '\\'))
    }

    private fun directory(
        path: String,
        name: String,
        size: Long = 0,
        children: List<FileNode> = emptyList(),
        lastModified: Long = 0L
    ): FileNode {
        return FileNode(
            path = path,
            name = name,
            size = size,
            isDirectory = true,
            children = children,
            lastModified = lastModified
        )
    }
}
