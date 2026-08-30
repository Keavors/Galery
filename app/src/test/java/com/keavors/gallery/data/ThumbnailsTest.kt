package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailBucketTest {

    @Test
    fun `a tile gets the smallest bucket that covers it`() {
        assertEquals(96, thumbnailBucketPx(43))
        assertEquals(96, thumbnailBucketPx(96))
        assertEquals(192, thumbnailBucketPx(97))
        assertEquals(384, thumbnailBucketPx(300))
        assertEquals(768, thumbnailBucketPx(500))
    }

    @Test
    fun `an enormous tile stops at the largest bucket instead of asking for more`() {
        assertEquals(768, thumbnailBucketPx(4000))
    }

    @Test
    fun `an unmeasured tile still asks for something loadable`() {
        assertTrue(thumbnailBucketPx(0) > 0)
        assertTrue(thumbnailBucketPx(-10) > 0)
    }

    @Test
    fun `every zoom level lands on one of a handful of buckets`() {
        // The point of bucketing: five levels must not mean five separate
        // cached copies of every photo. A 1080px-wide screen, roughly.
        val screenPx = 1080
        val buckets = ZoomLevel.entries.map { thumbnailBucketPx(screenPx / it.columns) }.toSet()

        assertTrue("levels spread over ${buckets.size} buckets", buckets.size <= 4)
    }
}
