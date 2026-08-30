package com.keavors.gallery.data

import android.content.Context
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Everything the details sheet knows about one file. Nulls are simply omitted. */
data class MediaDetails(
    val camera: String? = null,
    val lens: String? = null,
    val iso: String? = null,
    val exposure: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val flash: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * Reads the metadata the photo carries inside itself.
 *
 * Everything is wrapped: exif data is written by hundreds of different cameras
 * and apps, plenty of it malformed, and a details sheet that crashed on an odd
 * file would be worse than one that shows a few rows fewer.
 */
suspend fun Context.readDetails(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
    if (item.isVideo) return@withContext MediaDetails()

    runCatching {
        // Without setRequireOriginal the system strips location out of the copy
        // it hands over, and the coordinates would silently always be missing.
        val uri = runCatching { MediaStore.setRequireOriginal(item.contentUri()) }
            .getOrElse { item.contentUri() }

        contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) return@use MediaDetails()
            val exif = ExifInterface(stream)
            val coordinates = runCatching { exif.latLong }.getOrNull()

            MediaDetails(
                camera = listOfNotNull(
                    exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.ifEmpty { null },
                    exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.ifEmpty { null },
                ).distinct().joinToString(" ").ifEmpty { null },
                lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifEmpty { null },
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
                exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    .takeIf { it > 0 }?.let(::formatExposure),
                aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                    .takeIf { it > 0 }?.let(::formatAperture),
                focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    .takeIf { it > 0 }?.let(::formatFocalLength),
                flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
                    .takeIf { it >= 0 }?.let { it and 1 == 1 },
                latitude = coordinates?.getOrNull(0),
                longitude = coordinates?.getOrNull(1),
            )
        }
    }.getOrElse { MediaDetails() }
}

/**
 * Shutter speed the way a camera shows it: a fraction below a second, a decimal
 * above. "0.004 s" is technically the same as "1/250" and useless to read.
 */
fun formatExposure(seconds: Double): String = when {
    seconds <= 0 -> ""
    seconds >= 1 -> String.format(Locale.US, "%.1f", seconds).removeSuffix(".0")
    else -> "1/${(1 / seconds).roundToInt()}"
}

fun formatAperture(fNumber: Double): String =
    "f/" + String.format(Locale.US, "%.1f", fNumber).removeSuffix(".0")

fun formatFocalLength(mm: Double): String =
    String.format(Locale.US, "%.1f", mm).removeSuffix(".0")

/** Megapixels, one decimal: "12.2" for a 4032x3024 photo. */
fun formatMegapixels(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return ""
    return String.format(Locale.US, "%.1f", width.toLong() * height / 1_000_000.0)
}

/** "4032 × 3024" with the multiplication sign, not the letter x. */
fun formatResolution(width: Int, height: Int): String =
    if (width <= 0 || height <= 0) "" else "$width × $height"
