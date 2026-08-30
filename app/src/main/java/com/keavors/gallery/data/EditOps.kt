package com.keavors.gallery.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A rectangle inside a picture, as fractions of its width and height.
 *
 * Fractions rather than pixels because the editor works on a small copy and the
 * result is applied to the original: a crop chosen on a screen-sized preview has
 * to mean the same thing on a photo twenty times larger.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val isWhole: Boolean
        get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    /** Keeps the rectangle inside the picture and the right way round. */
    fun sane(): CropRect {
        val l = left.coerceIn(0f, 1f)
        val t = top.coerceIn(0f, 1f)
        val r = right.coerceIn(0f, 1f)
        val b = bottom.coerceIn(0f, 1f)
        return CropRect(
            left = min(l, r),
            top = min(t, b),
            right = max(l, r),
            bottom = max(t, b),
        )
    }

    /**
     * This crop as whole pixels of a picture that size.
     *
     * The rounding lives here and only here. The editor draws this rectangle
     * and saving cuts this rectangle, and a pixel of disagreement between the
     * two is a photograph that comes back different from the one that was
     * approved.
     */
    fun pixelsIn(width: Int, height: Int): PixelRect {
        if (width <= 0 || height <= 0) return PixelRect(0, 0, 0, 0)
        val x = (width * left).toInt().coerceIn(0, width - 1)
        val y = (height * top).toInt().coerceIn(0, height - 1)
        return PixelRect(
            x = x,
            y = y,
            width = (width * this.width).toInt().coerceIn(1, width - x),
            height = (height * this.height).toInt().coerceIn(1, height - y),
        )
    }

    companion object {
        val Whole = CropRect()

        /** Below this a crop is a mis-tap rather than an intention. */
        const val MIN_SIDE = 0.05f
    }
}

/** A rectangle of actual pixels, once a crop has been measured against a size. */
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * What the editor has been asked to do to a picture.
 *
 * A list of intentions rather than a modified bitmap. The editor shows them
 * applied to a small copy, which is what keeps it instant on a photo too large
 * to hold in memory; saving applies the same list to the original.
 */
data class EditOps(
    /** Right-angle turns, clockwise, 0 to 3. */
    val quarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    /** Fine rotation for a crooked horizon, in degrees. */
    val straighten: Float = 0f,
    val crop: CropRect = CropRect.Whole,
    val adjustments: Adjustments = Adjustments.None,
) {
    val isIdentity: Boolean
        get() = quarterTurns == 0 && !flipHorizontal && straighten == 0f &&
            crop.isWhole && adjustments.isNeutral

    /**
     * The turns and the tilt on their own.
     *
     * This is what the editor draws its preview from. The crop is a frame drawn
     * over that preview and the colours are applied while drawing it, so baking
     * either one in would be doing the same work twice — and in the crop's case
     * doing it to the very picture the frame is measured against.
     */
    val geometryOnly: EditOps
        get() = copy(crop = CropRect.Whole, adjustments = Adjustments.None)

    /** One more right-angle turn, wrapping rather than growing without bound. */
    fun turned(): EditOps = copy(
        quarterTurns = (quarterTurns + 1) % 4,
        // The crop was chosen in the old orientation; turning the picture has to
        // turn it too, or the frame jumps to a different part of the photo.
        crop = crop.turnedClockwise(),
    )

    fun flipped(): EditOps = copy(
        flipHorizontal = !flipHorizontal,
        crop = crop.mirroredHorizontally(),
    )

    fun straightened(degrees: Float): EditOps =
        copy(straighten = degrees.coerceIn(-MAX_STRAIGHTEN, MAX_STRAIGHTEN))

    fun cropped(rect: CropRect): EditOps = copy(crop = rect.sane())

    fun adjusted(values: Adjustments): EditOps = copy(adjustments = values)

    companion object {
        val None = EditOps()

        /** Beyond this a straighten is a rotation, and there is a button for that. */
        const val MAX_STRAIGHTEN = 15f
    }
}

/** The same region after the picture is turned a quarter clockwise. */
fun CropRect.turnedClockwise(): CropRect = CropRect(
    left = 1f - bottom,
    top = left,
    right = 1f - top,
    bottom = right,
).sane()

/** The same region after the picture is mirrored left to right. */
fun CropRect.mirroredHorizontally(): CropRect = CropRect(
    left = 1f - right,
    top = top,
    right = 1f - left,
    bottom = bottom,
).sane()

/**
 * The size a picture ends up after the right-angle turns and the crop.
 *
 * The straighten angle is deliberately not counted: rotating by a few degrees
 * and then trimming to the largest upright rectangle inside the result is done
 * when the pixels are, and the answer depends on the interpolation.
 */
fun outputSize(width: Int, height: Int, ops: EditOps): Pair<Int, Int> {
    val turned = ops.quarterTurns % 2 == 1
    val w = if (turned) height else width
    val h = if (turned) width else height
    val cropped = ops.crop.sane()
    return max(1, (w * cropped.width).toInt()) to max(1, (h * cropped.height).toInt())
}

/**
 * How many pixels an edit may hold in memory at once.
 *
 * Editing a two-hundred-megapixel photo at full size would need most of a
 * gigabyte for one bitmap, so the ceiling is taken from the heap this device
 * actually has rather than picked as a number. A quarter of it, at four bytes a
 * pixel, leaves room for the copy being drawn into.
 */
fun maxEditablePixels(maxHeapBytes: Long): Int {
    val budget = maxHeapBytes / 4
    val pixels = budget / BYTES_PER_PIXEL
    return pixels.coerceIn(MIN_EDIT_PIXELS, MAX_EDIT_PIXELS).toInt()
}

private const val BYTES_PER_PIXEL = 4L

/** Two megapixels: small, but still a picture rather than a thumbnail. */
private const val MIN_EDIT_PIXELS = 2_000_000L

/** Past this the extra detail is beyond what any screen or print will show. */
private const val MAX_EDIT_PIXELS = 40_000_000L

/** Whether a photo has to be shrunk before it can be edited on this device. */
fun needsDownscale(width: Int, height: Int, maxPixels: Int): Boolean =
    width.toLong() * height > maxPixels

/** The sample step that brings a picture under the ceiling, as a power of two. */
fun sampleSizeFor(width: Int, height: Int, maxPixels: Int): Int {
    var sample = 1
    while ((width / sample).toLong() * (height / sample) > maxPixels && sample < 32) {
        sample *= 2
    }
    return sample
}

/** True when the straighten slider is close enough to zero to mean nothing. */
fun isStraightNeutral(degrees: Float): Boolean = abs(degrees) < 0.05f
