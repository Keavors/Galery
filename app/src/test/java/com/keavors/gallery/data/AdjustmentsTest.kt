package com.keavors.gallery.data

import androidx.compose.ui.graphics.ColorMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a colour matrix does to one colour, on the 0..255 scale it works in.
 *
 * This is the definition of a colour matrix rather than a copy of any code:
 * every row is the recipe for one output channel, and the last column is a
 * constant. Getting the rows and columns the wrong way round is exactly the
 * mistake these tests exist to catch, so it is spelled out rather than reused.
 */
private fun ColorMatrix.on(red: Float, green: Float, blue: Float): Triple<Float, Float, Float> {
    fun channel(row: Int): Float {
        val at = row * 5
        return values[at] * red + values[at + 1] * green + values[at + 2] * blue +
            values[at + 3] * OPAQUE + values[at + 4]
    }
    return Triple(channel(0), channel(1), channel(2))
}

private const val OPAQUE = 255f

private fun assertColour(
    expected: Triple<Float, Float, Float>,
    actual: Triple<Float, Float, Float>,
) {
    assertEquals(expected.first, actual.first, 0.5f)
    assertEquals(expected.second, actual.second, 0.5f)
    assertEquals(expected.third, actual.third, 0.5f)
}

class AdjustmentsTest {

    @Test
    fun `nothing moved is nothing done`() {
        assertTrue(Adjustments.None.isNeutral)
        assertFalse(Adjustments.None.copy(contrast = 0.01f).isNeutral)
    }

    @Test
    fun `a picture with nothing asked of it comes out as it went in`() {
        val same = colorMatrixFor(Adjustments.None).on(10f, 120f, 240f)
        assertColour(Triple(10f, 120f, 240f), same)
    }
}

class ColourMatrixTest {

    @Test
    fun `brightness is added to every channel alike`() {
        val lifted = colorMatrixFor(Adjustments(brightness = 1f)).on(10f, 120f, 240f)
        assertColour(Triple(110f, 220f, 340f), lifted)

        val dropped = colorMatrixFor(Adjustments(brightness = -0.5f)).on(10f, 120f, 240f)
        assertColour(Triple(-40f, 70f, 190f), dropped)
    }

    @Test
    fun `exposure multiplies rather than adds, in stops`() {
        // Half a slider is one stop, which is twice the light.
        val stop = colorMatrixFor(Adjustments(exposure = 0.5f)).on(10f, 60f, 100f)
        assertColour(Triple(20f, 120f, 200f), stop)

        // All the way down is two stops, a quarter of it.
        val dark = colorMatrixFor(Adjustments(exposure = -1f)).on(40f, 80f, 200f)
        assertColour(Triple(10f, 20f, 50f), dark)
    }

    @Test
    fun `brightness happens after exposure, not before`() {
        // The order is the whole point of having both. Ten times four is forty
        // and then a hundred is a hundred and forty; the other way round it
        // would be four hundred and forty, and a slider at a third of the way
        // would blow the photograph out.
        val both = colorMatrixFor(Adjustments(exposure = 1f, brightness = 1f)).on(10f, 10f, 10f)
        assertColour(Triple(140f, 140f, 140f), both)
    }

    @Test
    fun `contrast turns the tones around mid grey`() {
        val harder = colorMatrixFor(Adjustments(contrast = 0.5f))
        // Mid grey is the pivot and does not move.
        assertColour(Triple(128f, 128f, 128f), harder.on(128f, 128f, 128f))
        // Everything else moves away from it, by half again as far.
        assertColour(Triple(68f, 188f, 128f), harder.on(88f, 168f, 128f))
    }

    @Test
    fun `saturation all the way down leaves a grey`() {
        val (r, g, b) = colorMatrixFor(Adjustments(saturation = -1f)).on(200f, 100f, 50f)
        assertEquals(r, g, 0.5f)
        assertEquals(g, b, 0.5f)
        // And a grey of about the brightness the colour had, not black.
        assertTrue(r > 80f && r < 160f)
    }

    @Test
    fun `warmth adds red and takes blue away`() {
        val warm = colorMatrixFor(Adjustments(temperature = 1f)).on(100f, 100f, 100f)
        assertColour(Triple(125f, 100f, 75f), warm)

        val cool = colorMatrixFor(Adjustments(temperature = -1f)).on(100f, 100f, 100f)
        assertColour(Triple(75f, 100f, 125f), cool)
    }

    @Test
    fun `tint moves green against the other two`() {
        val magenta = colorMatrixFor(Adjustments(tint = 1f)).on(100f, 100f, 100f)
        assertColour(Triple(115f, 85f, 115f), magenta)
    }

    @Test
    fun `two corrections at once are one matrix`() {
        // Not that the numbers are interesting — that six sliders never need six
        // passes over the picture, however many of them are moved.
        val matrix = colorMatrixFor(
            Adjustments(brightness = 0.2f, contrast = 0.3f, temperature = 0.4f)
        )
        assertEquals(20, matrix.values.size)
    }
}

class MatrixOrderTest {

    private val double = ColorMatrix(
        floatArrayOf(
            2f, 0f, 0f, 0f, 0f,
            0f, 2f, 0f, 0f, 0f,
            0f, 0f, 2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )

    private val plusTen = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 10f,
            0f, 1f, 0f, 0f, 10f,
            0f, 0f, 1f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f,
        )
    )

    @Test
    fun `then means then`() {
        // Double and then add ten is fifty; add ten and then double is sixty.
        // Reading it the other way round would silently reorder every
        // correction in the editor.
        assertColour(Triple(50f, 50f, 50f), double.then(plusTen).on(20f, 20f, 20f))
        assertColour(Triple(60f, 60f, 60f), plusTen.then(double).on(20f, 20f, 20f))
    }
}

class EditOpsWithColourTest {

    @Test
    fun `a photograph with only its colours touched is still edited`() {
        val ops = EditOps.None.adjusted(Adjustments(contrast = 0.4f))
        assertFalse(ops.isIdentity)
    }

    @Test
    fun `the preview is built from the geometry alone`() {
        // Both of the things drawn on top of the preview rather than into it:
        // the crop is a frame and the colours are a filter. Baking either into
        // the picture underneath is how the crop came to crop itself.
        val ops = EditOps.None
            .cropped(CropRect(0.1f, 0.1f, 0.6f, 0.6f))
            .adjusted(Adjustments(brightness = 0.5f))
            .turned()

        val geometry = ops.geometryOnly
        assertTrue(geometry.crop.isWhole)
        assertTrue(geometry.adjustments.isNeutral)
        assertEquals(1, geometry.quarterTurns)
    }
}

class CropPixelsTest {

    @Test
    fun `the whole picture is every pixel of it`() {
        val cut = CropRect.Whole.pixelsIn(4000, 3000)
        assertEquals(PixelRect(0, 0, 4000, 3000), cut)
    }

    @Test
    fun `a half in each direction is a quarter of the picture`() {
        val cut = CropRect(0.25f, 0.25f, 0.75f, 0.75f).pixelsIn(4000, 3000)
        assertEquals(PixelRect(1000, 750, 2000, 1500), cut)
    }

    @Test
    fun `a crop never runs off the end of the picture`() {
        // Rounding at the right-hand edge is the one that would throw when the
        // pixels were actually cut.
        val cut = CropRect(0.9999f, 0.9999f, 1f, 1f).pixelsIn(100, 100)
        assertTrue(cut.x + cut.width <= 100)
        assertTrue(cut.y + cut.height <= 100)
        assertTrue(cut.width >= 1 && cut.height >= 1)
    }

    @Test
    fun `a picture with no size yet has nothing to cut`() {
        assertEquals(PixelRect(0, 0, 0, 0), CropRect.Whole.pixelsIn(0, 0))
    }
}

class FilterStrengthTest {

    private val full = Adjustments(
        brightness = 0.4f,
        contrast = -0.2f,
        saturation = 1f,
        vignette = 0.5f,
    )

    @Test
    fun `at full strength a filter is exactly what it says`() {
        assertEquals(full, full * 1f)
    }

    @Test
    fun `at nothing a filter does nothing`() {
        assertTrue((full * 0f).isNeutral)
    }

    @Test
    fun `half way is half of every value, sign and all`() {
        val half = full * 0.5f
        assertEquals(0.2f, half.brightness, 1e-5f)
        assertEquals(-0.1f, half.contrast, 1e-5f)
        assertEquals(0.5f, half.saturation, 1e-5f)
        assertEquals(0.25f, half.vignette, 1e-5f)
    }
}
