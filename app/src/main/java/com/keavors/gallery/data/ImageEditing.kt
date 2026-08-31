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
import android.os.Environment
import android.provider.MediaStore
import java.io.File
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
 * colour, and last of all whatever was drawn on top — the same order the editor
 * shows them in, which is what lets a crop chosen on screen mean the same thing
 * here. The marks come last because they are meant to be seen: a brightness
 * slider has no business lightening somebody's pen.
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

    if (ops.marks.isNotEmpty()) {
        val marked = current.withMarks(ops.marks, ops.crop)
        if (marked !== current && current !== source) current.recycle()
        current = marked
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
 * How a save went, and — when it did not go — what actually stopped it.
 *
 * "Could not save" is no use to anybody: a save can fail for a dozen unrelated
 * reasons and only the one it hit says what to do about it.
 */
data class SaveResult(val outcome: SaveOutcome, val reason: String = "")

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
): SaveResult = withContext(Dispatchers.IO) {
    // The whole photograph is assembled in a scratch file first — pixels, then
    // the history that belongs with them — and only then copied over the
    // original, in one go.
    //
    // Doing it the other way round is what made a replaced photograph look like
    // a brand new one. Writing the pixels straight into the library's file and
    // patching the metadata afterwards leaves a moment where the file is a
    // picture with no history at all, and the library looks at it during that
    // moment: it finds no date inside, so it uses the only date left, which is
    // now. The correction that arrives a heartbeat later is a correction to a
    // decision already taken.
    val scratch = File(cacheDir, "edit-${item.id}-${System.currentTimeMillis()}.tmp")
    try {
        val compressed = runCatching {
            scratch.outputStream().use { out ->
                if (bitmap.compress(formatFor(item.mimeType), quality, out)) null
                else "the picture would not compress"
            }
        }.onFailure { Log.w(TAG, "could not lay out the edited photo", it) }
            .getOrElse { it.describe() }

        if (compressed != null) {
            return@withContext SaveResult(SaveOutcome.FAILED, compressed)
        }

        val kept = writeCarriedExif(scratch, exif, bitmap.width, bitmap.height)

        val copied = runCatching {
            val out = contentResolver.openOutputStream(item.contentUri(), "wt")
            if (out == null) {
                "the original would not open for writing"
            } else {
                out.use { scratch.inputStream().use { source -> source.copyTo(it) } }
                null
            }
        }.onFailure { Log.w(TAG, "overwrite failed", it) }.getOrElse { it.describe() }

        when {
            copied != null -> SaveResult(SaveOutcome.FAILED, copied)
            kept -> SaveResult(SaveOutcome.SAVED)
            else -> SaveResult(SaveOutcome.SAVED_WITHOUT_METADATA)
        }
    } finally {
        runCatching { scratch.delete() }
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
): SaveResult = withContext(Dispatchers.IO) {
    val beside = item.relativePath.trim().trim('/')
        .takeIf { it.isNotEmpty() && !item.relativePath.startsWith("/") }

    // Beside the original if the library will have it there, and in Pictures if
    // it will not. A photograph living in a messenger's own folder cannot be
    // joined by a new one, because that folder belongs to the messenger — and
    // "beside" is a courtesy, while saving the edit is the point.
    val first = writeCopy(item, bitmap, quality, exif, beside)
    if (first.outcome != SaveOutcome.FAILED || beside == null) {
        return@withContext first
    }

    val second = writeCopy(item, bitmap, quality, exif, Environment.DIRECTORY_PICTURES)
    if (second.outcome != SaveOutcome.FAILED) {
        second
    } else if (second.reason == first.reason) {
        second
    } else {
        second.copy(reason = "${first.reason} / ${second.reason}")
    }
}

/**
 * One attempt at putting a copy into the library, in [folder] or wherever the
 * library puts things with no folder asked for.
 *
 * Pending first, published after, so no half-written photo ever appears in
 * anybody's gallery.
 */
private suspend fun Context.writeCopy(
    item: MediaItem,
    bitmap: Bitmap,
    quality: Int,
    exif: Map<String, String>,
    folder: String?,
): SaveResult {
    val format = formatFor(item.mimeType)
    val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
    val stem = item.name.substringBeforeLast('.', item.name)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, stem + "_edit." + extension)
        put(
            MediaStore.MediaColumns.MIME_TYPE,
            if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg",
        )
        if (folder != null) put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val insert = runCatching { contentResolver.insert(collection, values) }
        .onFailure { Log.w(TAG, "could not create a copy", it) }
    val uri = insert.getOrNull() ?: return SaveResult(
        outcome = SaveOutcome.FAILED,
        reason = insert.exceptionOrNull()?.describe() ?: "the library refused a new file",
    )

    val written = runCatching {
        val out = contentResolver.openOutputStream(uri)
        if (out == null) {
            "the new file would not open for writing"
        } else {
            out.use { if (bitmap.compress(format, quality, it)) null else "the picture would not compress" }
        }
    }.onFailure { Log.w(TAG, "writing the copy failed", it) }.getOrElse { it.describe() }

    if (written != null) {
        runCatching { contentResolver.delete(uri, null, null) }
        return SaveResult(SaveOutcome.FAILED, written)
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
    return SaveResult(if (kept) SaveOutcome.SAVED else SaveOutcome.SAVED_WITHOUT_METADATA)
}

/** Anything's account of a failure, as short as it can be made. */
private fun Throwable.describe(): String =
    listOfNotNull(this::class.simpleName, message?.takeIf { it.isNotBlank() }).joinToString(": ")

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
