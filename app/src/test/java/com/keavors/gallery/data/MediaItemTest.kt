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


    @Test
    fun `empty library summarises to zeroes`() {
        assertEquals(LibrarySummary.Empty, emptyList<MediaItem>().summarize())
    }

    @Test
    fun `counts photos videos albums and bytes`() {
        val summary = listOf(
            testItem(1, size = 100, bucket = 1),
            testItem(2, size = 200, bucket = 1),
            testItem(3, isVideo = true, size = 300, bucket = 2),
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
            testItem(1, taken = 1_000_000_000_000),
            testItem(2, taken = 1_700_000_000_000),
            testItem(3, taken = 1_400_000_000_000),
        ).summarize()

        assertEquals(1_000_000_000_000, summary.oldest)
        assertEquals(1_700_000_000_000, summary.newest)
    }

    @Test
    fun `files without a date stay out of the range instead of dragging it to 1970`() {
        val summary = listOf(
            testItem(1, taken = 0),
            testItem(2, taken = 1_700_000_000_000),
        ).summarize()

        assertEquals(1_700_000_000_000, summary.oldest)
        assertEquals(1_700_000_000_000, summary.newest)
    }

    @Test
    fun `a library where nothing has a date reports no range`() {
        val summary = listOf(testItem(1, taken = 0)).summarize()
        assertNull(summary.oldest)
        assertNull(summary.newest)
    }
}

class FormatTest {

    private val locale = java.util.Locale.US

    @Test
    fun `bytes below a kilobyte are shown raw`() {
        assertEquals("0 B", formatBytes(0, locale))
        assertEquals("999 B", formatBytes(999, locale))
    }

    @Test
    fun `climbs through the units`() {
        assertEquals("1.0 KB", formatBytes(1000, locale))
        assertEquals("1.0 MB", formatBytes(1_000_000, locale))
        assertEquals("1.0 GB", formatBytes(1_000_000_000, locale))
    }

    @Test
    fun `drops the decimal once the number is long enough without it`() {
        assertEquals("99.9 GB", formatBytes(99_900_000_000, locale))
        assertEquals("148 GB", formatBytes(148_000_000_000, locale))
    }

    @Test
    fun `the other convention counts in 1024s and says so`() {
        // The two are not the same number and must never wear each other's
        // names: 1024 bytes is a KiB, and a KB is a thousand of them.
        assertEquals("1.0 KiB", formatBytes(1024, locale, binary = true))
        assertEquals("1.0 MiB", formatBytes(1024L * 1024, locale, binary = true))
        assertEquals("1023 B", formatBytes(1023, locale, binary = true))
    }

    @Test
    fun `counts get thin separators between groups`() {
        assertEquals("4 812", formatCount(4812, locale))
        assertEquals("512", formatCount(512, locale))
    }
}

/** What a tile says about a file, from what the library already knows. */
class BadgeKindTest {

    @Test
    fun `an ordinary photograph says nothing`() {
        assertEquals(null, testItem(1).badgeKind())
    }

    @Test
    fun `a gif is named by its type`() {
        assertEquals(BadgeKind.GIF, testItem(1).copy(mimeType = "image/gif").badgeKind())
    }

    @Test
    fun `a raw photograph is named by its type or by its name`() {
        assertEquals(BadgeKind.RAW, testItem(1).copy(mimeType = "image/x-adobe-dng").badgeKind())
        assertEquals(
            BadgeKind.RAW,
            testItem(1).copy(mimeType = "image/*", name = "DSC_0001.NEF").badgeKind(),
        )
    }

    @Test
    fun `a video is left to the badge it already has`() {
        // Videos carry their length, which is a more useful thing to say.
        assertEquals(null, testItem(1, isVideo = true).copy(mimeType = "video/mp4").badgeKind())
    }
}
