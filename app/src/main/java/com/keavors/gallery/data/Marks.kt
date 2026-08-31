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
        val angle: Float = 0f,
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
        val angle: Float = 0f,
    ) : Mark {
        /** The two corners, put the right way round and kept inside the picture. */
        val bounds: CropRect
            get() = CropRect(from.x, from.y, to.x, to.y).sane()
    }
}

/**
 * The three shapes of lettering on offer, in the order they are offered.
 *
 * Named here after the typeface each one is, and named on screen after what it
 * looks like once it is on a photograph, which are not the same words: the
 * first is what most captions want, and the last is the heavy one.
 */
enum class MarkFont { MONOSPACE, SERIF, PLAIN }

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

/**
 * Changing a mark that has already been put down.
 *
 * A mark is not an imprint. Once something is on the picture it stays a thing —
 * it can be picked up, moved, made bigger, turned and thrown away — and all of
 * that is done by rewriting the mark's own numbers rather than by wrapping it in
 * a transform. The wrapper would be easier and would leave two truths about
 * where the mark is; this way there is one.
 *
 * Everything below works in fractions of the picture, so a mark dragged across
 * a preview lands in the same place on the original.
 */

/** The middle of a mark, which is what it is turned and grown around. */
val Mark.centre: MarkPoint
    get() = when (this) {
        is Mark.Text -> at
        is Mark.Obscured -> MarkPoint((from.x + to.x) / 2f, (from.y + to.y) / 2f)
        is Mark.Stroke -> {
            if (points.isEmpty()) {
                MarkPoint(0.5f, 0.5f)
            } else {
                MarkPoint(points.sumOf { it.x.toDouble() }.toFloat() / points.size,
                    points.sumOf { it.y.toDouble() }.toFloat() / points.size)
            }
        }
    }

/** The same mark, shifted. */
fun Mark.movedBy(dx: Float, dy: Float): Mark = when (this) {
    is Mark.Text -> copy(at = at.shifted(dx, dy))
    is Mark.Obscured -> copy(from = from.shifted(dx, dy), to = to.shifted(dx, dy))
    is Mark.Stroke -> copy(points = points.map { it.shifted(dx, dy) })
}

/** The same mark, larger or smaller about its own middle. */
fun Mark.scaledBy(factor: Float): Mark {
    if (factor <= 0f) return this
    val middle = centre
    return when (this) {
        is Mark.Text -> copy(size = (size * factor).coerceIn(MIN_MARK_SIZE, MAX_MARK_SIZE))
        is Mark.Obscured -> copy(
            from = from.grownFrom(middle, factor),
            to = to.grownFrom(middle, factor),
        )
        is Mark.Stroke -> copy(
            points = points.map { it.grownFrom(middle, factor) },
            width = (width * factor).coerceIn(MIN_MARK_SIZE, MAX_MARK_SIZE),
        )
    }
}

/**
 * The same mark, turned about its own middle.
 *
 * Writing and hidden regions carry an angle and are turned when they are drawn.
 * A line has no angle to carry — it is its points — so its points are turned
 * instead, which is why the shape of the picture has to be known: a quarter turn
 * in fractions of a rectangle is not a quarter turn on the rectangle.
 */
fun Mark.turnedBy(degrees: Float, aspect: Float): Mark = when (this) {
    is Mark.Text -> copy(angle = angle + degrees)
    is Mark.Obscured -> copy(angle = angle + degrees)
    is Mark.Stroke -> {
        val middle = centre
        copy(points = points.map { it.turnedAround(middle, degrees, aspect) })
    }
}

/** The smallest and largest anything drawn may be, as fractions of the shorter side. */
private const val MIN_MARK_SIZE = 0.002f
private const val MAX_MARK_SIZE = 1.5f

private fun MarkPoint.shifted(dx: Float, dy: Float) = MarkPoint(x + dx, y + dy)

private fun MarkPoint.grownFrom(middle: MarkPoint, factor: Float) = MarkPoint(
    x = middle.x + (x - middle.x) * factor,
    y = middle.y + (y - middle.y) * factor,
)

private fun MarkPoint.turnedAround(
    middle: MarkPoint,
    degrees: Float,
    aspect: Float,
): MarkPoint {
    val radians = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(radians).toFloat()
    val sin = kotlin.math.sin(radians).toFloat()
    // Into a space where the picture is square, turned there, and back out
    // again — otherwise a circle drawn on a wide photograph would turn into an
    // ellipse.
    val dx = (x - middle.x) * aspect
    val dy = y - middle.y
    return MarkPoint(
        x = middle.x + (dx * cos - dy * sin) / aspect,
        y = middle.y + (dx * sin + dy * cos),
    )
}

/**
 * Roughly how much of the picture a mark takes up.
 *
 * Rough on purpose for writing: measuring text exactly needs a font, a density
 * and a text engine, and this is used to decide whether a finger landed on
 * something and where to draw a box around it. Both survive being a few percent
 * out; neither is worth building a text engine for.
 */
fun Mark.extent(aspect: Float): CropRect = when (this) {
    is Mark.Text -> {
        val lines = text.split('\n')
        val wide = size * LETTER_WIDTH * (lines.maxOfOrNull { it.length } ?: 1).coerceAtLeast(1)
        val high = size * LINE_HEIGHT * lines.size
        CropRect(
            left = at.x - xFraction(wide, aspect) / 2f,
            top = at.y - yFraction(high, aspect) / 2f,
            right = at.x + xFraction(wide, aspect) / 2f,
            bottom = at.y + yFraction(high, aspect) / 2f,
        )
    }

    is Mark.Obscured -> bounds

    is Mark.Stroke -> {
        if (points.isEmpty()) {
            CropRect(0f, 0f, 0f, 0f)
        } else {
            val half = width / 2f
            CropRect(
                left = points.minOf { it.x } - xFraction(half, aspect),
                top = points.minOf { it.y } - yFraction(half, aspect),
                right = points.maxOf { it.x } + xFraction(half, aspect),
                bottom = points.maxOf { it.y } + yFraction(half, aspect),
            )
        }
    }
}

/**
 * Whether a finger landed on this mark.
 *
 * Generous by a fixed margin, because a thin line is a hard thing to hit and a
 * mark nobody can pick up again might as well be paint.
 */
fun Mark.covers(point: MarkPoint, aspect: Float): Boolean {
    val around = extent(aspect)
    val slack = TAP_SLACK
    return point.x >= around.left - xFraction(slack, aspect) &&
        point.x <= around.right + xFraction(slack, aspect) &&
        point.y >= around.top - yFraction(slack, aspect) &&
        point.y <= around.bottom + yFraction(slack, aspect)
}

/** How much wider than itself a mark is to a fingertip. */
private const val TAP_SLACK = 0.02f

/** How wide a letter is compared to how tall, near enough. */
private const val LETTER_WIDTH = 0.55f

/** How much room a line of writing takes compared to its letter height. */
private const val LINE_HEIGHT = 1.2f

/**
 * A length measured against the shorter side, as a fraction of the width.
 *
 * Sizes here are fractions of the shorter side so that they mean the same thing
 * whichever way up the photograph is; positions are fractions of each side. Any
 * arithmetic mixing the two has to go through these.
 */
private fun xFraction(length: Float, aspect: Float): Float =
    if (aspect >= 1f) length / aspect else length

/** The same, as a fraction of the height. */
private fun yFraction(length: Float, aspect: Float): Float =
    if (aspect >= 1f) length else length * aspect
