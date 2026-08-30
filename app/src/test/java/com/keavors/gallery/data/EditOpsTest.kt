package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropRectTest {

    @Test
    fun `an untouched crop is the whole picture`() {
        assertTrue(CropRect.Whole.isWhole)
        assertEquals(1f, CropRect.Whole.width, 0f)
    }

    @Test
    fun `a rectangle dragged backwards is put the right way round`() {
        val backwards = CropRect(left = 0.8f, top = 0.9f, right = 0.2f, bottom = 0.1f).sane()
        assertEquals(0.2f, backwards.left, 1e-5f)
        assertEquals(0.8f, backwards.right, 1e-5f)
        assertEquals(0.1f, backwards.top, 1e-5f)
        assertEquals(0.9f, backwards.bottom, 1e-5f)
    }

    @Test
    fun `a rectangle dragged off the edge is pulled back inside`() {
        val outside = CropRect(left = -0.5f, top = -1f, right = 2f, bottom = 3f).sane()
        assertTrue(outside.isWhole)
    }
}

class TurningTest {

    /** A strip along the top-left of the picture. */
    private val corner = CropRect(left = 0f, top = 0f, right = 0.25f, bottom = 0.5f)

    @Test
    fun `turning the picture turns the crop with it`() {
        // Otherwise the frame stays where it was on screen and ends up over a
        // different part of the photograph.
        val turned = corner.turnedClockwise()
        assertEquals(0.5f, turned.left, 1e-5f)
        assertEquals(0f, turned.top, 1e-5f)
        assertEquals(1f, turned.right, 1e-5f)
        assertEquals(0.25f, turned.bottom, 1e-5f)
    }

    @Test
    fun `four turns bring the crop back where it started`() {
        var rect = corner
        repeat(4) { rect = rect.turnedClockwise() }
        assertEquals(corner.left, rect.left, 1e-5f)
        assertEquals(corner.top, rect.top, 1e-5f)
        assertEquals(corner.right, rect.right, 1e-5f)
        assertEquals(corner.bottom, rect.bottom, 1e-5f)
    }

    @Test
    fun `mirroring twice is doing nothing`() {
        val back = corner.mirroredHorizontally().mirroredHorizontally()
        assertEquals(corner.left, back.left, 1e-5f)
        assertEquals(corner.right, back.right, 1e-5f)
    }

    @Test
    fun `the whole picture stays whole however it is turned`() {
        assertTrue(CropRect.Whole.turnedClockwise().isWhole)
        assertTrue(CropRect.Whole.mirroredHorizontally().isWhole)
    }
}

class EditOpsTest {

    @Test
    fun `an untouched edit changes nothing`() {
        assertTrue(EditOps.None.isIdentity)
    }

    @Test
    fun `four turns is no turn at all`() {
        var ops = EditOps.None
        repeat(4) { ops = ops.turned() }
        assertEquals(0, ops.quarterTurns)
        assertTrue(ops.isIdentity)
    }

    @Test
    fun `flipping twice is doing nothing`() {
        assertTrue(EditOps.None.flipped().flipped().isIdentity)
    }

    @Test
    fun `straightening past the limit stops at it`() {
        // Beyond this it is a rotation, and there is a button for that.
        assertEquals(EditOps.MAX_STRAIGHTEN, EditOps.None.straightened(90f).straighten, 1e-5f)
        assertEquals(-EditOps.MAX_STRAIGHTEN, EditOps.None.straightened(-90f).straighten, 1e-5f)
    }

    @Test
    fun `a crop makes the edit no longer an identity`() {
        assertFalse(EditOps.None.cropped(CropRect(0.1f, 0.1f, 0.9f, 0.9f)).isIdentity)
    }
}

class OutputSizeTest {

    @Test
    fun `an untouched photo keeps its size`() {
        assertEquals(4000 to 3000, outputSize(4000, 3000, EditOps.None))
    }

    @Test
    fun `a right-angle turn swaps the sides`() {
        assertEquals(3000 to 4000, outputSize(4000, 3000, EditOps(quarterTurns = 1)))
        assertEquals(4000 to 3000, outputSize(4000, 3000, EditOps(quarterTurns = 2)))
    }

    @Test
    fun `a crop takes its share of the sides`() {
        val half = EditOps(crop = CropRect(0.25f, 0f, 0.75f, 0.5f))
        assertEquals(2000 to 1500, outputSize(4000, 3000, half))
    }

    @Test
    fun `a turn and a crop compose in that order`() {
        val ops = EditOps(quarterTurns = 1, crop = CropRect(0f, 0f, 0.5f, 1f))
        assertEquals(1500 to 4000, outputSize(4000, 3000, ops))
    }

    @Test
    fun `a vanishingly small crop still leaves a picture`() {
        val (w, h) = outputSize(4000, 3000, EditOps(crop = CropRect(0f, 0f, 0.00001f, 0.00001f)))
        assertTrue(w >= 1 && h >= 1)
    }
}

class MemoryCeilingTest {

    @Test
    fun `the ceiling comes from the heap the device actually has`() {
        // A quarter of the heap, at four bytes a pixel: one sixteenth of it.
        val heap = 512L * 1024 * 1024
        assertEquals((heap / 16).toInt(), maxEditablePixels(heap))
        assertEquals((heap / 32).toInt(), maxEditablePixels(heap / 2))
    }

    @Test
    fun `a tiny heap still leaves something worth calling a picture`() {
        assertTrue(maxEditablePixels(16L * 1024 * 1024) >= 2_000_000)
    }

    @Test
    fun `an enormous heap does not mean an enormous bitmap`() {
        assertTrue(maxEditablePixels(8L * 1024 * 1024 * 1024) <= 40_000_000)
    }

    @Test
    fun `an ordinary phone photo needs no shrinking`() {
        assertFalse(needsDownscale(4000, 3000, 32_000_000))
        assertEquals(1, sampleSizeFor(4000, 3000, 32_000_000))
    }

    @Test
    fun `a two hundred megapixel photo is halved until it fits`() {
        assertTrue(needsDownscale(16320, 12240, 32_000_000))
        val sample = sampleSizeFor(16320, 12240, 32_000_000)
        assertEquals(4, sample)
        assertFalse(needsDownscale(16320 / sample, 12240 / sample, 32_000_000))
    }

    @Test
    fun `sampling is a power of two and never runs away`() {
        val sample = sampleSizeFor(100_000, 100_000, 2_000_000)
        assertTrue(sample <= 32)
        assertEquals(0, sample and (sample - 1))
    }
}
