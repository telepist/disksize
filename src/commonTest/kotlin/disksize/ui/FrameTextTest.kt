package disksize.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameTextTest {

    @Test
    fun `shortenPath returns original when within limit`() {
        val original = "/tmp/path"
        val limit = 20
        val result = shortenPath(original, limit)
        assertEquals(original, result.trimEnd())
        assertEquals(limit, result.length)
    }

    @Test
    fun `shortenPath truncates with ellipsis when exceeding limit`() {
        val result = shortenPath("/Users/example/Documents/projects", 10)
        assertEquals("...rojects", result)
    }

    @Test
    fun `shortenPath handles very small limit`() {
        val result = shortenPath("/foo/bar", 3)
        assertEquals("/fo", result)
    }

    @Test
    fun `truncateWithEllipsis preserves shorter text`() {
        val original = "hello"
        val limit = 10
        val result = truncateWithEllipsis(original, limit)
        assertEquals(original, result.trimEnd())
        assertEquals(limit, result.length)
    }

    @Test
    fun `truncateWithEllipsis reduces long text`() {
        val result = truncateWithEllipsis("abcdefghijklmnopqrstuvwxyz", 8)
        assertEquals("abcde...", result)
    }
}
