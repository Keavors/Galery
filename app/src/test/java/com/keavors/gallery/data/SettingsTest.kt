package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJsonTest {

    private val changed = GallerySettings(
        themeMode = ThemeMode.DARK,
        palette = Palette.MONO,
        pureBlack = true,
        tileGapDp = 6,
        tileCornerDp = 0,
        animations = false,
        defaultZoomColumns = 12,
        sortBy = SortBy.NAME,
        sortOrder = SortOrder.OLDEST_FIRST,
        showVideos = false,
        tileBadges = false,
        relativeDates = false,
        chromeOnOpen = false,
        autoHideSeconds = 5,
        swipeDownCloses = false,
        swipeUpDetails = false,
        doubleTapZoom = false,
        loopPaging = true,
        maxBrightness = true,
        keepScreenOn = true,
        videoAutoplay = true,
        videoSound = true,
        videoRepeat = true,
        language = "en",
    )

    @Test
    fun `everything survives the round trip`() {
        assertEquals(changed, decodeSettings(encodeSettings(changed)))
    }

    @Test
    fun `defaults survive the round trip`() {
        assertEquals(GallerySettings(), decodeSettings(encodeSettings(GallerySettings())))
    }

    @Test
    fun `nothing stored means nothing has been decided`() {
        assertEquals(GallerySettings(), decodeSettings(null))
        assertEquals(GallerySettings(), decodeSettings(""))
    }

    @Test
    fun `a damaged file costs the settings, not the app`() {
        assertEquals(GallerySettings(), decodeSettings("{oh dear"))
    }

    @Test
    fun `a document from an older version keeps what it does say`() {
        // Fields it never heard of fall back one at a time, rather than the
        // whole document being thrown away.
        val partial = """{"themeMode":"DARK","tileGapDp":7}"""
        val settings = decodeSettings(partial)

        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(7, settings.tileGapDp)
        assertEquals(GallerySettings().palette, settings.palette)
        assertEquals(GallerySettings().videoRepeat, settings.videoRepeat)
    }

    @Test
    fun `a value that is no longer a valid choice falls back instead of crashing`() {
        val settings = decodeSettings("""{"palette":"NEON","sortBy":"COLOUR"}""")
        assertEquals(GallerySettings().palette, settings.palette)
        assertEquals(GallerySettings().sortBy, settings.sortBy)
    }

    @Test
    fun `numbers out of range are pulled back into it`() {
        val settings = decodeSettings("""{"tileGapDp":900,"autoHideSeconds":-4}""")
        assertEquals(8, settings.tileGapDp)
        assertEquals(0, settings.autoHideSeconds)
    }

    @Test
    fun `the default zoom resolves to a real level`() {
        assertEquals(ZoomLevel.SMALL, GallerySettings(defaultZoomColumns = 12).defaultZoom)
        // A column count no level has must not leave the grid without one.
        assertEquals(ZoomLevel.Default, GallerySettings(defaultZoomColumns = 5).defaultZoom)
    }
}

class SortingTest {

    private val library = listOf(
        testItem(1, name = "b.jpg", size = 300, taken = 100),
        testItem(2, name = "A.jpg", size = 100, taken = 300),
        testItem(3, name = "c.jpg", size = 200, taken = 200),
    )

    @Test
    fun `newest first is the default order`() {
        val sorted = library.sortedFor(SortBy.TAKEN, SortOrder.NEWEST_FIRST)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `oldest first is the same order reversed`() {
        val sorted = library.sortedFor(SortBy.TAKEN, SortOrder.OLDEST_FIRST)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun `names sort without regard to capitals`() {
        // Otherwise every file starting with a capital forms its own block.
        val sorted = library.sortedFor(SortBy.NAME, SortOrder.OLDEST_FIRST)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun `size sorts by bytes`() {
        val sorted = library.sortedFor(SortBy.SIZE, SortOrder.NEWEST_FIRST)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun `an empty library sorts to nothing rather than failing`() {
        assertTrue(emptyList<MediaItem>().sortedFor(SortBy.NAME, SortOrder.NEWEST_FIRST).isEmpty())
    }
}

class FilteringTest {

    private val library = listOf(testItem(1), testItem(2, isVideo = true), testItem(3))

    @Test
    fun `videos are shown unless they are turned off`() {
        assertEquals(3, library.filteredFor(GallerySettings()).size)
        assertEquals(
            listOf(1L, 3L),
            library.filteredFor(GallerySettings(showVideos = false)).map { it.id },
        )
    }
}
