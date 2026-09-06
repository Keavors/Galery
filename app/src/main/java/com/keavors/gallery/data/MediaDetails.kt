package com.keavors.gallery.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
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
    /** What is inside a video: the two codecs, how fast, how many bits. */
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val frameRate: String? = null,
    val bitrate: String? = null,
)

/**
 * Reads the metadata the photo carries inside itself.
 *
 * Everything is wrapped: exif data is written by hundreds of different cameras
 * and apps, plenty of it malformed, and a details sheet that crashed on an odd
 * file would be worse than one that shows a few rows fewer.
 */
suspend fun Context.readDetails(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
    if (item.isVideo) return@withContext videoDetails(item)

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

/**
 * What is actually inside a video file.
 *
 * Read from the tracks rather than from the container's own summary, because
 * the container lies by omission: an MKV is an MKV whether the video inside it
 * is H.264 that every phone plays or MPEG-4 ASP from 2005 that most do not, and
 * the same file may carry an AC3 soundtrack no Android decoder has ever heard
 * of. When something refuses to play, this is the sheet that says why — and
 * since the app now carries software decoders for exactly those cases, it is
 * also how anybody can tell which of them is doing the work.
 */
private fun Context.videoDetails(item: MediaItem): MediaDetails {
    val extractor = MediaExtractor()
    return runCatching {
        extractor.setDataSource(this, item.contentUri(), null)

        var video: MediaFormat? = null
        var audio: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            when {
                video == null && mime.startsWith("video/") -> video = format
                audio == null && mime.startsWith("audio/") -> audio = format
            }
        }

        MediaDetails(
            videoCodec = video?.getString(MediaFormat.KEY_MIME)?.let(::codecName),
            audioCodec = audio?.getString(MediaFormat.KEY_MIME)?.let(::codecName),
            frameRate = video?.numberOr(MediaFormat.KEY_FRAME_RATE)?.takeIf { it > 0 }
                ?.let { String.format(Locale.US, "%.0f", it) },
            // The video track's own rate where it has one, and the whole file's
            // where it does not: a container that only knows its total is still
            // telling the truth about it.
            bitrate = (video?.numberOr(MediaFormat.KEY_BIT_RATE)
                ?: fileBitrate(item))?.takeIf { it > 0 }
                ?.let { String.format(Locale.US, "%.1f", it / 1_000_000) },
        )
    }.getOrElse { MediaDetails() }.also { runCatching { extractor.release() } }
}

/** A track's number, whichever way the format happens to store it. */
private fun MediaFormat.numberOr(key: String): Float? = runCatching {
    when {
        !containsKey(key) -> null
        else -> runCatching { getFloat(key) }.getOrElse { getInteger(key).toFloat() }
    }
}.getOrNull()

/** Bits per second worked out from what is on the disk, when nothing else says. */
private fun fileBitrate(item: MediaItem): Float? {
    if (item.durationMs <= 0 || item.sizeBytes <= 0) return null
    return (item.sizeBytes * 8f) / (item.durationMs / 1000f)
}

/**
 * The name people use for a codec rather than the one Android does.
 *
 * "video/avc" is H.264 everywhere except in Android's own constants, and a
 * details sheet that says avc is a sheet that has to be translated before it
 * answers anything. Anything unrecognised keeps its own name — being told
 * "video/x-something" is still more use than being told nothing.
 */
private fun codecName(mime: String): String = CODEC_NAMES[mime.lowercase(Locale.US)]
    ?: mime.substringAfter('/').uppercase(Locale.US)

private val CODEC_NAMES = mapOf(
    "video/avc" to "H.264",
    "video/hevc" to "H.265 (HEVC)",
    "video/x-vnd.on2.vp8" to "VP8",
    "video/x-vnd.on2.vp9" to "VP9",
    "video/av01" to "AV1",
    "video/mp4v-es" to "MPEG-4",
    "video/mpeg2" to "MPEG-2",
    "video/3gpp" to "H.263",
    "audio/mp4a-latm" to "AAC",
    "audio/mpeg" to "MP3",
    "audio/ac3" to "AC-3 (Dolby Digital)",
    "audio/eac3" to "E-AC-3 (Dolby Digital Plus)",
    "audio/vnd.dts" to "DTS",
    "audio/vnd.dts.hd" to "DTS-HD",
    "audio/true-hd" to "Dolby TrueHD",
    "audio/opus" to "Opus",
    "audio/vorbis" to "Vorbis",
    "audio/flac" to "FLAC",
    "audio/raw" to "PCM",
    "audio/amr-nb" to "AMR-NB",
    "audio/amr-wb" to "AMR-WB",
)
