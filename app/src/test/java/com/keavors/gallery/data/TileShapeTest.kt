package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a mosaic is cut.
 *
 * The promise is that a mosaic row holds about as much picture as a square row
 * does, so switching shapes does not change how much of the library a screenful
 * shows — and that no photograph is ever dropped or repeated in the cutting.
 */
class MosaicRowsTest {

    private fun wide(id: Long) = testItem(id).copy(width = 4000, height = 2000)
    private fun tall(id: Long) = testItem(id).copy(width = 2000, height = 4000)
    private fun square(id: Long) = testItem(id).copy(width = 3000, height = 3000)

    @Test
    fun `squares fill a row exactly as the square grid does`() {
        val rows = mosaicRows((1L..8L).map { square(it) }, columns = 4)
        assertEquals(listOf(4, 4), rows.map { it.size })
    }

    @Test
    fun `wide pictures need fewer of them to fill a row`() {
        val rows = mosaicRows((1L..8L).map { wide(it) }, columns = 4)
        assertEquals(listOf(2, 2, 2, 2), rows.map { it.size })
    }

    @Test
    fun `nothing is lost or repeated in the cutting`() {
        val items = (1L..17L).map { if (it % 3 == 0L) tall(it) else wide(it) }
        val rows = mosaicRows(items, columns = 4)
        assertEquals(items.map { it.id }, rows.flatten().map { it.id })
    }

    @Test
    fun `a photograph nobody has measured counts as a square`() {
        val unmeasured = testItem(1).copy(width = 0, height = 0)
        assertEquals(1f, unmeasured.tileAspect(), 0.001f)
    }

    @Test
    fun `one panorama cannot squash a whole row`() {
        val panorama = testItem(1).copy(width = 12000, height = 1000)
        assertTrue(panorama.tileAspect() <= 2.5f)
    }

    @Test
    fun `the last row is left short rather than padded`() {
        val rows = mosaicRows((1L..5L).map { square(it) }, columns = 4)
        assertEquals(listOf(4, 1), rows.map { it.size })
    }
}

/** What the timeline shows and what it leaves out. */
class LibraryFilterTest {

    private val camera = testItem(1).copy(relativePath = "DCIM/Camera/", bucketName = "Camera")
    private val shot = testItem(2).copy(
        relativePath = "Pictures/Screenshots/",
        bucketName = "Screenshots",
    )
    private val loaded = testItem(3).copy(relativePath = "Download/", bucketName = "Download")
    private val clip = testItem(4, isVideo = true).copy(relativePath = "DCIM/Camera/")

    private val library = listOf(camera, shot, loaded, clip)

    @Test
    fun `everything by default`() {
        assertEquals(4, library.filteredFor(GallerySettings()).size)
    }

    @Test
    fun `screenshots and downloads can each be switched off on their own`() {
        val noShots = library.filteredFor(GallerySettings(showScreenshots = false))
        assertEquals(listOf(1L, 3L, 4L), noShots.map { it.id })

        val noLoads = library.filteredFor(GallerySettings(showDownloads = false))
        assertEquals(listOf(1L, 2L, 4L), noLoads.map { it.id })
    }

    @Test
    fun `videos still go by themselves`() {
        assertEquals(listOf(1L, 2L, 3L), library.filteredFor(GallerySettings(showVideos = false)).map { it.id })
    }

    @Test
    fun `a folder switched off on the albums screen leaves the timeline`() {
        val prefs = AlbumPreferences(hidden = setOf(AlbumSource.Folder(1).key))
        val visible = library.visibleIn(prefs, showHidden = false)
        assertTrue(visible.none { it.bucketId == 1L })
    }

    @Test
    fun `and comes back without being unhidden`() {
        val prefs = AlbumPreferences(hidden = setOf(AlbumSource.Folder(1).key))
        assertEquals(library.size, library.visibleIn(prefs, showHidden = true).size)
    }
}
