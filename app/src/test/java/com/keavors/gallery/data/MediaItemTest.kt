package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTimeTest {

    @Test
    fun `prefers the date from the photo itself`() {
        assertEquals(1_700_000_000_000, MediaTime.bestTimestamp(1_700_000_000_000, 1_600_000_000, 1_500_000_000))
    }

    @Test
    fun `falls back to modified date and converts seconds to millis`() {
        assertEquals(1_600_000_000_000, MediaTime.bestTimestamp(0, 1_600_000_000, 1_500_000_000))
    }

    @Test
    fun `falls back to added date when nothing else is known`() {
        assertEquals(1_500_000_000_000, MediaTime.bestTimestamp(0, 0, 1_500_000_000))
    }

    @Test
    fun `reports zero when the file carries no date at all`() {
        assertEquals(0, MediaTime.bestTimestamp(0, 0, 0))
    }
}

class LibrarySummaryTest {

    private fun item(
        id: Long,
        video: Boolean = false,
        bucket: Long = 1,
        size: Long = 1000,
        taken: Long = 1_700_000_000_000,
    ) = MediaItem(
        id = id,
        name = "IMG_$id.jpg",
        mimeType = if (video) "video/mp4" else "image/jpeg",
        isVideo = video,
        sizeBytes = size,
        width = 4000,
        height = 3000,
        durationMs = if (video) 5000 else 0,
        takenAt = taken,
        addedAt = taken,
        modifiedAt = taken,
        bucketId = bucket,
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        isFavorite = false,
        orientation = 0,
    )

    @Test
    fun `empty library summarises to zeroes`() {
        assertEquals(LibrarySummary.Empty, emptyList<MediaItem>().summarize())
    }

    @Test
    fun `counts photos videos albums and bytes`() {
        val summary = listOf(
            item(1, size = 100, bucket = 1),
            item(2, size = 200, bucket = 1),
            item(3, video = true, size = 300, bucket = 2),
        ).summarize()

        assertEquals(2, summary.photos)
        assertEquals(1, summary.videos)
        assertEquals(3, summary.total)
        assertEquals(2, summary.albums)
        assertEquals(600, summary.totalBytes)
    }

    @Test
    fun `date range spans oldest and newest`() {
        val summary = listOf(
            item(1, taken = 1_000_000_000_000),
            item(2, taken = 1_700_000_000_000),
            item(3, taken = 1_400_000_000_000),
        ).summarize()

        assertEquals(1_000_000_000_000, summary.oldest)
        assertEquals(1_700_000_000_000, summary.newest)
    }

    @Test
    fun `files without a date stay out of the range instead of dragging it to 1970`() {
        val summary = listOf(
            item(1, taken = 0),
            item(2, taken = 1_700_000_000_000),
        ).summarize()

        assertEquals(1_700_000_000_000, summary.oldest)
        assertEquals(1_700_000_000_000, summary.newest)
    }

    @Test
    fun `a library where nothing has a date reports no range`() {
        val summary = listOf(item(1, taken = 0)).summarize()
        assertNull(summary.oldest)
        assertNull(summary.newest)
    }
}

class FormatTest {

    private val locale = java.util.Locale.US

    @Test
    fun `bytes below a kilobyte are shown raw`() {
        assertEquals("0 B", formatBytes(0, locale))
        assertEquals("1023 B", formatBytes(1023, locale))
    }

    @Test
    fun `climbs through the units`() {
        assertEquals("1.0 KB", formatBytes(1024, locale))
        assertEquals("1.0 MB", formatBytes(1024L * 1024, locale))
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024, locale))
    }

    @Test
    fun `drops the decimal once the number is long enough without it`() {
        assertEquals("99.9 GB", formatBytes((99.9 * 1024 * 1024 * 1024).toLong(), locale))
        assertEquals("148 GB", formatBytes(148L * 1024 * 1024 * 1024, locale))
    }

    @Test
    fun `counts get thin separators between groups`() {
        assertEquals("4 812", formatCount(4812, locale))
        assertEquals("512", formatCount(512, locale))
    }
}
