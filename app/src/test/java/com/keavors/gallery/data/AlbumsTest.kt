package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderAlbumsTest {

    /** Newest first, the way the repository hands the library over. */
    private val library = listOf(
        testItem(1, bucket = 10, taken = 500),
        testItem(2, bucket = 20, taken = 400),
        testItem(3, bucket = 10, taken = 300),
        testItem(4, bucket = 30, taken = 200),
        testItem(5, bucket = 10, taken = 100),
    )

    @Test
    fun `an empty library has no folders`() {
        assertTrue(emptyList<MediaItem>().folderAlbums().isEmpty())
    }

    @Test
    fun `every folder is counted once with everything in it`() {
        val albums = library.folderAlbums()
        assertEquals(3, albums.size)
        assertEquals(3, albums.first { it.bucketId == 10L }.count)
        assertEquals(1, albums.first { it.bucketId == 20L }.count)
    }

    @Test
    fun `the cover is the newest photo in the folder`() {
        assertEquals(1L, library.folderAlbums().first { it.bucketId == 10L }.cover.id)
    }

    @Test
    fun `folders are ordered by how recently they were used`() {
        // The folder used this morning belongs above one last touched years ago,
        // whatever the two are called.
        assertEquals(listOf(10L, 20L, 30L), library.folderAlbums().map { it.bucketId })
    }

    @Test
    fun `a folder with no name falls back to the last part of its path`() {
        val item = testItem(9, bucket = 40).copy(bucketName = "", relativePath = "Pictures/Saved/")
        assertEquals("Saved", listOf(item).folderAlbums().single().name)
    }
}

class AlbumSourceTest {

    private val library = listOf(
        testItem(1, bucket = 10, favorite = true),
        testItem(2, bucket = 20, isVideo = true),
        testItem(3, bucket = 10, isVideo = true, favorite = true),
        testItem(4, bucket = 30),
    )

    @Test
    fun `a folder album is the folder`() {
        assertEquals(listOf(1L, 3L), library.inAlbum(AlbumSource.Folder(10)).map { it.id })
    }

    @Test
    fun `favourites reach across folders`() {
        assertEquals(listOf(1L, 3L), library.inAlbum(AlbumSource.Favourites).map { it.id })
    }

    @Test
    fun `videos reach across folders too`() {
        assertEquals(listOf(2L, 3L), library.inAlbum(AlbumSource.Videos).map { it.id })
    }

    @Test
    fun `an album with nothing in it is empty rather than absent`() {
        val nothing = listOf(testItem(1)).inAlbum(AlbumSource.Favourites)
        assertTrue(nothing.isEmpty())
    }

    @Test
    fun `albums keep the order the library already had`() {
        val ordered = library.inAlbum(AlbumSource.Videos)
        assertEquals(library.filter { it.isVideo }.map { it.id }, ordered.map { it.id })
    }
}

class AlbumOrderTest {

    private val albums = listOf(
        testItem(1, bucket = 10, taken = 500),
        testItem(2, bucket = 20, taken = 400),
        testItem(3, bucket = 30, taken = 300),
    ).folderAlbums()

    @Test
    fun `nothing pinned leaves the order alone`() {
        assertEquals(
            albums.map { it.bucketId },
            albums.pinnedFirst(AlbumPreferences()).map { it.bucketId },
        )
    }

    @Test
    fun `a pinned album goes to the top`() {
        val prefs = AlbumPreferences(pinned = setOf(AlbumSource.Folder(30).key))
        assertEquals(listOf(30L, 10L, 20L), albums.pinnedFirst(prefs).map { it.bucketId })
    }

    @Test
    fun `pinned albums keep their order relative to each other`() {
        val prefs = AlbumPreferences(
            pinned = setOf(AlbumSource.Folder(30).key, AlbumSource.Folder(20).key)
        )
        assertEquals(listOf(20L, 30L, 10L), albums.pinnedFirst(prefs).map { it.bucketId })
    }

    @Test
    fun `hidden albums are left out until asked for`() {
        val prefs = AlbumPreferences(hidden = setOf(AlbumSource.Folder(20).key))
        assertEquals(listOf(10L, 30L), albums.withoutHidden(prefs, showHidden = false).map { it.bucketId })
        assertEquals(3, albums.withoutHidden(prefs, showHidden = true).size)
    }
}
