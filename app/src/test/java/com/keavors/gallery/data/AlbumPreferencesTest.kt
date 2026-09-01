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
            AlbumSource.Vault,
        ).map { it.key }

        assertEquals(keys.size, keys.toSet().size)
    }
}

class AlbumPreferencesJsonTest {

    private val full = AlbumPreferences(
        pinned = setOf("folder:1", "favourites"),
        hidden = setOf("folder:2"),
        covers = mapOf("folder:1" to 555L),
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
    fun `an older file that still holds albums this app no longer keeps is read anyway`() {
        // Albums used to be lists of ids inside this app. They are folders now,
        // and a preferences file written before that must still open — with what
        // it says about pinning and covers intact and the rest ignored.
        val json = """{"pinned":["folder:9"],"albums":[{"id":1,"name":"Old"}]}"""
        assertEquals(setOf("folder:9"), decodeAlbumPreferences(json).pinned)
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

class AlbumContentsTest {

    private val library = listOf(
        testItem(1, bucket = 1),
        testItem(2, bucket = 2).copy(isFavorite = true),
        testItem(3, bucket = 1, isVideo = true),
    )

    @Test
    fun `an album holds what its folder holds`() {
        assertEquals(listOf(1L, 3L), library.inAlbum(AlbumSource.Folder(1)).map { it.id })
    }

    @Test
    fun `the questions asked of the whole library answer for themselves`() {
        assertEquals(listOf(2L), library.inAlbum(AlbumSource.Favourites).map { it.id })
        assertEquals(listOf(3L), library.inAlbum(AlbumSource.Videos).map { it.id })
    }

    @Test
    fun `the library cannot answer for the vault`() {
        assertTrue(library.inAlbum(AlbumSource.Vault).isEmpty())
    }
}

/**
 * What a folder's opinions do when the folder is renamed.
 *
 * A folder's id comes from its path, so renaming one leaves the preferences
 * talking about a folder that no longer exists and saying nothing about the one
 * that now does.
 */
class MovedOpinionsTest {

    private val camera = AlbumSource.Folder(1)
    private val holiday = AlbumSource.Folder(2)

    @Test
    fun `pinned, hidden and the cover all follow the folder`() {
        val before = AlbumPreferences(
            pinned = setOf(camera.key),
            hidden = setOf(camera.key),
            covers = mapOf(camera.key to 42L),
        )

        val after = before.movedTo(camera, holiday)

        assertTrue(after.isPinned(holiday))
        assertTrue(after.isHidden(holiday))
        assertEquals(42L, after.coverId(holiday))
    }

    @Test
    fun `nothing is left behind under the old name`() {
        val before = AlbumPreferences(pinned = setOf(camera.key), covers = mapOf(camera.key to 42L))
        val after = before.movedTo(camera, holiday)

        assertFalse(after.isPinned(camera))
        assertNull(after.coverId(camera))
    }

    @Test
    fun `another folder's opinions are left alone`() {
        val other = AlbumSource.Folder(9)
        val before = AlbumPreferences(pinned = setOf(other.key))
        val after = before.movedTo(camera, holiday)

        assertTrue(after.isPinned(other))
        assertFalse(after.isPinned(holiday))
    }

    @Test
    fun `a folder that was nothing in particular stays nothing in particular`() {
        val after = AlbumPreferences().movedTo(camera, holiday)
        assertEquals(AlbumPreferences(), after)
    }
}
