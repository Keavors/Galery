@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.keavors.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "VideoTrim"

/**
 * The piece of a video that is being kept.
 *
 * Milliseconds from the start of the file, both ends. Everything about which
 * numbers are allowed lives in the two functions below, so that the handles on
 * screen and the export that follows them cannot disagree about what a trim is.
 */
data class TrimRange(val startMs: Long, val endMs: Long) {

    val lengthMs: Long get() = endMs - startMs

    /** True when nothing has actually been trimmed off either end. */
    fun isWhole(durationMs: Long): Boolean = startMs <= 0L && endMs >= durationMs

    companion object {
        /**
         * The shortest a trim may leave.
         *
         * Half a second is about the least that is still a clip rather than a
         * mistake, and it keeps the two handles from ever crossing.
         */
        const val MIN_LENGTH_MS = 500L

        fun whole(durationMs: Long) = TrimRange(0L, durationMs.coerceAtLeast(MIN_LENGTH_MS))
    }
}

/** The range after the left handle is dragged to [wanted]. */
fun TrimRange.startAt(wanted: Long): TrimRange =
    copy(startMs = wanted.coerceIn(0L, (endMs - TrimRange.MIN_LENGTH_MS).coerceAtLeast(0L)))

/** The range after the right handle is dragged to [wanted]. */
fun TrimRange.endAt(wanted: Long, durationMs: Long): TrimRange =
    copy(
        endMs = wanted.coerceIn(
            (startMs + TrimRange.MIN_LENGTH_MS).coerceAtMost(durationMs),
            durationMs,
        )
    )

/**
 * Where the frames along the timeline are taken from.
 *
 * Spread across the whole video rather than across the trim, because the strip
 * is what the handles are dragged along: it has to keep still while they move.
 */
fun frameTimesMs(durationMs: Long, count: Int): List<Long> {
    if (durationMs <= 0L || count <= 0) return emptyList()
    // The middle of each slice rather than its start, so the first frame is not
    // the black one every video begins with.
    return (0 until count).map { i -> durationMs * (2 * i + 1) / (2L * count) }
}

/**
 * A handful of frames from across the video, for the strip the handles slide
 * along.
 *
 * Small ones: they are shown a centimetre high, and asking the decoder for
 * full-size frames to throw nine tenths of away is how a screen takes a second
 * to open. Any of them may be missing — a frame near the end of a damaged file
 * is a fair thing for a decoder to refuse — and a gap in the strip is better
 * than no strip.
 */
suspend fun Context.videoFrames(item: MediaItem, times: List<Long>): List<Bitmap?> =
    withContext(Dispatchers.IO) {
        if (times.isEmpty()) return@withContext emptyList()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this@videoFrames, item.contentUri())
            val wide = if (item.height > 0) {
                (FRAME_HEIGHT_PX * item.width / item.height).coerceIn(1, FRAME_HEIGHT_PX * 4)
            } else {
                FRAME_HEIGHT_PX
            }
            times.map { at ->
                runCatching {
                    retriever.getScaledFrameAtTime(
                        at * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        wide,
                        FRAME_HEIGHT_PX,
                    )
                }.getOrNull()
            }
        } catch (failure: RuntimeException) {
            // setDataSource throws this for anything it cannot open, and a video
            // that will not give up its frames is still a video that can be
            // trimmed by the clock.
            Log.w(TAG, "could not read frames out of the video", failure)
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

/** Two hundred pixels tall: the strip is drawn at about a third of that. */
private const val FRAME_HEIGHT_PX = 200

/**
 * Writes the kept piece of a video out as a new file.
 *
 * Always a copy, never over the original, and that is a limit rather than a
 * preference: the encoder reads the source while it writes, so the one file it
 * cannot write to is the one it is reading. What comes out is a real
 * re-encode — hardware, through Media3's Transformer — so the result is a
 * normal video rather than a container with a note in it about where to start.
 */
suspend fun Context.trimVideo(
    item: MediaItem,
    range: TrimRange,
    onProgress: (Int) -> Unit,
): SaveOutcome {
    val scratch = File(cacheDir, "trim-${item.id}-${System.currentTimeMillis()}.mp4")
    return try {
        if (!exportClip(item, range, scratch, onProgress)) {
            SaveOutcome.FAILED
        } else {
            publishTrimmed(item, scratch)
        }
    } finally {
        // The scratch file goes whatever happened, including a cancellation:
        // a cache full of half-written videos is its own bug.
        withContext(NonCancellable + Dispatchers.IO) { scratch.delete() }
    }
}

/**
 * The encode itself.
 *
 * Transformer talks on the thread it was built on and expects that thread to
 * have a looper, so all of this happens on the main one. None of the work does:
 * that is on the codec.
 */
private suspend fun Context.exportClip(
    item: MediaItem,
    range: TrimRange,
    target: File,
    onProgress: (Int) -> Unit,
): Boolean = withContext(Dispatchers.Main) {
    val clipped = Media3Item.Builder()
        .setUri(item.contentUri())
        .setClippingConfiguration(
            Media3Item.ClippingConfiguration.Builder()
                .setStartPositionMs(range.startMs)
                .setEndPositionMs(range.endMs)
                .build()
        )
        .build()

    // Shared between the encode and the watcher below, both of which run here on
    // the main thread, which is the only thread allowed to ask it anything.
    var transformer: Transformer? = null

    val watcher = launch {
        val holder = ProgressHolder()
        while (isActive) {
            delay(PROGRESS_INTERVAL_MS)
            // Asked rather than pushed: Transformer has no progress callback,
            // and five times a second is plenty for a bar somebody is watching.
            val state = transformer?.getProgress(holder) ?: continue
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
        }
    }

    try {
        suspendCancellableCoroutine { continuation ->
            val encoder = Transformer.Builder(this@exportClip)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        continuation.resume(true)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        Log.w(TAG, "could not write the trimmed video", exception)
                        continuation.resume(false)
                    }
                })
                .build()

            transformer = encoder
            encoder.start(EditedMediaItem.Builder(clipped).build(), target.absolutePath)

            continuation.invokeOnCancellation {
                // Cancellation can arrive on any thread; the encoder may only be
                // stopped from the one that started it.
                Handler(Looper.getMainLooper()).post { runCatching { encoder.cancel() } }
            }
        }
    } finally {
        watcher.cancel()
    }
}

/** How often the bar is asked to catch up, in milliseconds. */
private const val PROGRESS_INTERVAL_MS = 200L

/**
 * Moves the finished file into the library, beside the video it came from.
 *
 * Pending first, published after, so no half-copied video ever appears in
 * anybody's gallery — the same order the photo editor writes in.
 */
private suspend fun Context.publishTrimmed(item: MediaItem, source: File): SaveOutcome =
    withContext(Dispatchers.IO) {
        val stem = item.name.substringBeforeLast('.', item.name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, stem + "_trim.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            val path = item.relativePath.trim().trim('/')
            if (path.isNotEmpty() && !item.relativePath.startsWith("/")) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$path/")
            }
            // A video has no EXIF to carry, but it does have the one thing that
            // matters most: when it was taken. Without this the clip sorts to
            // today and is lost among things it has nothing to do with.
            if (item.takenAt > 0) put(MediaStore.Video.Media.DATE_TAKEN, item.takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { contentResolver.insert(collection, values) }
            .onFailure { Log.w(TAG, "could not create the trimmed copy", it) }
            .getOrNull() ?: return@withContext SaveOutcome.FAILED

        val copied = runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
                true
            } ?: false
        }.onFailure { Log.w(TAG, "could not write the trimmed copy", it) }.getOrElse { false }

        if (!copied) {
            runCatching { contentResolver.delete(uri, null, null) }
            return@withContext SaveOutcome.FAILED
        }

        runCatching {
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        SaveOutcome.SAVED
    }
