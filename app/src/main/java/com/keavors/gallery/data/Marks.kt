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
