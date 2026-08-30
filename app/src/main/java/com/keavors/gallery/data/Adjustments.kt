package com.keavors.gallery.data

import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.pow

/**
 * Where the light and colour sliders are set.
 *
 * Every one of them is neutral at zero and runs from -1 to 1, so "nothing has
 * been touched" is the same thing as "all zeroes" and needs no flag of its own.
 * What a value means in real units — stops, degrees of warmth, points of
 * brightness — is decided by the matrix below and nowhere else, which is what
 * lets the sliders be renamed or re-ranged without touching any arithmetic.
 */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val exposure: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val sharpness: Float = 0f,
    val vignette: Float = 0f,
) {
    /** True when nothing here would change a single pixel. */
    val isNeutral: Boolean
        get() = matrixIsNeutral && toneIsNeutral && vignette == 0f

    /** True when nothing here needs the picture drawn through a colour matrix. */
    val matrixIsNeutral: Boolean
        get() = brightness == 0f && contrast == 0f && exposure == 0f &&
            saturation == 0f && temperature == 0f && tint == 0f

    /** True when nothing here needs the pixels walked one at a time. */
    val toneIsNeutral: Boolean
        get() = shadows == 0f && highlights == 0f && sharpness == 0f

    companion object {
        val None = Adjustments()
    }
}

/**
 * The same settings, applied more gently or not at all.
 *
 * This is what the strength of a filter means: every value moved the same share
 * of the way back towards neutral. Doing it here rather than in the filters
 * keeps a filter a plain set of numbers with nothing clever about it.
 */
operator fun Adjustments.times(strength: Float): Adjustments = Adjustments(
    brightness = brightness * strength,
    contrast = contrast * strength,
    exposure = exposure * strength,
    shadows = shadows * strength,
    highlights = highlights * strength,
    saturation = saturation * strength,
    temperature = temperature * strength,
    tint = tint * strength,
    sharpness = sharpness * strength,
    vignette = vignette * strength,
)

/**
 * How a vignette is drawn, in one place for the two canvases that draw it.
 *
 * It is drawn rather than computed into the pixels, so it costs nothing while
 * the slider moves — and because a vignette belongs to the picture that is
 * being kept, it goes on after the crop, centred on what is left.
 */
object Vignette {

    /** Nothing at all happens inside this much of the way to the corner. */
    const val CLEAR_TO = 0.45f

    /** How dark the corner goes at the very end of the slider. */
    const val DEEPEST = 0.7f

    /** How opaque the corner is at this setting. */
    fun opacity(strength: Float): Float = kotlin.math.abs(strength) * DEEPEST

    /** Past the middle it darkens; short of it, it lightens instead. */
    fun darkens(strength: Float): Boolean = strength > 0f
}

/**
 * The one matrix that carries out all of them at once.
 *
 * A colour matrix is what makes the sliders instant on a photograph of any
 * size: the picture is never touched while they move, it is only drawn through
 * this, and drawing through a matrix is something the graphics chip does for
 * free. The same matrix is handed to the full-size save, so what is on screen
 * and what is written to disk cannot drift apart.
 *
 * The order is the order a darkroom would use and it is not interchangeable —
 * light first, then colour, then how far apart the tones are pulled, then how
 * light the whole thing sits, and saturation last so it acts on the result
 * rather than on the original.
 */
fun colorMatrixFor(adjustments: Adjustments): ColorMatrix {
    var matrix = ColorMatrix()

    if (adjustments.exposure != 0f) {
        // Stops, doubling and halving, because that is what exposure is.
        val factor = 2f.pow(adjustments.exposure * MAX_STOPS)
        matrix = matrix.then(scaling(factor, factor, factor))
    }

    if (adjustments.temperature != 0f || adjustments.tint != 0f) {
        // Warm adds red and takes blue; magenta takes green. Nothing here
        // pretends to be a real white balance — a white balance needs to know
        // what the light was, and a slider does not.
        matrix = matrix.then(
            scaling(
                red = 1f + adjustments.temperature * WARMTH + adjustments.tint * MAGENTA,
                green = 1f - adjustments.tint * MAGENTA,
                blue = 1f - adjustments.temperature * WARMTH + adjustments.tint * MAGENTA,
            )
        )
    }

    if (adjustments.contrast != 0f) {
        // Pulled apart around mid grey, so the middle of the picture stays where
        // it is and only the ends of the scale move.
        val spread = 1f + adjustments.contrast
        matrix = matrix
            .then(scaling(spread, spread, spread))
            .then(lifting(MID_GREY * (1f - spread)))
    }

    if (adjustments.brightness != 0f) {
        // Added, not multiplied: that is the difference between brightness and
        // exposure, and it is why both are worth having.
        matrix = matrix.then(lifting(adjustments.brightness * BRIGHTNESS_RANGE))
    }

    if (adjustments.saturation != 0f) {
        matrix = matrix.then(
            ColorMatrix().apply { setToSaturation(1f + adjustments.saturation) }
        )
    }

    return matrix
}

/** Two stops either way: past that a photograph is being rescued, not adjusted. */
private const val MAX_STOPS = 2f

/** A quarter of the red and blue channels at the ends of the warmth slider. */
private const val WARMTH = 0.25f

/** Green and magenta pull less than warmth: the eye is far quicker to see it. */
private const val MAGENTA = 0.15f

/** The tone contrast turns around, on the 0..255 scale a colour matrix works in. */
private const val MID_GREY = 128f

/** Points of brightness at the end of the slider, again on the 0..255 scale. */
private const val BRIGHTNESS_RANGE = 100f

/** Each channel on its own, for warmth and for anything that scales the light. */
private fun scaling(red: Float, green: Float, blue: Float) = ColorMatrix(
    floatArrayOf(
        red, 0f, 0f, 0f, 0f,
        0f, green, 0f, 0f, 0f,
        0f, 0f, blue, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
)

/** The same amount added to all three channels. */
private fun lifting(amount: Float) = ColorMatrix(
    floatArrayOf(
        1f, 0f, 0f, 0f, amount,
        0f, 1f, 0f, 0f, amount,
        0f, 0f, 1f, 0f, amount,
        0f, 0f, 0f, 1f, 0f,
    )
)

/**
 * This matrix and then that one, in the order they are read.
 *
 * The algebra writes it the other way round — the second one goes on the left —
 * and getting that backwards silently swaps the order of every correction, so
 * it is spelled out here once rather than remembered at each of the five places
 * above.
 */
internal fun ColorMatrix.then(next: ColorMatrix): ColorMatrix {
    val after = next.values
    val before = values
    val out = FloatArray(20)
    for (row in 0..3) {
        for (column in 0..3) {
            var sum = 0f
            for (k in 0..3) sum += after[row * 5 + k] * before[k * 5 + column]
            out[row * 5 + column] = sum
        }
        // The last column is a constant, so it collects the second matrix's own
        // constant on top of whatever the first one's constants turn into.
        var constant = after[row * 5 + 4]
        for (k in 0..3) constant += after[row * 5 + k] * before[k * 5 + 4]
        out[row * 5 + 4] = constant
    }
    return ColorMatrix(out)
}
