package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private val BRIGHTNESS = EditStep(EditStep.Kind.ADJUST, "BRIGHTNESS")
private val CONTRAST = EditStep(EditStep.Kind.ADJUST, "CONTRAST")
private val TURN = EditStep(EditStep.Kind.TURN)
private val CROP = EditStep(EditStep.Kind.CROP)

private fun bright(value: Float) = EditOps.None.adjusted(Adjustments(brightness = value))

class EditHistoryTest {

    @Test
    fun `a fresh history has nowhere to go`() {
        val history = EditHistory()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertTrue(history.present.isIdentity)
    }

    @Test
    fun `one change can be taken back`() {
        val turned = EditHistory().with(EditOps.None.turned(), TURN)
        assertTrue(turned.canUndo)
        assertEquals(1, turned.present.quarterTurns)
        assertEquals(0, turned.undone().present.quarterTurns)
    }

    @Test
    fun `a change that changes nothing is not a step`() {
        // Sliders report the value they are already at, and a slider put back
        // where it started is not something anybody wants to undo.
        val history = EditHistory().with(bright(0.4f), BRIGHTNESS)
        assertSame(history, history.with(bright(0.4f), BRIGHTNESS))
    }

    @Test
    fun `a slider dragged across the screen is one step back`() {
        // The whole reason a step says what kind it is. Two hundred reported
        // values on the way from nothing to a half are one act.
        var history = EditHistory()
        for (step in 1..200) {
            history = history.with(bright(step / 400f), BRIGHTNESS)
        }
        assertEquals(0.5f, history.present.adjustments.brightness, 1e-5f)
        assertTrue(history.past.size == 1)
        assertTrue(history.undone().present.isIdentity)
    }

    @Test
    fun `moving to a different slider starts a step of its own`() {
        val history = EditHistory()
            .with(bright(0.5f), BRIGHTNESS)
            .with(bright(0.5f).adjusted(Adjustments(brightness = 0.5f, contrast = 0.3f)), CONTRAST)

        assertEquals(0.3f, history.present.adjustments.contrast, 1e-5f)
        // Back to the brightness alone, not back to nothing.
        val undone = history.undone()
        assertEquals(0f, undone.present.adjustments.contrast, 1e-5f)
        assertEquals(0.5f, undone.present.adjustments.brightness, 1e-5f)
    }

    @Test
    fun `pressing a button twice is two steps`() {
        // Unlike a slider: both turns were meant, and undoing has to give back
        // one of them rather than both.
        val history = EditHistory()
            .with(EditOps.None.turned(), TURN)
            .with(EditOps.None.turned().turned(), TURN)

        assertEquals(2, history.present.quarterTurns)
        assertEquals(1, history.undone().present.quarterTurns)
    }

    @Test
    fun `what was undone can be done again`() {
        val history = EditHistory().with(bright(0.6f), BRIGHTNESS).undone()
        assertTrue(history.canRedo)
        assertEquals(0.6f, history.redone().present.adjustments.brightness, 1e-5f)
    }

    @Test
    fun `going somewhere new closes the way forward`() {
        val history = EditHistory()
            .with(bright(0.6f), BRIGHTNESS)
            .undone()
            .with(EditOps.None.turned(), TURN)

        assertFalse(history.canRedo)
    }

    @Test
    fun `an undo is a full stop`() {
        // Otherwise the change after an undo would be folded into the step that
        // was just undone, and the picture would have nowhere to go back to.
        val history = EditHistory()
            .with(bright(0.2f), BRIGHTNESS)
            .undone()
            .with(bright(0.9f), BRIGHTNESS)

        assertTrue(history.canUndo)
        assertTrue(history.undone().present.isIdentity)
    }

    @Test
    fun `it does not remember further back than it says`() {
        var history = EditHistory()
        for (step in 1..EditHistory.DEPTH * 2) {
            history = history.with(EditOps.None.copy(quarterTurns = step % 4), EditStep(EditStep.Kind.TURN, "$step"))
        }
        assertEquals(EditHistory.DEPTH, history.past.size)
    }

    @Test
    fun `undoing with nothing behind it does nothing at all`() {
        val history = EditHistory().with(EditOps.None.cropped(CropRect(0f, 0f, 0.5f, 0.5f)), CROP)
        val far = history.undone().undone().undone()
        assertTrue(far.present.isIdentity)
        assertTrue(far.canRedo)
    }
}
