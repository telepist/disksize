package disksize.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SizeFormatterTest {

    @Test
    fun `should format zero bytes`() {
        assertEquals("0 B", SizeFormatter.format(0))
    }

    @Test
    fun `should format bytes under 1 KB`() {
        assertEquals("1 B", SizeFormatter.format(1))
        assertEquals("10 B", SizeFormatter.format(10))
        assertEquals("100 B", SizeFormatter.format(100))
        assertEquals("999 B", SizeFormatter.format(999))
        assertEquals("1023 B", SizeFormatter.format(1023))
    }

    @Test
    fun `should format kilobytes`() {
        assertEquals("1.0 KB", SizeFormatter.format(1024))
        assertEquals("1.5 KB", SizeFormatter.format(1536)) // 1.5 * 1024
        assertEquals("10.0 KB", SizeFormatter.format(10240))
        assertEquals("100.5 KB", SizeFormatter.format(102912)) // 100.5 * 1024
        assertEquals("999.9 KB", SizeFormatter.format(1023898)) // ~999.9 * 1024
    }

    @Test
    fun `should format megabytes`() {
        assertEquals("1.0 MB", SizeFormatter.format(1048576)) // 1 * 1024 * 1024
        assertEquals("1.5 MB", SizeFormatter.format(1572864)) // 1.5 * 1024 * 1024
        assertEquals("10.0 MB", SizeFormatter.format(10485760)) // 10 * 1024 * 1024
        assertEquals("100.5 MB", SizeFormatter.format(105381888)) // 100.5 * 1024 * 1024
        assertEquals("500.0 MB", SizeFormatter.format(524288000)) // 500 * 1024 * 1024
    }

    @Test
    fun `should format gigabytes`() {
        assertEquals("1.0 GB", SizeFormatter.format(1073741824)) // 1 * 1024^3
        assertEquals("1.5 GB", SizeFormatter.format(1610612736)) // 1.5 * 1024^3
        assertEquals("10.0 GB", SizeFormatter.format(10737418240)) // 10 * 1024^3
        assertEquals("100.5 GB", SizeFormatter.format(107910852608)) // 100.5 * 1024^3
        assertEquals("500.0 GB", SizeFormatter.format(536870912000)) // 500 * 1024^3
    }

    @Test
    fun `should format terabytes`() {
        assertEquals("1.0 TB", SizeFormatter.format(1099511627776)) // 1 * 1024^4
        assertEquals("1.5 TB", SizeFormatter.format(1649267441664)) // 1.5 * 1024^4
        assertEquals("10.0 TB", SizeFormatter.format(10995116277760)) // 10 * 1024^4
        assertEquals("100.5 TB", SizeFormatter.format(110501910732800)) // 100.5 * 1024^4
    }

    @Test
    fun `should handle maximum Long value`() {
        // Long.MAX_VALUE = 9223372036854775807
        // Should be approximately 8.0 EB (exabytes), but we'll format as TB
        val result = SizeFormatter.format(Long.MAX_VALUE)
        // Should start with a large number and end with TB
        assertEquals(true, result.endsWith(" TB") || result.endsWith(" PB"))
    }

    @Test
    fun `should round to one decimal place`() {
        assertEquals("1.0 KB", SizeFormatter.format(1024))
        assertEquals("1.1 KB", SizeFormatter.format(1126)) // 1.099... KB, rounds to 1.1
        assertEquals("1.9 KB", SizeFormatter.format(1946)) // 1.9 KB
        assertEquals("2.0 KB", SizeFormatter.format(2048))
    }

    @Test
    fun `should handle edge cases at unit boundaries`() {
        // Just under 1 KB
        assertEquals("1023 B", SizeFormatter.format(1023))

        // Exactly 1 KB
        assertEquals("1.0 KB", SizeFormatter.format(1024))

        // Just under 1 MB (1048575 bytes / 1024 = 1023.999... KB)
        assertEquals("1024.0 KB", SizeFormatter.format(1048575))

        // Exactly 1 MB
        assertEquals("1.0 MB", SizeFormatter.format(1048576))

        // Just under 1 GB (1073741823 bytes / 1048576 = 1023.999... MB)
        assertEquals("1024.0 MB", SizeFormatter.format(1073741823))

        // Exactly 1 GB
        assertEquals("1.0 GB", SizeFormatter.format(1073741824))
    }
}
