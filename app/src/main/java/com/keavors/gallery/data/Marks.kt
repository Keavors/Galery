package com.keavors.gallery.data

import androidx.compose.ui.geometry.Rect

/**
 * A point on something drawn over a photograph, in fractions of that
 * photograph.
 *
 * Fractions for the same reason the crop uses them: what is drawn on a
 * screen-sized preview has to mean the same thing on the full-size picture
 * underneath, and a pen stroke recorded in screen pixels would come out a
 * twentieth of its size on a photograph twenty times larger.
 */
data class MarkPoint(val x: Float, val y: Float)

/**
 * Something drawn on top of a photograph.
 *
 * Marks are measured against the whole picture rather than against the part the
 * crop has left, so that changing the crop afterwards moves the picture under
 * them and leaves them where they were put — on the face, on the sign, on
 * whatever they were pointing at.
 */
sealed interface Mark {

    /**
     * A line drawn by a finger.
     *
     * [width] is a fraction of the picture's shorter side, so a brush that
     * looked right on the preview looks right on the original. An [erases]
     * stroke takes away what other strokes have put down, and nothing else:
     * the photograph itself is never drawn on, only the sheet over it.
     */
    data class Stroke(
        val points: List<MarkPoint>,
        val colour: Int,
        val width: Float,
        val erases: Boolean = false,
    ) : Mark

    /**
     * Words, or one big emoji, put down where a finger tapped.
     *
     * A sticker is not a separate kind of thing: an emoji is text, drawn by the
     * same code through the same font, and pretending otherwise would be two
     * implementations of one idea. [size] is the height of the writing as a
     * fraction of the picture's shorter side, so it survives the jump from the
     * preview to the original like everything else here.
     */
    data class Text(
        val text: String,
        val at: MarkPoint,
        val colour: Int,
        val size: Float,
        val font: MarkFont = MarkFont.PLAIN,
    ) : Mark

    /**
     * A rectangle of the picture, made unreadable.
     *
     * Kept as the two corners a finger dragged between rather than as a tidy
     * rectangle, because that is what lets the corner still under the finger go
     * on being the corner under the finger, whichever way the drag goes.
     *
     * Blurring and pixelating are the same operation with one difference, and
     * the difference is not in what is computed: both show the region at a
     * coarse resolution, and [pixelated] decides only whether it is stretched
     * back smoothly or in squares. Anything that hides a face hides it either
     * way; which one is a matter of taste.
     */
    data class Obscured(
        val from: MarkPoint,
        val to: MarkPoint,
        val pixelated: Boolean,
    ) : Mark {
        /** The two corners, put the right way round and kept inside the picture. */
        val bounds: CropRect
            get() = CropRect(from.x, from.y, to.x, to.y).sane()
    }
}

/** The three shapes of lettering on offer. Enough to choose from, few enough to choose. */
enum class MarkFont { PLAIN, SERIF, MONOSPACE }

/**
 * Where the whole picture sits, relative to the piece of it being shown.
 *
 * The one piece of arithmetic that keeps marks glued to the photograph while
 * the crop moves underneath them. Given the rectangle the cropped picture
 * occupies, this is the rectangle the uncropped one would occupy — usually
 * larger than what is on screen, and usually starting off the top left corner
 * of it, which is exactly right: the parts of a mark that fall outside the crop
 * are the parts that have been cropped away.
 */
fun uncroppedArea(crop: CropRect, shownWidth: Float, shownHeight: Float): Rect {
    val sane = crop.sane()
    if (sane.width <= 0f || sane.height <= 0f) return Rect(0f, 0f, shownWidth, shownHeight)

    val whole = shownWidth / sane.width
    val tall = shownHeight / sane.height
    val left = -sane.left * whole
    val top = -sane.top * tall
    return Rect(left, top, left + whole, top + tall)
}

/** Thickness in pixels, given the picture it is being drawn on. */
fun Mark.Stroke.widthOn(area: Rect): Float =
    width * minOf(area.width, area.height)

/** Letter height in pixels, given the picture it is being drawn on. */
fun Mark.Text.sizeOn(area: Rect): Float =
    size * minOf(area.width, area.height)

/**
 * The pixels of a picture that fall under a rectangle of the canvas.
 *
 * What lets a region be blurred at all: the marks are drawn in canvas
 * coordinates and the pixels to blur have to be fetched in the picture's own,
 * which are two different rectangles whenever the picture is not being shown at
 * full size — which is always, on a preview.
 */
fun sourcePixels(dst: Rect, occupies: Rect, imageWidth: Int, imageHeight: Int): PixelRect {
    if (occupies.width <= 0f || occupies.height <= 0f) return PixelRect(0, 0, 0, 0)

    val left = ((dst.left - occupies.left) / occupies.width * imageWidth)
        .toInt().coerceIn(0, (imageWidth - 1).coerceAtLeast(0))
    val top = ((dst.top - occupies.top) / occupies.height * imageHeight)
        .toInt().coerceIn(0, (imageHeight - 1).coerceAtLeast(0))
    val right = ((dst.right - occupies.left) / occupies.width * imageWidth)
        .toInt().coerceIn(left + 1, imageWidth)
    val bottom = ((dst.bottom - occupies.top) / occupies.height * imageHeight)
        .toInt().coerceIn(top + 1, imageHeight)

    return PixelRect(left, top, right - left, bottom - top)
}

/**
 * The same pixels, in far fewer of them: each block averaged down to one.
 *
 * This is the whole of both blur and pixelation. Drawn back smoothly it is a
 * blur, drawn back in squares it is a mosaic, and doing it this way rather than
 * with a proper convolution means the work is the size of the answer rather
 * than the size of the question — a region of any size costs the same.
 */
fun coarsen(pixels: IntArray, width: Int, height: Int, across: Int, down: Int): IntArray {
    val out = IntArray(across * down)
    for (row in 0 until down) {
        val fromY = row * height / down
        val toY = ((row + 1) * height / down).coerceAtLeast(fromY + 1)
        for (column in 0 until across) {
            val fromX = column * width / across
            val toX = ((column + 1) * width / across).coerceAtLeast(fromX + 1)

            var red = 0L
            var green = 0L
            var blue = 0L
            var counted = 0
            for (y in fromY until minOf(toY, height)) {
                for (x in fromX until minOf(toX, width)) {
                    val pixel = pixels[y * width + x]
                    red += (pixel shr 16 and 0xFF).toLong()
                    green += (pixel shr 8 and 0xFF).toLong()
                    blue += (pixel and 0xFF).toLong()
                    counted++
                }
            }
            out[row * across + column] = if (counted == 0) {
                0xFF000000.toInt()
            } else {
                (0xFF shl 24) or
                    ((red / counted).toInt() shl 16) or
                    ((green / counted).toInt() shl 8) or
                    (blue / counted).toInt()
            }
        }
    }
    return out
}

/** How many blocks a hidden region is reduced to across its wider side. */
const val OBSCURE_BLOCKS = 14
