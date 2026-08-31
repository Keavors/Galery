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
