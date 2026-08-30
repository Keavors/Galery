package com.keavors.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
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
 * Turns and mirroring first, then the tilt, then the crop — the same order the
 * editor shows them in, which is what lets a crop chosen on screen mean the same
 * thing here.
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
        val x = (current.width * crop.left).toInt().coerceIn(0, current.width - 1)
        val y = (current.height * crop.top).toInt().coerceIn(0, current.height - 1)
        val w = (current.width * crop.width).toInt().coerceIn(1, current.width - x)
        val h = (current.height * crop.height).toInt().coerceIn(1, current.height - y)
        val cropped = Bitmap.createBitmap(current, x, y, w, h)
        if (cropped !== current && current !== source) current.recycle()
        current = cropped
    }

    return current
}

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
suspend fun Context.overwriteWith(item: MediaItem, bitmap: Bitmap, quality: Int): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openOutputStream(item.contentUri(), "wt")?.use { out ->
                bitmap.compress(formatFor(item.mimeType), quality, out)
            } ?: false
        }.onFailure { Log.w(TAG, "overwrite failed", it) }.getOrElse { false }
    }

/**
 * Writes the edited picture alongside the original.
 *
 * Pending first, published after, so no half-written photo ever appears in
 * anybody's gallery.
 */
suspend fun Context.saveEditedCopy(item: MediaItem, bitmap: Bitmap, quality: Int): Uri? =
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
            .getOrNull() ?: return@withContext null

        val written = runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(format, quality, out)
            } ?: false
        }.onFailure { Log.w(TAG, "writing the copy failed", it) }.getOrElse { false }

        if (!written) {
            runCatching { contentResolver.delete(uri, null, null) }
            return@withContext null
        }

        runCatching {
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        uri
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
