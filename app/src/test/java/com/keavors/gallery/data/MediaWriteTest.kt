package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `a file outside the trash has no expiry to report`() {
        assertNull(daysUntilExpiry(0, now))
        assertNull(daysUntilExpiry(-1, now))
    }

    @Test
    fun `whole days are counted as themselves`() {
        assertEquals(30, daysUntilExpiry(now + 30 * day, now))
        assertEquals(1, daysUntilExpiry(now + day, now))
    }

    @Test
    fun `a part day rounds up rather than down`() {
        // Eight hours left is one day left to anyone reading it; saying zero
        // would imply the file is already gone.
        assertEquals(1, daysUntilExpiry(now + day / 3, now))
        assertEquals(3, daysUntilExpiry(now + 2 * day + 1, now))
    }

    @Test
    fun `an expiry that has passed reads as none left`() {
        assertEquals(0, daysUntilExpiry(now - day, now))
        assertEquals(0, daysUntilExpiry(now, now))
    }
}
