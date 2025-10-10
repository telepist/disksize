package disksize.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathUtilsTest {

    @Test
    fun `normalizePath collapses separators and trims trailing slash`() {
        assertEquals("/tmp", normalizePath("/tmp/"))
        assertEquals("/tmp/data", normalizePath("/tmp//data///"))
        assertEquals("/", normalizePath("/"))
        assertEquals(".", normalizePath(""))
    }

    @Test
    fun `parentPath handles root and nested paths`() {
        assertNull(parentPath("/"))
        assertNull(parentPath(""))
        assertEquals("/", parentPath("/tmp"))
        assertEquals("/tmp", parentPath("/tmp/data"))
        assertEquals("/tmp", parentPath("/tmp/data/"))
    }
}
