package com.keavors.gallery.data

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same rectangle, to within a rounding error, side by side. */
private fun assertSame(expected: Rect, actual: Rect) {
    assertEquals(expected.left, actual.left, 1e-3f)
    assertEquals(expected.top, actual.top, 1e-3f)
    assertEquals(expected.right, actual.right, 1e-3f)
    assertEquals(expected.bottom, actual.bottom, 1e-3f)
}

class UncroppedAreaTest {

    @Test
    fun `with nothing cropped the picture is exactly what is shown`() {
        // Compared side by side rather than whole: a rectangle at the origin
        // comes out with a negative zero for its left edge, which is the same
        // number and not the same object.
        assertSame(Rect(0f, 0f, 800f, 600f), uncroppedArea(CropRect.Whole, 800f, 600f))
    }

    @Test
    fun `a half-width crop means the whole picture is twice as wide`() {
        // And starts off the left edge, which is the point: the part of a mark
        // that was cropped away has to fall outside the screen rather than
        // sliding inwards to fit.
        val area = uncroppedArea(CropRect(0.5f, 0f, 1f, 1f), 800f, 600f)
        assertEquals(1600f, area.width, 1e-3f)
        assertEquals(-800f, area.left, 1e-3f)
        assertEquals(0f, area.top, 1e-3f)
    }

    @Test
    fun `a crop out of the middle pushes the picture up and to the left`() {
        val area = uncroppedArea(CropRect(0.25f, 0.25f, 0.75f, 0.75f), 400f, 400f)
        assertEquals(800f, area.width, 1e-3f)
        assertEquals(800f, area.height, 1e-3f)
        assertEquals(-200f, area.left, 1e-3f)
        assertEquals(-200f, area.top, 1e-3f)
    }

    @Test
    fun `a mark in the middle of the picture stays in the middle of the crop`() {
        // The property that matters, said as a round trip: a mark placed at the
        // centre of the photograph is drawn at the centre of a crop that is
        // itself centred.
        val crop = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val area = uncroppedArea(crop, 400f, 400f)
        assertEquals(200f, area.left + 0.5f * area.width, 1e-3f)
        assertEquals(200f, area.top + 0.5f * area.height, 1e-3f)
    }

    @Test
    fun `a crop of nothing is not divided by`() {
        assertSame(Rect(0f, 0f, 100f, 100f), uncroppedArea(CropRect(0.5f, 0.5f, 0.5f, 0.5f), 100f, 100f))
    }
}

class StrokeWidthTest {

    @Test
    fun `thickness follows the shorter side, so it survives a resize`() {
        // The whole reason it is a fraction: a brush that looked right on a
        // preview a thousand pixels wide has to look the same on a photograph
        // eight thousand wide.
        val stroke = Mark.Stroke(points = emptyList(), colour = 0, width = 0.01f)
        assertEquals(6f, stroke.widthOn(Rect(0f, 0f, 800f, 600f)), 1e-3f)
        assertEquals(60f, stroke.widthOn(Rect(0f, 0f, 8000f, 6000f)), 1e-3f)
    }
}

class MarkedOpsTest {

    private val dot = Mark.Stroke(points = listOf(MarkPoint(0.5f, 0.5f)), colour = -1, width = 0.01f)

    @Test
    fun `a photograph with something drawn on it is edited`() {
        assertFalse(EditOps.None.marked(listOf(dot)).isIdentity)
    }

    @Test
    fun `the preview underneath carries no marks`() {
        // They are drawn over the preview rather than into it, like the crop
        // frame and the colours, so baking them in would draw them twice.
        val ops = EditOps.None.marked(listOf(dot)).turned()
        assertTrue(ops.geometryOnly.marks.isEmpty())
        assertEquals(1, ops.quarterTurns)
    }
}

class ObscuredRegionTest {

    @Test
    fun `a region dragged backwards is still a region`() {
        // Dragged up and to the left, which is half of all drags: the corner
        // that was started from stays put and the other one follows the finger,
        // so the rectangle has to be put the right way round afterwards.
        val backwards = Mark.Obscured(
            from = MarkPoint(0.8f, 0.9f),
            to = MarkPoint(0.2f, 0.3f),
            pixelated = false,
        )
        assertEquals(0.2f, backwards.bounds.left, 1e-5f)
        assertEquals(0.3f, backwards.bounds.top, 1e-5f)
        assertEquals(0.8f, backwards.bounds.right, 1e-5f)
        assertEquals(0.9f, backwards.bounds.bottom, 1e-5f)
    }
}

class SourcePixelsTest {

    /** A picture 400 by 300, shown on screen at twice that size. */
    private val occupies = Rect(0f, 0f, 800f, 600f)

    @Test
    fun `a rectangle of the screen maps to the pixels beneath it`() {
        val cut = sourcePixels(Rect(200f, 150f, 600f, 450f), occupies, 400, 300)
        assertEquals(PixelRect(100, 75, 200, 150), cut)
    }

    @Test
    fun `a rectangle hanging off the picture is trimmed to it`() {
        // Half the reason this exists: a region dragged past the edge of the
        // photograph would otherwise ask for pixels that are not there.
        val cut = sourcePixels(Rect(-500f, -500f, 400f, 300f), occupies, 400, 300)
        assertEquals(0, cut.x)
        assertEquals(0, cut.y)
        assertTrue(cut.x + cut.width <= 400)
        assertTrue(cut.y + cut.height <= 300)
    }

    @Test
    fun `a region is never nothing at all`() {
        val cut = sourcePixels(Rect(10f, 10f, 10.1f, 10.1f), occupies, 400, 300)
        assertTrue(cut.width >= 1 && cut.height >= 1)
    }

    @Test
    fun `a picture that is nowhere yet asks for nothing`() {
        assertEquals(PixelRect(0, 0, 0, 0), sourcePixels(Rect.Zero, Rect.Zero, 400, 300))
    }
}

class CoarsenTest {

    @Test
    fun `each block becomes the average of what was in it`() {
        // Two blocks across: black on the left, white on the right, and nothing
        // in between to average them into grey.
        val pixels = IntArray(4) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val blocks = coarsen(pixels, width = 2, height = 2, across = 2, down = 1)
        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0] and 0xFF)
        assertEquals(255, blocks[1] and 0xFF)
    }

    @Test
    fun `a whole region can become a single colour`() {
        val pixels = IntArray(100) { 0xFF404040.toInt() }
        val blocks = coarsen(pixels, width = 10, height = 10, across = 1, down = 1)
        assertEquals(1, blocks.size)
        assertEquals(0x40, blocks[0] and 0xFF)
    }

    @Test
    fun `the answer is the size asked for, whatever went in`() {
        // What makes hiding a sky cost the same as hiding a face: the work and
        // the result are the size of the answer, not of the question.
        val pixels = IntArray(500 * 400) { 0xFF123456.toInt() }
        assertEquals(14 * 9, coarsen(pixels, 500, 400, across = 14, down = 9).size)
    }
}
