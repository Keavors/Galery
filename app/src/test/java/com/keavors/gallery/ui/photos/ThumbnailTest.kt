package com.keavors.gallery.ui.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTest {

    @Test
    fun `short clips read as minutes and seconds`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:07", formatDuration(7_400))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun `an hour adds a field rather than counting to ninety minutes`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("2:03:04", formatDuration((2 * 3600 + 3 * 60 + 4) * 1000L))
    }

    @Test
    fun `seconds and minutes keep their leading zero`() {
        assertEquals("10:05", formatDuration(605_000))
        assertEquals("1:05:05", formatDuration(3_905_000))
    }
}
