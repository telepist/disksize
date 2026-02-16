package disksize.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameFormattingTest {

    @Test
    fun `formatPercentage rounds to single decimal`() {
        assertEquals("12.3", formatPercentage(12.26))
        assertEquals("0.0", formatPercentage(0.001))
        assertEquals("99.9", formatPercentage(99.94))
    }

    @Test
    fun `formatDuration rounds to single decimal`() {
        assertEquals("5.4", formatDuration(5.35))
        assertEquals("0.0", formatDuration(0.01))
    }

    @Test
    fun `determinateProgressSegment renders expected fill`() {
        val segment = determinateProgressSegment(fraction = 0.5, width = 12)
        assertEquals("[█████     ]", segment.text)
        assertTrue(segment.text.length == 12)
    }
}
