package com.keavors.gallery.data

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * The corrections a colour matrix cannot do.
 *
 * A colour matrix is the same arithmetic for every pixel in the picture. These
 * three are not: shadows and highlights depend on how dark the pixel already
 * is, and sharpness depends on how different it is from the ones around it. So
 * they are done the slow way, one pixel at a time — but by this code and no
 * other, for the preview and for the save alike. Two versions of a curve is two
 * photographs.
 */

/** How far the darkest and lightest parts can be pushed, on the 0..1 scale. */
private const val TONE_LIFT = 0.35f

/** How hard the sharpest end of the slider pulls an edge apart. */
private const val MAX_SHARPEN = 1.5f

/**
 * One channel after the shadow and highlight sliders.
 *
 * The two masks are the whole idea: squared, so that lifting the shadows leaves
 * a white sky alone and pulling the highlights back leaves a black shadow
 * alone. Without them both sliders would be brightness with extra steps.
 *
 * [value] and [luma] are 0..1, and so is what comes back.
 */
internal fun tonedChannel(value: Float, luma: Float, shadows: Float, highlights: Float): Float {
    val dark = (1f - luma) * (1f - luma)
    val light = luma * luma
    return (value + shadows * dark * TONE_LIFT + highlights * light * TONE_LIFT)
        .coerceIn(0f, 1f)
}

/**
 * Shadows, highlights and sharpness, applied to a block of pixels in place.
 *
 * Pixels rather than a bitmap so that this can be tested: the arithmetic is the
 * part worth being sure about, and a Bitmap cannot be made outside a running
 * phone. [pixels] is ARGB, row by row, as [Bitmap.getPixels] hands it over.
 */
fun tonePixels(pixels: IntArray, width: Int, height: Int, adjustments: Adjustments) {
    if (adjustments.shadows != 0f || adjustments.highlights != 0f) {
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val red = (pixel shr 16 and 0xFF) / 255f
            val green = (pixel shr 8 and 0xFF) / 255f
            val blue = (pixel and 0xFF) / 255f

            // The eye's own weighting, so that a shadow is what looks like one
            // rather than what happens to be low in all three channels.
            val luma = 0.299f * red + 0.587f * green + 0.114f * blue

            pixels[i] = (pixel.toLong() and 0xFF000000L).toInt() or
                (byteOf(tonedChannel(red, luma, adjustments.shadows, adjustments.highlights)) shl 16) or
                (byteOf(tonedChannel(green, luma, adjustments.shadows, adjustments.highlights)) shl 8) or
                byteOf(tonedChannel(blue, luma, adjustments.shadows, adjustments.highlights))
        }
    }

    if (adjustments.sharpness != 0f) sharpen(pixels, width, height, adjustments.sharpness)
}

/** A copy of the picture with those same three applied. */
fun Bitmap.tonedCopy(adjustments: Adjustments): Bitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    tonePixels(pixels, width, height, adjustments)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

/**
 * An unsharp mask: the picture plus what it has that a blurred copy of it does
 * not, which is its edges.
 *
 * The slider runs both ways. Pushed the other side of neutral the same
 * arithmetic mixes the blurred copy in instead, which is a softening — worth
 * having, and free.
 */
private fun sharpen(pixels: IntArray, width: Int, height: Int, sharpness: Float) {
    if (width < 3 || height < 3) return
    val strength = sharpness * MAX_SHARPEN
    val blurred = boxBlurred(pixels, width, height)

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val soft = blurred[i]
        pixels[i] = (pixel.toLong() and 0xFF000000L).toInt() or
            (edged(pixel shr 16 and 0xFF, soft shr 16 and 0xFF, strength) shl 16) or
            (edged(pixel shr 8 and 0xFF, soft shr 8 and 0xFF, strength) shl 8) or
            edged(pixel and 0xFF, soft and 0xFF, strength)
    }
}

private fun edged(value: Int, blurred: Int, strength: Float): Int =
    (value + strength * (value - blurred)).roundToInt().coerceIn(0, 255)

/**
 * A three-wide blur, done across and then down.
 *
 * Separated into two passes because the answer is the same and the work is six
 * reads a pixel instead of nine — on a forty-megapixel photograph that is the
 * difference between a wait and a longer one.
 */
private fun boxBlurred(pixels: IntArray, width: Int, height: Int): IntArray {
    val across = IntArray(pixels.size)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            across[row + x] = averageOf(
                pixels[row + (x - 1).coerceAtLeast(0)],
                pixels[row + x],
                pixels[row + (x + 1).coerceAtMost(width - 1)],
            )
        }
    }

    val down = IntArray(pixels.size)
    for (y in 0 until height) {
        val above = (y - 1).coerceAtLeast(0) * width
        val row = y * width
        val below = (y + 1).coerceAtMost(height - 1) * width
        for (x in 0 until width) {
            down[row + x] = averageOf(across[above + x], across[row + x], across[below + x])
        }
    }
    return down
}

private fun averageOf(a: Int, b: Int, c: Int): Int {
    val red = ((a shr 16 and 0xFF) + (b shr 16 and 0xFF) + (c shr 16 and 0xFF)) / 3
    val green = ((a shr 8 and 0xFF) + (b shr 8 and 0xFF) + (c shr 8 and 0xFF)) / 3
    val blue = ((a and 0xFF) + (b and 0xFF) + (c and 0xFF)) / 3
    return (red shl 16) or (green shl 8) or blue
}

private fun byteOf(value: Float): Int = (value * 255f).roundToInt().coerceIn(0, 255)
