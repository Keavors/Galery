package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a video is worth taking back to where it stopped.
 *
 * The rule has two edges and both matter: offering to resume a video ten seconds
 * in is offering nothing, and offering to resume one fifteen seconds from the
 * end is a way of never seeing the beginning again.
 */
class WatchPositionsTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun `a video left in the middle is remembered`() {
        val kept = WatchPositions().remembering(id = 1, positionMs = hour / 2, durationMs = hour)
        assertEquals(hour / 2, kept.of(1))
    }

    @Test
    fun `a video barely started is not`() {
        val kept = WatchPositions().remembering(id = 1, positionMs = 5_000, durationMs = hour)
        assertNull(kept.of(1))
    }

    @Test
    fun `a video watched to the end is forgotten rather than resumed at the credits`() {
        val kept = WatchPositions().remembering(id = 1, positionMs = hour - 5_000, durationMs = hour)
        assertNull(kept.of(1))
    }

    @Test
    fun `watching one to the end forgets where it was before`() {
        val started = WatchPositions().remembering(1, hour / 2, hour)
        val finished = started.remembering(1, hour - 1_000, hour)
        assertNull(finished.of(1))
    }

    @Test
    fun `a video of unknown length is not remembered at all`() {
        // Without a length there is no telling the middle from the end.
        assertNull(WatchPositions().remembering(1, 30_000, 0).of(1))
    }

    @Test
    fun `the oldest is forgotten first, and the one just watched is not`() {
        var positions = WatchPositions()
        for (id in 1L..205L) {
            positions = positions.remembering(id, hour / 2, hour)
        }
        assertEquals(200, positions.at.size)
        assertNull(positions.of(1))
        assertEquals(hour / 2, positions.of(205))
    }

    @Test
    fun `watching an old one again moves it out of the way of forgetting`() {
        var positions = WatchPositions()
        for (id in 1L..200L) positions = positions.remembering(id, hour / 2, hour)
        positions = positions.remembering(1, hour / 3, hour)
        for (id in 201L..205L) positions = positions.remembering(id, hour / 2, hour)

        assertEquals(hour / 3, positions.of(1))
        assertNull(positions.of(2))
    }

    @Test
    fun `what goes in comes back out`() {
        val positions = WatchPositions(mapOf(7L to 1234L, 9L to 5678L))
        assertEquals(positions, decodeWatchPositions(encodeWatchPositions(positions)))
    }

    @Test
    fun `nothing stored, and nonsense stored, both read as nothing watched`() {
        assertEquals(WatchPositions(), decodeWatchPositions(null))
        assertEquals(WatchPositions(), decodeWatchPositions("{not json"))
        assertTrue(decodeWatchPositions("""{"nonsense":1}""").at.isEmpty())
    }
}
