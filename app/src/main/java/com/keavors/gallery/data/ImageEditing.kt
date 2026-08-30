package com.keavors.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "ImageEditing"

/**
 * How much a picture must shrink to stay inside its own frame when tilted.
 *
 * Straightening a crooked horizon rotates the photograph, which leaves the
 * corners empty. Rather than show them, the largest upright rectangle of the
 * same shape is taken from the middle — this is how much of the original that
 * rectangle covers.
 */
fun straightenScale(width: Int, height: Int, degrees: Float): Float {
    if (width <= 0 || height <= 0 || isStraightNeutral(degrees)) return 1f
    val a = Math.toRadians(kotlin.math.abs(degrees).toDouble())
    val c = cos(a)
    val s = sin(a)
    val w = width.toDouble()
    val h = height.toDouble()
    val byWidth = w / (w * c + h * s)
    val byHeight = h / (w * s + h * c)
    return min(byWidth, byHeight).toFloat().coerceIn(0.1f, 1f)
}

/**
 * Reads a picture at a size this device can actually work with.
 *
 * ImageDecoder is used rather than BitmapFactory because it applies the
 * orientation stored in the file: a photo taken sideways has to arrive upright,
 * or every rotation the editor offers would be measured from the wrong place.
 */
suspend fun Context.decodeForEditing(item: MediaItem, maxPixels: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(contentResolver, item.contentUri())
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Software allocation: the result is drawn into by a Canvas, and
                // a hardware bitmap cannot be read back.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val sample = sampleSizeFor(info.size.width, info.size.height, maxPixels)
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }
        }.onFailure { Log.w(TAG, "could not decode for editing", it) }.getOrNull()
    }

/**
 * Draws a picture with the edits applied.
 *
 * Turns and mirroring first, then the tilt, then the crop, then the light and
 * colour — the same order the editor shows them in, which is what lets a crop
 * chosen on screen mean the same thing here.
 */
fun applyOps(source: Bitmap, ops: EditOps): Bitmap {
    var current = source

    if (ops.quarterTurns != 0 || ops.flipHorizontal) {
        val matrix = Matrix().apply {
            if (ops.flipHorizontal) postScale(-1f, 1f)
            if (ops.quarterTurns != 0) postRotate(ops.quarterTurns * 90f)
        }
        val turned = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        if (turned !== current && current !== source) current.recycle()
        current = turned
    }

    if (!isStraightNeutral(ops.straighten)) {
        val scale = straightenScale(current.width, current.height, ops.straighten)
        val outWidth = (current.width * scale).toInt().coerceAtLeast(1)
        val outHeight = (current.height * scale).toInt().coerceAtLeast(1)
        val tilted = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(tilted).apply {
            translate(outWidth / 2f, outHeight / 2f)
            rotate(-ops.straighten)
            drawBitmap(
                current,
                -current.width / 2f,
                -current.height / 2f,
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
        }
        if (current !== source) current.recycle()
        current = tilted
    }

    val crop = ops.crop.sane()
    if (!crop.isWhole) {
        val cut = crop.pixelsIn(current.width, current.height)
        val cropped = Bitmap.createBitmap(current, cut.x, cut.y, cut.width, cut.height)
        if (cropped !== current && current !== source) current.recycle()
        current = cropped
    }

    // Shadows, highlights and sharpness first, because they are what the
    // preview showed first: these are all arithmetic on what the pixel already
    // is, and doing them either side of the matrix gives different pictures.
    if (!ops.adjustments.toneIsNeutral) {
        val toned = current.tonedCopy(ops.adjustments)
        if (current !== source) current.recycle()
        current = toned
    }

    if (!ops.adjustments.matrixIsNeutral || ops.adjustments.vignette != 0f) {
        // One pass for both: the matrix the sliders were drawn through, applied
        // by the graphics chip rather than by a hundred megapixels of Kotlin
        // arithmetic, and the vignette laid over the result.
        val corrected = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(corrected)
        canvas.drawBitmap(
            current,
            0f,
            0f,
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                if (!ops.adjustments.matrixIsNeutral) {
                    colorFilter = ColorMatrixColorFilter(colorMatrixFor(ops.adjustments).values)
                }
            },
        )
        if (ops.adjustments.vignette != 0f) {
            canvas.drawVignette(corrected.width, corrected.height, ops.adjustments.vignette)
        }
        if (current !== source) current.recycle()
        current = corrected
    }

    return current
}

/** The same vignette the editor draws, on the picture that is being written. */
private fun Canvas.drawVignette(width: Int, height: Int, strength: Float) {
    val corner = if (Vignette.darkens(strength)) Color.BLACK else Color.WHITE
    val alpha = (Vignette.opacity(strength) * 255f).toInt().coerceIn(0, 255)
    val paint = Paint().apply {
        shader = RadialGradient(
            width / 2f,
            height / 2f,
            hypot(width.toFloat(), height.toFloat()) / 2f,
            intArrayOf(corner and 0x00FFFFFF, (alpha shl 24) or (corner and 0x00FFFFFF)),
            floatArrayOf(Vignette.CLEAR_TO, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
}

/**
 * How a save went.
 *
 * Three answers rather than two, because "the photograph is there but it has
 * forgotten when and where it was taken" is neither a success worth saying
 * nothing about nor a failure worth throwing the edit away over.
 */
enum class SaveOutcome { SAVED, SAVED_WITHOUT_METADATA, FAILED }

/**
 * A write request for an existing file.
 *
 * Overwriting somebody else's photo needs the system's blessing exactly as
 * deleting does, and with media management granted it is given without asking.
 */
fun writeRequestFor(context: Context, item: MediaItem): IntentSenderRequest =
    MediaStore.createWriteRequest(
        context.contentResolver,
        listOf(item.contentUri()),
    ).let { IntentSenderRequest.Builder(it.intentSender).build() }

/**
 * Writes the edited picture over the original.
 *
 * The caller must already hold permission for it — see [writeRequestFor].
 */
suspend fun Context.overwriteWith(
    item: MediaItem,
    bitmap: Bitmap,
    quality: Int,
    exif: Map<String, String>,
): SaveOutcome = withContext(Dispatchers.IO) {
    val written = runCatching {
        contentResolver.openOutputStream(item.contentUri(), "wt")?.use { out ->
            bitmap.compress(formatFor(item.mimeType), quality, out)
        } ?: false
    }.onFailure { Log.w(TAG, "overwrite failed", it) }.getOrElse { false }

    if (!written) {
        SaveOutcome.FAILED
    } else if (writeCarriedExif(item.contentUri(), exif, bitmap.width, bitmap.height)) {
        SaveOutcome.SAVED
    } else {
        SaveOutcome.SAVED_WITHOUT_METADATA
    }
}

/**
 * Writes the edited picture alongside the original.
 *
 * Pending first, published after, so no half-written photo ever appears in
 * anybody's gallery.
 */
suspend fun Context.saveEditedCopy(
    item: MediaItem,
    bitmap: Bitmap,
    quality: Int,
    exif: Map<String, String>,
): SaveOutcome =
    withContext(Dispatchers.IO) {
        val format = formatFor(item.mimeType)
        val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
        val stem = item.name.substringBeforeLast('.', item.name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, stem + "_edit." + extension)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg",
            )
            val path = item.relativePath.trim().trim('/')
            if (path.isNotEmpty() && !item.relativePath.startsWith("/")) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$path/")
            }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { contentResolver.insert(collection, values) }
            .onFailure { Log.w(TAG, "could not create a copy", it) }
            .getOrNull() ?: return@withContext SaveOutcome.FAILED

        val written = runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(format, quality, out)
            } ?: false
        }.onFailure { Log.w(TAG, "writing the copy failed", it) }.getOrElse { false }

        if (!written) {
            runCatching { contentResolver.delete(uri, null, null) }
            return@withContext SaveOutcome.FAILED
        }

        // While it is still pending, so that nothing else ever sees the copy
        // without its history.
        val kept = writeCarriedExif(uri, exif, bitmap.width, bitmap.height)

        runCatching {
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        if (kept) SaveOutcome.SAVED else SaveOutcome.SAVED_WITHOUT_METADATA
    }

/**
 * The format to write back in.
 *
 * A photo that arrived as a JPEG leaves as one. Anything with transparency to
 * lose becomes a PNG, because the alternative is a black rectangle where the
 * transparency was.
 */
private fun formatFor(mimeType: String): Bitmap.CompressFormat = when {
    mimeType.contains("png") || mimeType.contains("webp") -> Bitmap.CompressFormat.PNG
    else -> Bitmap.CompressFormat.JPEG
}
