package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumKeyTest {

    @Test
    fun `a folder is keyed by its id, not its name`() {
        // Renaming a folder on the phone must not lose whether it was pinned.
        assertEquals("folder:42", AlbumSource.Folder(42).key)
    }

    @Test
    fun `every kind of album has its own key`() {
        val keys = listOf(
            AlbumSource.Folder(1),
            AlbumSource.Favourites,
            AlbumSource.Videos,
            AlbumSource.User(1),
        ).map { it.key }

        assertEquals(keys.size, keys.toSet().size)
    }
}

class AlbumPreferencesJsonTest {

    private val full = AlbumPreferences(
        pinned = setOf("folder:1", "favourites"),
        hidden = setOf("folder:2"),
        covers = mapOf("folder:1" to 555L),
        userAlbums = listOf(
            UserAlbum(id = 100, name = "Отпуск", memberIds = setOf(1, 2, 3)),
            UserAlbum(id = 200, name = "", memberIds = emptySet()),
        ),
    )

    @Test
    fun `what goes in comes back out`() {
        assertEquals(full, decodeAlbumPreferences(encodeAlbumPreferences(full)))
    }

    @Test
    fun `empty preferences survive the round trip`() {
        val empty = AlbumPreferences()
        assertEquals(empty, decodeAlbumPreferences(encodeAlbumPreferences(empty)))
    }

    @Test
    fun `nothing stored reads as nothing decided`() {
        assertEquals(AlbumPreferences(), decodeAlbumPreferences(null))
        assertEquals(AlbumPreferences(), decodeAlbumPreferences(""))
        assertEquals(AlbumPreferences(), decodeAlbumPreferences("   "))
    }

    @Test
    fun `a corrupt file costs the pinned albums, not the app`() {
        // Throwing here would mean a damaged preferences file stops the gallery
        // from opening at all.
        assertEquals(AlbumPreferences(), decodeAlbumPreferences("{not json"))
        assertEquals(AlbumPreferences(), decodeAlbumPreferences("[]"))
    }

    @Test
    fun `an album entry missing its fields does not take the others with it`() {
        val json = """{"albums":[{"id":1},{"nonsense":true},{"id":2,"name":"Ok"}]}"""
        val albums = decodeAlbumPreferences(json).userAlbums

        assertEquals(3, albums.size)
        assertEquals(setOf<Long>(), albums[0].memberIds)
        assertEquals("Ok", albums[2].name)
    }

    @Test
    fun `unicode names survive`() {
        val prefs = AlbumPreferences(userAlbums = listOf(UserAlbum(1, "Лето 2026 ☀", setOf(7))))
        assertEquals(prefs, decodeAlbumPreferences(encodeAlbumPreferences(prefs)))
    }

    @Test
    fun `the lookups answer for what is stored and only that`() {
        assertTrue(full.isPinned(AlbumSource.Folder(1)))
        assertFalse(full.isPinned(AlbumSource.Folder(2)))
        assertTrue(full.isHidden(AlbumSource.Folder(2)))
        assertEquals(555L, full.coverId(AlbumSource.Folder(1)))
        assertNull(full.coverId(AlbumSource.Videos))
    }
}

class UserAlbumContentsTest {

    private val library = listOf(testItem(1), testItem(2), testItem(3))
    private val albums = listOf(UserAlbum(id = 7, name = "Trip", memberIds = setOf(1, 3)))

    @Test
    fun `a user album holds exactly what was put in it`() {
        val contents = library.inAlbum(AlbumSource.User(7), albums)
        assertEquals(listOf(1L, 3L), contents.map { it.id })
    }

    @Test
    fun `an album nobody made is empty rather than everything`() {
        assertTrue(library.inAlbum(AlbumSource.User(999), albums).isEmpty())
    }

    @Test
    fun `a member that no longer exists simply does not appear`() {
        // Photos get deleted from under an album; membership is not a promise
        // that the file is still there.
        val withGhost = listOf(UserAlbum(id = 7, name = "Trip", memberIds = setOf(1, 404)))
        assertEquals(listOf(1L), library.inAlbum(AlbumSource.User(7), withGhost).map { it.id })
    }
}
