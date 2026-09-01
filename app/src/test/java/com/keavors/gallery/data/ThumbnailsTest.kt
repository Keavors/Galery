package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `the same photo at different zoom levels is cached separately`() {
        val small = thumbnailCacheKey(42, thumbnailBucketPx(43))
        val large = thumbnailCacheKey(42, thumbnailBucketPx(500))
        assertNotEquals(small, large)
    }

    @Test
    fun `the same photo at the same zoom level is one cache entry`() {
        // Two tiles a pixel apart in width must not each claim their own copy.
        assertEquals(
            thumbnailCacheKey(42, thumbnailBucketPx(120)),
            thumbnailCacheKey(42, thumbnailBucketPx(160)),
        )
    }

    @Test
    fun `different photos never share a cache entry`() {
        assertNotEquals(thumbnailCacheKey(1, 96), thumbnailCacheKey(2, 96))
    }
}

/**
 * The name a picture is kept under while it is being read.
 *
 * One rule serving the grid, the viewer and the moment an intent arrives, which
 * is the whole point of it: if the three disagreed about the name, each would
 * decode the same photograph for itself and the viewer would show black while
 * it did.
 */
class PreviewCacheKeyTest {

    @Test
    fun `a photograph in the library is named by its row, whoever asks`() {
        val item = testItem(42)
        assertEquals(thumbnailCacheKey(42, 384), previewCacheKey(item, 384))
    }

    @Test
    fun `a photograph with no row is named by where it came from`() {
        val fromOutside = ExternalRef(
            name = "photo.jpg",
            uri = "content://com.example.files/document/photo.jpg",
        ).asStandaloneItem()

        assertTrue(previewCacheKey(fromOutside, 384).contains(fromOutside.uri))
    }

    @Test
    fun `two photographs from outside do not share one`() {
        val first = ExternalRef(uri = "content://x/a.jpg").asStandaloneItem()
        val second = ExternalRef(uri = "content://x/b.jpg").asStandaloneItem()
        assertNotEquals(previewCacheKey(first, 384), previewCacheKey(second, 384))
    }

    @Test
    fun `a file in the vault keeps the name its tile already uses`() {
        // Vaulted files have no MediaStore row either, but they do have an id of
        // their own, and the grid has already cached them under it.
        val hidden = testItem(-90_000, isPrivate = true)
        assertEquals(thumbnailCacheKey(-90_000, 384), previewCacheKey(hidden, 384))
    }

    @Test
    fun `the same photograph at two sizes is two entries`() {
        val item = testItem(42)
        assertNotEquals(previewCacheKey(item, 192), previewCacheKey(item, 384))
    }
}
