package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

/**
 * What counts as a photograph off a sensor.
 *
 * Two places ask and must agree: the tile that marks it raw, and the decoder
 * that has to open it a different way because Android cannot read one at all.
 */
class RawPhotoTest {

    @Test
    fun `the library's own word for it is enough`() {
        assertTrue(isRawPhoto("image/x-adobe-dng", "whatever"))
        assertTrue(isRawPhoto("image/x-sony-arw", "whatever"))
    }

    @Test
    fun `and the name, for when the library says only that it is an image`() {
        // MediaStore files a raw photograph under a plain image type often
        // enough that the name is the only thing that knows.
        assertTrue(isRawPhoto("image/jpeg", "DSC_0001.NEF"))
        assertTrue(isRawPhoto(null, "photo.dng"))
    }

    @Test
    fun `an ordinary photograph is not one`() {
        assertFalse(isRawPhoto("image/jpeg", "IMG_0001.jpg"))
        assertFalse(isRawPhoto("image/heif", "IMG_0001.heic"))
        assertFalse(isRawPhoto(null, null))
    }

    @Test
    fun `a name that merely contains the letters is not one either`() {
        assertFalse(isRawPhoto("image/jpeg", "dng-comparison.jpg"))
        assertFalse(isRawPhoto("image/jpeg", "raw_food_recipe.png"))
    }

    @Test
    fun `the tile and the decoder agree about the same file`() {
        val item = testItem(1, name = "DSC_0001.dng")
        assertEquals(BadgeKind.RAW, item.badgeKind())
        assertTrue(isRawPhoto(item.mimeType, item.name))
    }
}
