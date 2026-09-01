package com.keavors.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FramePreview"

/**
 * How wide a frame is fetched at, in pixels.
 *
 * Enough for the box the scrub bar puts it in and no more: asking for a frame at
 * the video's own resolution would decode eight million pixels to show half a
 * million, once for every position a dragging finger settles on.
 */
private const val PREVIEW_WIDTH = 960

/**
 * Reads one frame out of a video, for showing above the scrub bar.
 *
 * The nearest key frame rather than the exact one, deliberately: an exact frame
 * means decoding forward from the previous key frame, which can be a second of
 * video, and this happens again every time a dragging finger moves. The nearest
 * key frame is what every player shows while scrubbing, and it is close enough
 * to the truth to aim by.
 *
 * A retriever is expensive to open and cheap to reuse, so the caller keeps one
 * for as long as a finger is down and closes it after.
 */
suspend fun MediaMetadataRetriever.frameAt(positionMs: Long, height: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            getScaledFrameAtTime(
                positionMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                PREVIEW_WIDTH,
                height.coerceAtLeast(1),
            )
        }.onFailure { Log.w(TAG, "no frame at $positionMs", it) }.getOrNull()
    }

/**
 * Opens a reader for one video, or null if the file will not have it.
 *
 * Null is an ordinary answer: a video from another app's private storage may
 * refuse, and a scrub bar without a picture over it is still a scrub bar.
 */
suspend fun Context.frameReader(item: MediaItem): MediaMetadataRetriever? =
    withContext(Dispatchers.IO) {
        runCatching {
            MediaMetadataRetriever().apply { setDataSource(this@frameReader, item.contentUri()) }
        }.onFailure { Log.w(TAG, "could not read ${item.name}", it) }.getOrNull()
    }
