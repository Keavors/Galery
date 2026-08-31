package com.keavors.gallery.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.keavors.gallery.data.CropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterboxTest {

    @Test
    fun `a wide picture in a tall space fills the width and is centred`() {
        val frame = letterboxIn(Size(1000f, 2000f), imageWidth = 400, imageHeight = 200)
        assertEquals(0f, frame.left, 1e-3f)
        assertEquals(1000f, frame.width, 1e-3f)
        assertEquals(500f, frame.height, 1e-3f)
        assertEquals(750f, frame.top, 1e-3f)
    }

    @Test
    fun `a tall picture in a wide space fills the height and is centred`() {
        val frame = letterboxIn(Size(2000f, 1000f), imageWidth = 200, imageHeight = 400)
        assertEquals(1000f, frame.height, 1e-3f)
        assertEquals(500f, frame.width, 1e-3f)
        assertEquals(750f, frame.left, 1e-3f)
        assertEquals(0f, frame.top, 1e-3f)
    }

    @Test
    fun `a picture keeps its shape whatever it is put in`() {
        val frame = letterboxIn(Size(1080f, 1900f), imageWidth = 4032, imageHeight = 3024)
        assertEquals(4032f / 3024f, frame.width / frame.height, 1e-3f)
    }

    @Test
    fun `nothing is laid out before there is anywhere to lay it out`() {
        // Touches arrive before the first measure on a slow frame, and a frame
        // of NaNs would put the crop somewhere no finger could reach.
        assertEquals(Rect.Zero, letterboxIn(Size.Zero, 400, 300))
        assertEquals(Rect.Zero, letterboxIn(Size(100f, 100f), 0, 0))
    }
}

class CropOnCanvasTest {

    /** A photograph 800 wide by 600 tall, sitting 100 down from the top. */
    private val frame = Rect(0f, 100f, 800f, 700f)

    @Test
    fun `the whole picture is the whole frame`() {
        assertEquals(frame, CropRect.Whole.on(frame))
    }

    @Test
    fun `a quarter in the corner lands on that corner of the picture`() {
        val corner = CropRect(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f).on(frame)
        assertEquals(0f, corner.left, 1e-3f)
        assertEquals(100f, corner.top, 1e-3f)
        assertEquals(400f, corner.right, 1e-3f)
        assertEquals(400f, corner.bottom, 1e-3f)
    }

    @Test
    fun `dragging by so many pixels moves the frame by exactly that many`() {
        // The whole point of the fractions: what the finger travels and what the
        // frame travels have to be the same distance, or the frame slides away
        // from under the touch.
        val start = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val moved = start.moved(Grab.WHOLE, dx = 80f / frame.width, dy = 60f / frame.height)
        assertEquals(start.on(frame).left + 80f, moved.on(frame).left, 1e-3f)
        assertEquals(start.on(frame).top + 60f, moved.on(frame).top, 1e-3f)
    }
}

class GrabTest {

    private val frame = Rect(0f, 0f, 800f, 600f)
    private val reach = 36f
    private val middle = CropRect(0.25f, 0.25f, 0.75f, 0.75f)

    @Test
    fun `a finger on a corner takes that corner`() {
        assertEquals(Grab.TOP_LEFT, grabFor(Offset(200f, 150f), middle, frame, reach))
        assertEquals(Grab.BOTTOM_RIGHT, grabFor(Offset(600f, 450f), middle, frame, reach))
        assertEquals(Grab.TOP_RIGHT, grabFor(Offset(590f, 160f), middle, frame, reach))
        assertEquals(Grab.BOTTOM_LEFT, grabFor(Offset(210f, 440f), middle, frame, reach))
    }

    @Test
    fun `a finger in the middle takes the whole frame`() {
        assertEquals(Grab.WHOLE, grabFor(Offset(400f, 300f), middle, frame, reach))
    }

    @Test
    fun `a finger on the picture outside the frame takes nothing`() {
        assertEquals(Grab.NONE, grabFor(Offset(50f, 50f), middle, frame, reach))
    }

    @Test
    fun `on a small frame the nearest corner wins`() {
        // Every corner of a crop this size is within reach of every touch. Asked
        // in a fixed order they would all answer the top left, and the frame
        // would only ever grow up and to the left.
        val small = CropRect(0.5f, 0.5f, 0.55f, 0.55f)
        val bottomRight = small.on(frame).bottomRight
        assertEquals(Grab.BOTTOM_RIGHT, grabFor(bottomRight, small, frame, reach))
        assertEquals(Grab.TOP_LEFT, grabFor(small.on(frame).topLeft, small, frame, reach))
    }

    @Test
    fun `a finger on a side takes that side`() {
        val on = middle.on(frame)
        assertEquals(Grab.LEFT, grabFor(Offset(on.left, on.center.y), middle, frame, reach))
        assertEquals(Grab.RIGHT, grabFor(Offset(on.right, on.center.y), middle, frame, reach))
        assertEquals(Grab.TOP, grabFor(Offset(on.center.x, on.top), middle, frame, reach))
        assertEquals(Grab.BOTTOM, grabFor(Offset(on.center.x, on.bottom), middle, frame, reach))
    }

    @Test
    fun `a side is grabbed from just outside it as well as just inside`() {
        val on = middle.on(frame)
        assertEquals(Grab.LEFT, grabFor(Offset(on.left - 10f, on.center.y), middle, frame, reach))
        assertEquals(Grab.LEFT, grabFor(Offset(on.left + 10f, on.center.y), middle, frame, reach))
    }

    @Test
    fun `a corner beats the two sides that meet there`() {
        // A finger on a corner is on both of its sides as well, and moving one
        // edge when two were meant is the thing that reads as a fight.
        val on = middle.on(frame)
        assertEquals(Grab.TOP_LEFT, grabFor(on.topLeft, middle, frame, reach))
    }

    @Test
    fun `a side is not grabbed off the end of it`() {
        // Level with the left edge but well above the frame: the reach around a
        // side must not stretch up the picture past where the side stops.
        val on = middle.on(frame)
        assertEquals(Grab.NONE, grabFor(Offset(on.left, on.top - 120f), middle, frame, reach))
    }

    @Test
    fun `nothing is grabbed before the picture has been placed`() {
        assertEquals(Grab.NONE, grabFor(Offset(10f, 10f), middle, Rect.Zero, reach))
    }
}

class MovedTest {

    @Test
    fun `a corner stops before it crosses the other side`() {
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val squashed = crop.moved(Grab.TOP_LEFT, dx = 1f, dy = 1f)
        assertEquals(0.8f - CropRect.MIN_SIDE, squashed.left, 1e-5f)
        assertEquals(0.8f - CropRect.MIN_SIDE, squashed.top, 1e-5f)
        assertTrue(squashed.width > 0f && squashed.height > 0f)
    }

    @Test
    fun `a corner stops at the edge of the picture`() {
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val opened = crop.moved(Grab.BOTTOM_RIGHT, dx = 1f, dy = 1f)
        assertEquals(1f, opened.right, 1e-5f)
        assertEquals(1f, opened.bottom, 1e-5f)
    }

    @Test
    fun `the whole frame slides without changing size`() {
        val crop = CropRect(0.1f, 0.1f, 0.5f, 0.5f)
        val slid = crop.moved(Grab.WHOLE, dx = 0.2f, dy = 0.3f)
        assertEquals(crop.width, slid.width, 1e-5f)
        assertEquals(crop.height, slid.height, 1e-5f)
        assertEquals(0.3f, slid.left, 1e-5f)
        assertEquals(0.4f, slid.top, 1e-5f)
    }

    @Test
    fun `the whole frame stops at the edge rather than shrinking against it`() {
        val crop = CropRect(0.1f, 0.1f, 0.5f, 0.5f)
        val pushed = crop.moved(Grab.WHOLE, dx = 5f, dy = -5f)
        assertEquals(1f, pushed.right, 1e-5f)
        assertEquals(0f, pushed.top, 1e-5f)
        assertEquals(crop.width, pushed.width, 1e-5f)
        assertEquals(crop.height, pushed.height, 1e-5f)
    }

    @Test
    fun `a side moves alone and leaves the other three where they were`() {
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val pulled = crop.moved(Grab.LEFT, dx = -0.1f, dy = 0.5f)
        assertEquals(0.1f, pulled.left, 1e-5f)
        assertEquals(0.2f, pulled.top, 1e-5f)
        assertEquals(0.8f, pulled.right, 1e-5f)
        assertEquals(0.8f, pulled.bottom, 1e-5f)
    }

    @Test
    fun `a side ignores the drag across it`() {
        // The bottom edge is dragged sideways as well as up; only the up counts.
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val raised = crop.moved(Grab.BOTTOM, dx = 0.3f, dy = -0.2f)
        assertEquals(0.6f, raised.bottom, 1e-5f)
        assertEquals(0.2f, raised.left, 1e-5f)
        assertEquals(0.8f, raised.right, 1e-5f)
    }

    @Test
    fun `a side stops before it crosses the one opposite`() {
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val squashed = crop.moved(Grab.RIGHT, dx = -1f, dy = 0f)
        assertEquals(0.2f + CropRect.MIN_SIDE, squashed.right, 1e-5f)
    }

    @Test
    fun `a side stops at the edge of the picture`() {
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(0f, crop.moved(Grab.TOP, dx = 0f, dy = -1f).top, 1e-5f)
    }

    @Test
    fun `a corner is the two sides that meet there`() {
        // Not a separate rule, and this is what says so: dragging a corner has
        // to land where dragging each of its sides in turn would have.
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        val byCorner = crop.moved(Grab.TOP_LEFT, dx = 0.1f, dy = 0.05f)
        val bySides = crop.moved(Grab.LEFT, dx = 0.1f, dy = 0.05f)
            .moved(Grab.TOP, dx = 0.1f, dy = 0.05f)
        assertEquals(bySides, byCorner)
    }

    @Test
    fun `a drag that grabbed nothing changes nothing`() {
        val crop = CropRect(0.1f, 0.2f, 0.3f, 0.4f)
        assertEquals(crop, crop.moved(Grab.NONE, dx = 0.5f, dy = 0.5f))
    }
}

class FixedShapeTest {

    /** A photograph half again as wide as it is tall. */
    private val wide = 3f / 2f

    @Test
    fun `a square crop of a wide photograph is not square in fractions`() {
        // The trap this function exists for: a crop is fractions of the picture,
        // so equal fractions on a 3:2 photograph are a 3:2 rectangle, not a
        // square.
        val square = CropRect.Whole.keeping(ratio = 1f, pictureShape = wide, grab = Grab.NONE)
        assertEquals(1f, square.width * wide / square.height, 1e-4f)
    }

    @Test
    fun `a shape only ever shrinks the frame`() {
        val fitted = CropRect.Whole.keeping(ratio = 1f, pictureShape = wide, grab = Grab.NONE)
        assertTrue(fitted.width <= 1f && fitted.height <= 1f)
        assertTrue(fitted.left >= 0f && fitted.top >= 0f)
        assertTrue(fitted.right <= 1f && fitted.bottom <= 1f)
    }

    @Test
    fun `with nothing being dragged it shrinks about the middle`() {
        val fitted = CropRect.Whole.keeping(ratio = 1f, pictureShape = wide, grab = Grab.NONE)
        assertEquals(0.5f, (fitted.left + fitted.right) / 2f, 1e-4f)
        assertEquals(0.5f, (fitted.top + fitted.bottom) / 2f, 1e-4f)
    }

    @Test
    fun `the corner being dragged stays under the finger`() {
        // Held by the opposite corner: the one being pulled is where the finger
        // is, and a frame that jumps out from under it is unusable.
        val crop = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        val fitted = crop.keeping(ratio = 1f, pictureShape = wide, grab = Grab.TOP_LEFT)
        assertEquals(0.9f, fitted.right, 1e-4f)
        assertEquals(0.9f, fitted.bottom, 1e-4f)
    }

    @Test
    fun `dragging a side holds the side opposite`() {
        val crop = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        val fitted = crop.keeping(ratio = 1f, pictureShape = wide, grab = Grab.RIGHT)
        assertEquals(0.1f, fitted.left, 1e-4f)
        // And centres what the drag was not touching.
        assertEquals(0.5f, (fitted.top + fitted.bottom) / 2f, 1e-4f)
    }

    @Test
    fun `a shape that would be thinner than a crop may be is widened to fit`() {
        val thin = CropRect(0.4f, 0.4f, 0.42f, 0.42f)
        val fitted = thin.keeping(ratio = 16f / 9f, pictureShape = wide, grab = Grab.NONE)
        assertTrue(fitted.width >= CropRect.MIN_SIDE || fitted.height >= CropRect.MIN_SIDE)
        assertTrue(fitted.left >= 0f && fitted.right <= 1f)
    }

    @Test
    fun `a shape asked of a picture with no size changes nothing`() {
        val crop = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertEquals(crop, crop.keeping(ratio = 1f, pictureShape = 0f, grab = Grab.NONE))
        assertEquals(crop, crop.keeping(ratio = 0f, pictureShape = 1f, grab = Grab.NONE))
    }
}
