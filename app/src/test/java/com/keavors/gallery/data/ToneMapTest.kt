package com.keavors.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A pixel from its three channels, opaque, the way a bitmap hands them over. */
private fun argb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

private fun red(pixel: Int) = pixel shr 16 and 0xFF
private fun green(pixel: Int) = pixel shr 8 and 0xFF
private fun blue(pixel: Int) = pixel and 0xFF
private fun alpha(pixel: Int) = pixel ushr 24 and 0xFF

class ToneCurveTest {

    @Test
    fun `neutral sliders leave the value alone`() {
        assertEquals(0.4f, tonedChannel(0.4f, luma = 0.4f, shadows = 0f, highlights = 0f), 1e-5f)
    }

    @Test
    fun `lifting the shadows lifts the shadows and not the sky`() {
        // The point of the mask. Without it this would be brightness with a
        // longer name, and the white would go grey along with everything else.
        val dark = tonedChannel(0.05f, luma = 0.05f, shadows = 1f, highlights = 0f)
        val light = tonedChannel(0.98f, luma = 0.98f, shadows = 1f, highlights = 0f)
        assertTrue(dark > 0.3f)
        assertEquals(0.98f, light, 0.01f)
    }

    @Test
    fun `pulling the highlights back leaves the shadows where they are`() {
        val light = tonedChannel(0.95f, luma = 0.95f, shadows = 0f, highlights = -1f)
        val dark = tonedChannel(0.04f, luma = 0.04f, shadows = 0f, highlights = -1f)
        assertTrue(light < 0.7f)
        assertEquals(0.04f, dark, 0.01f)
    }

    @Test
    fun `a channel never leaves the scale it lives on`() {
        assertEquals(1f, tonedChannel(0.99f, luma = 0.99f, shadows = 0f, highlights = 1f), 1e-5f)
        assertEquals(0f, tonedChannel(0.01f, luma = 0.01f, shadows = -1f, highlights = 0f), 1e-5f)
    }

    @Test
    fun `a grey pixel stays grey`() {
        // Every channel is moved by the same amount, which is what keeps these
        // two sliders out of the colour's business.
        val toned = intArrayOf(argb(60, 60, 60))
        tonePixels(toned, 1, 1, Adjustments(shadows = 0.6f))
        assertEquals(red(toned[0]), green(toned[0]))
        assertEquals(green(toned[0]), blue(toned[0]))
        assertTrue(red(toned[0]) > 60)
    }

    @Test
    fun `what is see-through stays see-through`() {
        val pixels = intArrayOf((0x40 shl 24) or 0x203040)
        tonePixels(pixels, 1, 1, Adjustments(shadows = 1f, highlights = -1f))
        assertEquals(0x40, alpha(pixels[0]))
    }

    @Test
    fun `nothing asked for is nothing done`() {
        val before = intArrayOf(argb(10, 20, 30), argb(200, 210, 220))
        val after = before.copyOf()
        tonePixels(after, 2, 1, Adjustments.None)
        assertTrue(before.contentEquals(after))
    }
}

class SharpnessTest {

    /**
     * A three by three with a bright middle: the smallest picture that has an
     * edge in it to sharpen.
     */
    private fun spot(): IntArray = IntArray(9) { if (it == 4) argb(200, 200, 200) else argb(100, 100, 100) }

    @Test
    fun `sharpening pulls an edge further apart`() {
        val sharpened = spot()
        tonePixels(sharpened, 3, 3, Adjustments(sharpness = 1f))
        // The bright middle gets brighter and its dark surroundings darker,
        // which is all sharpening is.
        assertTrue(red(sharpened[4]) > 200)
        assertTrue(red(sharpened[0]) < 100)
    }

    @Test
    fun `the other side of neutral softens instead`() {
        val softened = spot()
        tonePixels(softened, 3, 3, Adjustments(sharpness = -1f))
        assertTrue(red(softened[4]) < 200)
    }

    @Test
    fun `a picture too small to have neighbours is left alone`() {
        // Two pixels wide there is nothing to average against, and reaching for
        // it would read off the end of the array.
        val tiny = intArrayOf(argb(10, 10, 10), argb(240, 240, 240))
        val before = tiny.copyOf()
        tonePixels(tiny, 2, 1, Adjustments(sharpness = 1f))
        assertTrue(before.contentEquals(tiny))
    }
}

class AdjustmentGroupsTest {

    @Test
    fun `each group knows only its own`() {
        // The three groups decide which of the three kinds of work a save has to
        // do, and a slider counted in the wrong one is either a correction that
        // does nothing or a pass over the pixels for no reason.
        val toneOnly = Adjustments(shadows = 0.5f)
        assertTrue(toneOnly.matrixIsNeutral)
        assertFalse(toneOnly.toneIsNeutral)
        assertFalse(toneOnly.isNeutral)

        val matrixOnly = Adjustments(contrast = 0.5f)
        assertFalse(matrixOnly.matrixIsNeutral)
        assertTrue(matrixOnly.toneIsNeutral)

        val vignetteOnly = Adjustments(vignette = 0.5f)
        assertTrue(vignetteOnly.matrixIsNeutral)
        assertTrue(vignetteOnly.toneIsNeutral)
        assertFalse(vignetteOnly.isNeutral)
    }

    @Test
    fun `a vignette darkens one way and lightens the other`() {
        assertTrue(Vignette.darkens(0.5f))
        assertFalse(Vignette.darkens(-0.5f))
        assertEquals(Vignette.opacity(0.5f), Vignette.opacity(-0.5f), 1e-5f)
        assertEquals(0f, Vignette.opacity(0f), 1e-5f)
    }
}
