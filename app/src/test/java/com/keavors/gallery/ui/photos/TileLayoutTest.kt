package com.keavors.gallery.ui.photos

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic that replaced the layout system for the grid.
 *
 * Every tile used to be an element that Compose measured and placed; now a row
 * is one canvas and this works out where each photograph goes. It is guarded
 * closely because there is nothing else left to be right: a mistake here is a
 * grid that overlaps, leaves gaps or hands a tap to the wrong photograph.
 */
class TileLayoutTest {

    @Test
    fun `a full row fills the width exactly`() {
        val rects = rowTileRects(count = 4, columns = 4, widthPx = 1000f, gapPx = 10f)
        assertEquals(4, rects.size)
        assertEquals(0f, rects.first().left, 0.01f)
        assertEquals(1000f, rects.last().right, 0.01f)
    }

    @Test
    fun `tiles are square and evenly spaced`() {
        val rects = rowTileRects(count = 4, columns = 4, widthPx = 1000f, gapPx = 10f)
        val side = (1000f - 30f) / 4f
        rects.forEachIndexed { index, rect ->
            assertEquals(side, rect.width, 0.01f)
            assertEquals(side, rect.height, 0.01f)
            assertEquals(index * (side + 10f), rect.left, 0.01f)
        }
    }

    @Test
    fun `a short last row keeps the tile size of a full one`() {
        val full = rowTileRects(count = 4, columns = 4, widthPx = 1000f, gapPx = 10f)
        val short = rowTileRects(count = 2, columns = 4, widthPx = 1000f, gapPx = 10f)

        assertEquals(2, short.size)
        assertEquals(full[0].width, short[0].width, 0.01f)
        assertEquals(full[1].left, short[1].left, 0.01f)
    }

    @Test
    fun `a mosaic row fills the width and keeps every proportion`() {
        val aspects = listOf(2f, 1f, 0.5f)
        val rects = rowTileRects(3, columns = 4, widthPx = 1000f, gapPx = 10f, aspects = aspects)

        assertEquals(1000f, rects.last().right, 0.05f)
        rects.forEachIndexed { index, rect ->
            assertEquals(aspects[index], rect.width / rect.height, 0.01f)
        }
        // One height for the whole row, which is what makes it a row.
        assertEquals(rects[0].height, rects[2].height, 0.01f)
    }

    @Test
    fun `nothing is ever laid out to nothing`() {
        assertTrue(rowTileRects(0, 4, 1000f, 10f).isEmpty())
        assertTrue(rowTileRects(4, 4, 0f, 10f).isEmpty())
        // Twenty-five columns on a narrow screen still leaves a tile to draw.
        assertTrue(rowTileRects(25, 25, 300f, 8f).all { it.width >= 1f })
    }

    @Test
    fun `a wide picture is cropped from the middle across`() {
        val (offset, size) = centreCrop(400, 200, Size(100f, 100f))
        assertEquals(200, size.width)
        assertEquals(200, size.height)
        assertEquals(100, offset.x)
        assertEquals(0, offset.y)
    }

    @Test
    fun `a tall picture is cropped from the middle down`() {
        val (offset, size) = centreCrop(200, 400, Size(100f, 100f))
        assertEquals(200, size.width)
        assertEquals(200, size.height)
        assertEquals(0, offset.x)
        assertEquals(100, offset.y)
    }

    @Test
    fun `a picture that already fits is not cropped at all`() {
        val (offset, size) = centreCrop(300, 300, Size(150f, 150f))
        assertEquals(0, offset.x)
        assertEquals(0, offset.y)
        assertEquals(300, size.width)
    }

    @Test
    fun `a mosaic tile crops to its own proportions, not to a square`() {
        val (_, size) = centreCrop(400, 400, Size(200f, 100f))
        assertEquals(2f, size.width.toFloat() / size.height.toFloat(), 0.01f)
    }

    @Test
    fun `a tap lands on the tile it is over, and on nothing in the gaps`() {
        val rects = rowTileRects(count = 4, columns = 4, widthPx = 1000f, gapPx = 10f)
        val side = rects[0].width

        assertEquals(0, tileAt(rects, 5f, 5f))
        assertEquals(1, tileAt(rects, side + 15f, 5f))
        assertEquals(3, tileAt(rects, 995f, 5f))
        // In the gap between two tiles, and below the row.
        assertEquals(-1, tileAt(rects, side + 5f, 5f))
        assertEquals(-1, tileAt(rects, 5f, side + 50f))
    }

    @Test
    fun `a row is as tall as its tallest tile`() {
        val rects = rowTileRects(3, 4, 1000f, 10f, aspects = listOf(2f, 1f, 0.5f))
        assertEquals(rects[0].height, rowHeight(rects), 0.01f)
    }
}
