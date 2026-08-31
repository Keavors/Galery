package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A minute, which is about as long as anything anybody trims. */
private const val MINUTE = 60_000L

class TrimRangeTest {

    @Test
    fun `an untouched trim keeps the whole video`() {
        val whole = TrimRange.whole(MINUTE)
        assertTrue(whole.isWhole(MINUTE))
        assertEquals(MINUTE, whole.lengthMs)
    }

    @Test
    fun `moving either end means it is no longer the whole video`() {
        assertFalse(TrimRange.whole(MINUTE).startAt(1_000L).isWhole(MINUTE))
        assertFalse(TrimRange.whole(MINUTE).endAt(59_000L, MINUTE).isWhole(MINUTE))
    }

    @Test
    fun `the handles cannot cross`() {
        // The one thing that must not happen: a clip that ends before it starts
        // is a file the encoder either refuses or writes empty.
        val range = TrimRange(10_000L, 20_000L)
        assertTrue(range.startAt(50_000L).lengthMs >= TrimRange.MIN_LENGTH_MS)
        assertTrue(range.endAt(0L, MINUTE).lengthMs >= TrimRange.MIN_LENGTH_MS)
    }

    @Test
    fun `neither handle leaves the video`() {
        val range = TrimRange(10_000L, 20_000L)
        assertEquals(0L, range.startAt(-5_000L).startMs)
        assertEquals(MINUTE, range.endAt(MINUTE * 2, MINUTE).endMs)
    }

    @Test
    fun `a handle dragged to the far end stops a clip short of it`() {
        val range = TrimRange(0L, MINUTE)
        assertEquals(MINUTE - TrimRange.MIN_LENGTH_MS, range.startAt(MINUTE).startMs)
    }

    @Test
    fun `a video shorter than the shortest trim is still a range`() {
        // A two-hundred-millisecond clip exists, and opening the trimmer on one
        // must not produce a range that ends before it starts.
        val tiny = TrimRange.whole(200L)
        assertTrue(tiny.lengthMs >= TrimRange.MIN_LENGTH_MS)
    }
}

class FrameTimesTest {

    @Test
    fun `frames are spread across the whole video`() {
        val times = frameTimesMs(MINUTE, 4)
        assertEquals(4, times.size)
        assertTrue(times.first() > 0L)
        assertTrue(times.last() < MINUTE)
        assertTrue(times.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `the first frame is not the black one every video opens with`() {
        // Taken from the middle of each slice rather than its start, which is
        // the difference between a strip of pictures and a strip of black.
        assertTrue(frameTimesMs(MINUTE, 8).first() >= MINUTE / 16)
    }

    @Test
    fun `a video with no length asks for no frames`() {
        assertTrue(frameTimesMs(0L, 8).isEmpty())
        assertTrue(frameTimesMs(MINUTE, 0).isEmpty())
    }
}
