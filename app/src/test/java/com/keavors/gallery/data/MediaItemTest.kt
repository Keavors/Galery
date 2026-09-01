package com.keavors.gallery.data

import org.junit.Assert.assertEquals
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
