package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchExternalTest {

    private fun item(id: Long, name: String = "IMG_$id.jpg", size: Long = 1000, bucket: Long = 1) =
        testItem(id = id, name = name, size = size, bucket = bucket)

    private val library = listOf(
        item(1, name = "DSC_0001.jpg", size = 100, bucket = 1),
        item(2, name = "DSC_0002.jpg", size = 200, bucket = 1),
        item(3, name = "DSC_0001.jpg", size = 300, bucket = 2),
        item(4, name = "holiday.mp4", size = 400, bucket = 2),
    )

    @Test
    fun `an id from a MediaStore uri wins outright`() {
        val found = library.matchExternal(ExternalRef(mediaStoreId = 3))
        assertEquals(3L, found?.id)
    }

    @Test
    fun `an unknown id does not fall through to a wrong guess`() {
        assertNull(library.matchExternal(ExternalRef(mediaStoreId = 99)))
    }

    @Test
    fun `a matching uri is enough on its own`() {
        val found = library.matchExternal(
            ExternalRef(uri = "content://media/external/images/media/2")
        )
        assertEquals(2L, found?.id)
    }

    @Test
    fun `a unique file name identifies the photo`() {
        assertEquals(4L, library.matchExternal(ExternalRef(name = "holiday.mp4"))?.id)
    }

    @Test
    fun `a name shared by two folders is settled by the size`() {
        // DSC_0001.jpg exists in two folders — the wrong pick would silently show
        // the wrong folder's neighbours, which is worse than showing none.
        assertEquals(1L, library.matchExternal(ExternalRef(name = "DSC_0001.jpg", sizeBytes = 100))?.id)
        assertEquals(3L, library.matchExternal(ExternalRef(name = "DSC_0001.jpg", sizeBytes = 300))?.id)
    }

    @Test
    fun `an ambiguous name with no size is left unanswered`() {
        assertNull(library.matchExternal(ExternalRef(name = "DSC_0001.jpg")))
    }

    @Test
    fun `an ambiguous name with a size that matches neither is left unanswered`() {
        assertNull(library.matchExternal(ExternalRef(name = "DSC_0001.jpg", sizeBytes = 999)))
    }

    @Test
    fun `a file the library has never seen is not matched to anything`() {
        assertNull(library.matchExternal(ExternalRef(name = "from-a-messenger.jpg", sizeBytes = 1)))
        assertNull(library.matchExternal(ExternalRef()))
    }

    @Test
    fun `a blank name is treated as no name`() {
        assertNull(library.matchExternal(ExternalRef(name = "   ")))
    }
}

class FolderNeighboursTest {

    private fun item(id: Long, bucket: Long) =
        testItem(id = id, bucket = bucket, taken = 1_700_000_000_000 - id)

    private val library = listOf(item(1, 10), item(2, 20), item(3, 10), item(4, 20), item(5, 10))

    @Test
    fun `a folder can be asked for by id alone`() {
        assertEquals(listOf(2L, 4L), library.inFolder(20).map { it.id })
    }

    @Test
    fun `neighbours are the rest of the same folder`() {
        val folder = library.inFolder(10)
        assertEquals(listOf(1L, 3L, 5L), folder.map { it.id })
    }

    @Test
    fun `neighbours keep the order the library already had them in`() {
        val folder = library.inFolder(20)
        assertEquals(listOf(2L, 4L), folder.map { it.id })
    }

    @Test
    fun `the opened photo is found among its neighbours`() {
        val folder = library.inFolder(10)
        assertEquals(2, folder.indexOfId(5))
    }

    @Test
    fun `a photo that vanished from its folder starts at the beginning`() {
        // The library can reload between opening and looking; landing on the
        // first neighbour beats crashing on an index of minus one.
        assertEquals(0, library.inFolder(10).indexOfId(99))
    }

    @Test
    fun `a folder of one has exactly one page`() {
        assertEquals(1, listOf(item(7, 70)).inFolder(70).size)
    }
}

class ProvisionalGuessTest {

    @Test
    fun `the type the intent declared settles it`() {
        assertTrue(looksLikeVideo("video/mp4", "content://x/1"))
        assertFalse(looksLikeVideo("image/jpeg", "content://x/clip.mp4"))
    }

    @Test
    fun `without a type the name is the next best thing`() {
        // Nothing is asked of the sending app here on purpose: that is a call
        // into somebody else's process, and this runs before the first frame.
        assertTrue(looksLikeVideo(null, "content://media/external/video/media/7.mkv"))
        assertTrue(looksLikeVideo("", "file:///storage/DCIM/VID_0001.MP4"))
        assertFalse(looksLikeVideo(null, "content://media/external/images/media/7.jpg"))
    }

    @Test
    fun `a query string does not become part of the extension`() {
        assertTrue(looksLikeVideo(null, "content://x/a.mp4?token=9"))
    }

    @Test
    fun `a uri that says nothing at all is taken for a photograph`() {
        // The commoner case by far, and the cheaper one to be wrong about: a
        // video shown as a still corrects itself a moment later.
        assertFalse(looksLikeVideo(null, "content://com.example.files/document/42"))
    }
}

class StandaloneItemTest {

    @Test
    fun `a photograph that named a row keeps its number`() {
        // The number is what finds the thumbnail the grid already drew, which is
        // what puts the picture on screen in the same frame as the interface
        // rather than a decode later.
        val item = ExternalRef(
            mediaStoreId = 42,
            name = "IMG_0042.jpg",
            uri = "content://media/external/images/media/42",
        ).asStandaloneItem()

        assertEquals(42L, item.id)
    }

    @Test
    fun `a photograph from somebody else's storage admits it has no number`() {
        val item = ExternalRef(
            name = "photo.jpg",
            uri = "content://org.telegram.messenger.provider/media/photo.jpg",
        ).asStandaloneItem()

        assertEquals(UNKNOWN_ID, item.id)
    }

    @Test
    fun `the library is asked about the number a standalone item carries`() {
        // Both halves of the same rule: what asStandaloneItem puts in the id is
        // what matchExternal looks the photograph up by.
        val library = listOf(testItem(7))
        val ref = ExternalRef(mediaStoreId = 7)

        assertEquals(ref.asStandaloneItem().id, library.matchExternal(ref)?.id)
    }
}
