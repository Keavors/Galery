package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultIndexTest {

    private val entries = listOf(
        VaultEntry(1, "v1", "IMG_0001.jpg", "image/jpeg", false, 1000, 500, "DCIM/Camera/"),
        VaultEntry(2, "v2", "clip.mp4", "video/mp4", true, 2000, 400, "Movies/"),
    )

    @Test
    fun `what goes in comes back out`() {
        assertEquals(entries, decodeVault(encodeVault(entries)))
    }

    @Test
    fun `an empty vault round trips`() {
        assertEquals(emptyList<VaultEntry>(), decodeVault(encodeVault(emptyList())))
        assertEquals(emptyList<VaultEntry>(), decodeVault(null))
    }

    @Test
    fun `a damaged index costs the list, not the app`() {
        assertEquals(emptyList<VaultEntry>(), decodeVault("{not an array"))
    }

    @Test
    fun `one unreadable entry does not take the others with it`() {
        // The files are still on disk either way; losing one record beats
        // losing the whole list.
        val json = """[{"file":"v1","name":"a.jpg"},{"nofile":true},{"file":"v3","name":"c.jpg"}]"""
        val decoded = decodeVault(json)
        assertEquals(listOf("v1", "v3"), decoded.map { it.fileName })
    }

    @Test
    fun `stored names give nothing away`() {
        // A listing of the vault directory should say nothing about what is in
        // it, and two photos with the same name must not collide.
        assertEquals("v42", vaultFileName(42))
        assertTrue(vaultFileName(1) != vaultFileName(2))
    }
}

class RestorePathTest {

    private fun entry(path: String, video: Boolean = false) =
        VaultEntry(1, "v1", "a.jpg", "image/jpeg", video, 1, 1, path)

    @Test
    fun `a file goes back where it came from`() {
        assertEquals("DCIM/Camera/", restorePathFor(entry("DCIM/Camera/")))
        assertEquals("Pictures/Saved/", restorePathFor(entry("Pictures/Saved")))
    }

    @Test
    fun `no remembered path means the standard folder`() {
        assertEquals("Pictures/", restorePathFor(entry("")))
        assertEquals("Movies/", restorePathFor(entry("", video = true)))
    }

    @Test
    fun `an absolute path is not trusted`() {
        // A path from another volume would either fail the insert or put the
        // file somewhere nobody expects.
        assertEquals("Pictures/", restorePathFor(entry("/storage/emulated/0/DCIM/")))
    }

    @Test
    fun `a hidden folder is not restored into`() {
        assertEquals("Pictures/", restorePathFor(entry(".thumbnails/")))
    }
}

class HidingRuleTest {

    @Test
    fun `an ordinary photo can be hidden`() {
        assertTrue(canBeHidden(testItem(1)))
    }

    @Test
    fun `a file already in the vault cannot be hidden again`() {
        // The crash this guards: the copy went through, then the delete request
        // was handed a file MediaStore has never heard of and refused it — with
        // the copy already recorded, which is where the duplicate came from.
        assertFalse(canBeHidden(testItem(1, isPrivate = true)))
    }
}
