package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNameTest {

    @Test
    fun `an ordinary name is fine`() {
        assertTrue(isUsableFolderName("Holiday"))
        assertTrue(isUsableFolderName("Отпуск 2026"))
    }

    @Test
    fun `a name that would become a path is not`() {
        assertFalse(isUsableFolderName("DCIM/Camera"))
        assertFalse(isUsableFolderName("back\\slash"))
    }

    @Test
    fun `a name that would hide the folder from every gallery is not`() {
        // A leading dot is how a folder disappears on Android. Hiding is
        // something this app does deliberately and reversibly elsewhere.
        assertFalse(isUsableFolderName(".secret"))
    }

    @Test
    fun `a name of nothing but space is not`() {
        assertFalse(isUsableFolderName(""))
        assertFalse(isUsableFolderName("   "))
    }
}

class RenamedFolderPathTest {

    @Test
    fun `only the last part of the path changes`() {
        assertEquals("DCIM/Holiday/", renamedFolderPath("DCIM/Camera/", "Holiday"))
        assertEquals(
            "Pictures/Sent/Kept/",
            renamedFolderPath("Pictures/Sent/Telegram/", "Kept"),
        )
    }

    @Test
    fun `the name is taken without its spaces`() {
        assertEquals("DCIM/Holiday/", renamedFolderPath("DCIM/Camera/", "  Holiday  "))
    }

    @Test
    fun `a missing trailing slash is not a different folder`() {
        assertEquals("DCIM/Holiday/", renamedFolderPath("DCIM/Camera", "Holiday"))
    }

    @Test
    fun `one of Android's own directories cannot be renamed`() {
        // Renaming DCIM would move every photograph on the phone somewhere no
        // other app thinks to look.
        assertNull(renamedFolderPath("DCIM/", "Holiday"))
        assertNull(renamedFolderPath("Pictures/", "Holiday"))
        assertFalse(isRenamableFolder("DCIM/"))
        assertTrue(isRenamableFolder("DCIM/Camera/"))
    }

    @Test
    fun `a name that will not do stops the rename before it starts`() {
        assertNull(renamedFolderPath("DCIM/Camera/", "a/b"))
        assertNull(renamedFolderPath("DCIM/Camera/", ""))
    }

    @Test
    fun `a new folder is made where photographs are allowed to live`() {
        assertEquals("Pictures/Holiday/", newFolderPath("Holiday"))
        assertNull(newFolderPath(" "))
    }
}

class AlreadyInFolderTest {

    @Test
    fun `a file is where it is, whatever the slashes say`() {
        val item = testItem(1).copy(relativePath = "DCIM/Camera/")
        assertTrue(isAlreadyIn(item, "DCIM/Camera/"))
        assertTrue(isAlreadyIn(item, "DCIM/Camera"))
        assertTrue(isAlreadyIn(item, "dcim/camera/"))
    }

    @Test
    fun `a file somewhere else is not`() {
        val item = testItem(1).copy(relativePath = "DCIM/Camera/")
        assertFalse(isAlreadyIn(item, "Pictures/Holiday/"))
    }
}
