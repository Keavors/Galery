package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Finding the video hidden inside a photograph.
 *
 * Neither convention is documented by anybody, so the rules are guarded here in
 * both directions: a file that has a video must give up its offset, and a file
 * that has none must not be talked into producing one.
 */
class MotionPhotoTest {

    private fun jpeg(size: Int = 64) = ByteArray(size) { (it % 251).toByte() }

    private fun mp4(): ByteArray {
        // A length of 24 and then "ftyp", which is how every MP4 starts.
        val header = byteArrayOf(0, 0, 0, 24) + "ftyp".toByteArray(Charsets.US_ASCII)
        return header + ByteArray(16) { 7 }
    }

    @Test
    fun `an ordinary photograph has nothing in it`() {
        assertNull(motionVideoStart(jpeg()))
    }

    @Test
    fun `the samsung marker names the place exactly`() {
        val head = jpeg()
        val marker = "MotionPhoto_Data".toByteArray(Charsets.US_ASCII)
        val bytes = head + marker + mp4()

        assertEquals(head.size + marker.size, motionVideoStart(bytes))
    }

    @Test
    fun `an appended mp4 is found by its own header`() {
        val head = jpeg()
        assertEquals(head.size, motionVideoStart(head + mp4()))
    }

    @Test
    fun `the letters ftyp inside the pixels are not a video`() {
        // The same four letters, but with nonsense where the box length should
        // be: a real box announces a small length before it names itself.
        val decoy = byteArrayOf(-1, -1, -1, -1) + "ftyp".toByteArray(Charsets.US_ASCII)
        assertNull(motionVideoStart(jpeg() + decoy + jpeg()))
    }

    @Test
    fun `the last video wins when the letters appear twice`() {
        val head = jpeg()
        val decoy = byteArrayOf(-1, -1, -1, -1) + "ftyp".toByteArray(Charsets.US_ASCII)
        val bytes = head + decoy + mp4()

        assertEquals(head.size + decoy.size, motionVideoStart(bytes))
    }

    @Test
    fun `a marker with nothing after it is not a video`() {
        val marker = "MotionPhoto_Data".toByteArray(Charsets.US_ASCII)
        assertNull(motionVideoStart(jpeg() + marker))
    }
}
