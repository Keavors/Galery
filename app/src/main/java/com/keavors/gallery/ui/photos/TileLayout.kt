package com.keavors.gallery.ui.photos

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Where each photograph of a row goes, in pixels within the row.
 *
 * Worked out here, as arithmetic, rather than by handing every tile to the
 * layout system: a row at twenty-five columns is twenty-five tiles, and at that
 * width the whole screen holds six hundred of them. Measuring, placing and
 * drawing six hundred separate elements is what made the grid crawl; six hundred
 * rectangles on one canvas is what it costs instead.
 *
 * @param aspects each photograph's width against its height, for a mosaic, or
 *   null for a square grid where every tile is the same.
 */
fun rowTileRects(
    count: Int,
    columns: Int,
    widthPx: Float,
    gapPx: Float,
    aspects: List<Float>? = null,
): List<Rect> {
    if (count <= 0 || widthPx <= 0f) return emptyList()

    if (aspects == null) {
        // A short last row keeps the tile size of every other row rather than
        // stretching to fill the width, so the grid stays a grid.
        val side = ((widthPx - gapPx * (columns - 1)) / columns).coerceAtLeast(1f)
        return List(count) { index ->
            val left = index * (side + gapPx)
            Rect(left, 0f, left + side, side)
        }
    }

    val total = aspects.take(count).sum().coerceAtLeast(0.01f)
    val height = ((widthPx - gapPx * (count - 1)) / total).coerceAtLeast(1f)
    var left = 0f
    return List(count) { index ->
        val width = height * aspects[index]
        val rect = Rect(left, 0f, left + width, height)
        left += width + gapPx
        rect
    }
}

/** How tall a row of these rectangles is. */
fun rowHeight(rects: List<Rect>): Float = rects.maxOfOrNull { it.height } ?: 0f

/**
 * The part of a bitmap that fills a tile without stretching it.
 *
 * The middle of the picture, cropped to the tile's proportions — the same thing
 * ContentScale.Crop does, done by hand because the drawing is done by hand.
 * Returns the source rectangle; the destination is the tile itself.
 */
fun centreCrop(bitmapWidth: Int, bitmapHeight: Int, tile: Size): Pair<IntOffset, IntSize> {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || tile.width <= 0f || tile.height <= 0f) {
        return IntOffset.Zero to IntSize(bitmapWidth.coerceAtLeast(1), bitmapHeight.coerceAtLeast(1))
    }

    val wanted = tile.width / tile.height
    val have = bitmapWidth.toFloat() / bitmapHeight.toFloat()

    return if (have > wanted) {
        // Wider than the tile: keep the full height and take the middle across.
        val width = (bitmapHeight * wanted).toInt().coerceIn(1, bitmapWidth)
        IntOffset((bitmapWidth - width) / 2, 0) to IntSize(width, bitmapHeight)
    } else {
        val height = (bitmapWidth / wanted).toInt().coerceIn(1, bitmapHeight)
        IntOffset(0, (bitmapHeight - height) / 2) to IntSize(bitmapWidth, height)
    }
}

/** Which tile of a row a finger at [x] is on, or -1 if it is on a gap or past the end. */
fun tileAt(rects: List<Rect>, x: Float, y: Float): Int =
    rects.indexOfFirst { it.contains(Offset(x, y)) }
