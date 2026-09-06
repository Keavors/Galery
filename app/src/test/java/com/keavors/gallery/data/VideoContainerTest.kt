package com.keavors.gallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which player a file needs, decided by its first bytes.
 *
 * The rule earns its tests because it is what stands between a video and a black
 * rectangle, and because the case that prompted it was a lie: six RealMedia
 * files named .mp4, which both the name and the library called MP4.
 */
class VideoContainerTest {

    private fun head(vararg bytes: Int) = ByteArray(16) { index ->
        bytes.getOrElse(index) { 0 }.toByte()
    }

    @Test
    fun `RealMedia needs the other player, whatever it is called`() {
        // ".RMF" — and RMVB is the same container.
        assertTrue(needsFullPlayer(head(0x2E, 0x52, 0x4D, 0x46)))
    }

    @Test
    fun `so does the container WMV lives in`() {
        assertTrue(needsFullPlayer(head(0x30, 0x26, 0xB2, 0x75)))
    }

    @Test
    fun `and Flash, which still turns up in old folders`() {
        assertTrue(needsFullPlayer(head(0x46, 0x57, 0x53)))
        assertTrue(needsFullPlayer(head(0x43, 0x57, 0x53)))
    }

    @Test
    fun `an ordinary MP4 does not`() {
        // Four bytes of box length, then "ftyp".
        assertFalse(needsFullPlayer(head(0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70)))
    }

    @Test
    fun `nor Matroska, WebM or AVI, which Media3 opens on its own`() {
        assertFalse(needsFullPlayer(head(0x1A, 0x45, 0xDF, 0xA3)))
        assertFalse(needsFullPlayer(head(0x52, 0x49, 0x46, 0x46)))
    }

    @Test
    fun `a file too short to say anything is left to the ordinary player`() {
        // It will fail there and report why, which is better than being handed
        // to a second engine that would fail at it silently.
        assertFalse(needsFullPlayer(byteArrayOf()))
        assertFalse(needsFullPlayer(byteArrayOf(0x2E, 0x52)))
    }

    @Test
    fun `the signature is only read at the very front`() {
        // ".RMF" further inside a file is data, not a container.
        assertFalse(needsFullPlayer(head(0x00, 0x2E, 0x52, 0x4D, 0x46)))
    }
}
