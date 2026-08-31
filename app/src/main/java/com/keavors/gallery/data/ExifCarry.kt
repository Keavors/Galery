package com.keavors.gallery.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "ExifCarry"

/**
 * Carrying a photograph's own account of itself into the edited version.
 *
 * Compressing a bitmap writes pixels and nothing else, so an edited photo comes
 * out with no date, no camera, no lens and no place — a picture that has
 * forgotten where it was taken. Everything that is still true after an edit is
 * read off the original first and written back on afterwards.
 *
 * Read first is the part that matters: overwriting destroys the original, and
 * by then there is nothing left to read.
 */

/**
 * What survives an edit.
 *
 * Deliberately not everything. The orientation is left out because the turns
 * have been baked into the pixels and a photograph that then claims to need
 * turning again would arrive sideways. The dimensions are left out because they
 * are written from the new bitmap. The camera's own contrast, saturation and
 * sharpness settings are left out because this editor has just changed all
 * three, and a file that still claims otherwise is worse than one that says
 * nothing.
 */
private val CARRIED = listOf(
    // When
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,

    // What with
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_LENS_MAKE,
    ExifInterface.TAG_LENS_MODEL,
    ExifInterface.TAG_LENS_SPECIFICATION,
    ExifInterface.TAG_BODY_SERIAL_NUMBER,

    // How
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_APERTURE_VALUE,
    ExifInterface.TAG_MAX_APERTURE_VALUE,
    ExifInterface.TAG_SHUTTER_SPEED_VALUE,
    ExifInterface.TAG_BRIGHTNESS_VALUE,
    ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
    ExifInterface.TAG_EXPOSURE_PROGRAM,
    ExifInterface.TAG_EXPOSURE_MODE,
    // Not its deprecated older name as well: that is the same tag under two
    // spellings, and writing both writes it twice.
    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_ISO_SPEED,
    ExifInterface.TAG_METERING_MODE,
    ExifInterface.TAG_LIGHT_SOURCE,
    ExifInterface.TAG_WHITE_BALANCE,
    ExifInterface.TAG_FLASH,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
    ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
    ExifInterface.TAG_SUBJECT_DISTANCE,
    ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
    ExifInterface.TAG_SCENE_CAPTURE_TYPE,
    ExifInterface.TAG_SENSING_METHOD,
    ExifInterface.TAG_COLOR_SPACE,

    // Where
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_PROCESSING_METHOD,
    ExifInterface.TAG_GPS_SPEED,
    ExifInterface.TAG_GPS_SPEED_REF,
    ExifInterface.TAG_GPS_IMG_DIRECTION,
    ExifInterface.TAG_GPS_IMG_DIRECTION_REF,

    // Whose
    ExifInterface.TAG_ARTIST,
    ExifInterface.TAG_COPYRIGHT,
    ExifInterface.TAG_IMAGE_DESCRIPTION,
    ExifInterface.TAG_USER_COMMENT,
    ExifInterface.TAG_SOFTWARE,
)

/**
 * Everything worth keeping, read off the original.
 *
 * Empty is a perfectly good answer: a screenshot has no camera and a PNG has no
 * EXIF at all.
 */
suspend fun Context.carriedExif(item: MediaItem, keepWhen: Boolean): Map<String, String> =
    withContext(Dispatchers.IO) {
        val found = readExif(item)
        when {
            // A copy is a new photograph, made now, and says so: every tag
            // about when goes. The camera, the lens and the place still travel
            // — they are as true of the copy as of the original — but the day
            // is not, and a copy claiming its original's day would sort back
            // into last summer instead of appearing where it was just made.
            !keepWhen -> found - WHEN_TAGS

            // A replacement is the same photograph and keeps its day. One that
            // never had a day inside it — a screenshot, anything a messenger
            // stripped on the way — is given the day the library knew, because
            // after this the file is where the day will be read from:
            // rewriting the pixels makes the library look again, and what it
            // finds there wins.
            found.keys.none { it in DATE_TAGS } && item.takenAt > 0 -> {
                val stamp = EXIF_DATE.format(Date(item.takenAt))
                found + mapOf(
                    ExifInterface.TAG_DATETIME_ORIGINAL to stamp,
                    ExifInterface.TAG_DATETIME to stamp,
                    // Stamped as UTC and saying so, so the moment any reader
                    // computes back is exactly the moment that went in,
                    // whatever timezone either end is in.
                    ExifInterface.TAG_OFFSET_TIME_ORIGINAL to "+00:00",
                    ExifInterface.TAG_OFFSET_TIME to "+00:00",
                )
            }

            else -> found
        }
    }

/** The tags that say when, any one of which is enough to keep a photograph in place. */
private val DATE_TAGS = setOf(
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_DIGITIZED,
)

/** Every tag with a moment in it, for the saves that must not carry one. */
private val WHEN_TAGS = DATE_TAGS + setOf(
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
)

/** The one shape EXIF writes a moment in, and it is not anybody else's. */
private val EXIF_DATE = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
    // In step with the offset tags written beside it.
    timeZone = TimeZone.getTimeZone("UTC")
}

private suspend fun Context.readExif(item: MediaItem): Map<String, String> =
    withContext(Dispatchers.IO) {
        // Without setRequireOriginal the system hands over a copy with the
        // location stripped out, and the place a photograph was taken is the one
        // piece of this that cannot be worked out again later.
        val uri = runCatching { MediaStore.setRequireOriginal(item.contentUri()) }
            .getOrElse { item.contentUri() }

        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                CARRIED.mapNotNull { tag ->
                    exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { tag to it }
                }.toMap()
            }.orEmpty()
        }.onFailure { Log.w(TAG, "could not read the original's metadata", it) }
            .getOrElse { emptyMap() }
    }

/**
 * Writes them onto the picture that has just been saved, along with the truth
 * about its new shape.
 *
 * Returns false when the metadata could not be attached — the photograph itself
 * is already written and is fine, but it has lost its memory, and the person who
 * saved it is the only one who can decide whether that matters.
 */
suspend fun Context.writeCarriedExif(
    uri: Uri,
    tags: Map<String, String>,
    width: Int,
    height: Int,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        contentResolver.openFileDescriptor(uri, "rw")?.use { file ->
            ExifInterface(file.fileDescriptor).carry(tags, width, height)
            true
        } == true
    }.onFailure { Log.w(TAG, "could not write the metadata back", it) }.getOrElse { false }
}

/**
 * The same, onto a file sitting on the disk.
 *
 * Used where the finished photograph is assembled before it is put anywhere:
 * an ordinary file is the simplest thing ExifInterface can be handed, and the
 * result is a complete picture — pixels and history together — from the first
 * moment anybody else can see it.
 */
suspend fun writeCarriedExif(
    file: File,
    tags: Map<String, String>,
    width: Int,
    height: Int,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ExifInterface(file.absolutePath).carry(tags, width, height)
        true
    }.onFailure { Log.w(TAG, "could not write the metadata onto the file", it) }
        .getOrElse { false }
}

/** What is actually written, wherever the file came from. */
private fun ExifInterface.carry(tags: Map<String, String>, width: Int, height: Int) {
    tags.forEach { (tag, value) -> setAttribute(tag, value) }

    // The turns are in the pixels now. A file that still asks to be turned
    // would be turned twice by whatever opens it next.
    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
    setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width.toString())
    setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height.toString())
    setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
    setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())

    saveAttributes()
}
