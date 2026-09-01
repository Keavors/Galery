package com.keavors.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * What the grid asks the image loader for.
 *
 * A type of its own rather than a plain Uri: it keeps the thumbnail path from
 * colliding with ordinary image loading, and it doubles as the memory cache key.
 */
data class MediaThumb(val id: Long, val isVideo: Boolean)

/**
 * The sizes thumbnails are actually kept at.
 *
 * Tiles range from about forty pixels across at twenty-five columns to half the
 * screen at two, and caching a separate bitmap for every width in between would
 * mean a cache miss on every zoom change. Rounding up to a handful of sizes
 * means zooming reuses what is already in memory.
 */
private val THUMB_BUCKETS = intArrayOf(96, 192, 384, 768)

/**
 * The bucket to load at when nothing on screen has been measured yet.
 *
 * A photograph opened from another app has no tile behind it to take a size
 * from, and the grid's own default zoom lands here — so a thumbnail cached for
 * one is the thumbnail wanted by the other.
 */
const val DEFAULT_THUMB_BUCKET = 384

/**
 * The sizes that may stand in for a tile of [bucketPx], best first.
 *
 * The size asked for, and then every larger one. A thumbnail already in memory
 * for a four-column grid is a perfectly good picture for a tile a quarter of the
 * size — the screen scales it down for nothing — and using it is the difference
 * between zooming out instantly and asking the media store for five thousand
 * smaller copies of pictures it has already given us.
 */
fun standInBuckets(bucketPx: Int): List<Int> = THUMB_BUCKETS.filter { it >= bucketPx }

/** The bucket a tile of [tilePx] should load at. */
fun thumbnailBucketPx(tilePx: Int): Int =
    THUMB_BUCKETS.firstOrNull { it >= tilePx } ?: THUMB_BUCKETS.last()

/**
 * What a cached thumbnail is called.
 *
 * Coil names cache entries through a keyer registered for the data type, and it
 * has none for [MediaThumb] — so unless the name is supplied by hand nothing is
 * ever written to the memory cache, and every tile is re-read the moment it
 * scrolls back into view. The bucket belongs in the name because the same photo
 * is held at several sizes.
 */
fun thumbnailCacheKey(id: Long, bucketPx: Int): String = "thumb-$id-$bucketPx"

/**
 * Loads grid thumbnails through MediaStore instead of decoding the originals.
 *
 * The phone already keeps a thumbnail for every photo and video it knows about,
 * and asking for it is the difference between reading a few kilobytes and
 * decoding a hundred-megapixel JPEG for a tile the size of a fingernail. It also
 * gets video covers for free, with no frame extraction.
 *
 * What it does not do is honour the size it is asked for: MediaStore hands back
 * whatever it has stored, typically around 512x384. At twenty-five columns that
 * is a three-quarter-megabyte bitmap for a forty-pixel tile, six hundred of them
 * on screen — enough to evict the cache on every scroll and to make the whole
 * grid stutter. So whatever comes back is scaled down here before it is cached.
 */
class MediaThumbnailFetcher(
    private val context: Context,
    private val thumb: MediaThumb,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val target = maxOf(
            options.size.width.pxOrElse { FALLBACK_PX },
            options.size.height.pxOrElse { FALLBACK_PX },
        ).coerceAtLeast(1)

        val raw = context.contentResolver.loadThumbnail(
            mediaContentUri(thumb.id, thumb.isVideo),
            Size(target, target),
            null,
        )

        ImageFetchResult(
            image = raw.fitShortestEdgeTo(target).asImage(),
            // Scaled to fit the tile, not cropped to it, so Coil should not treat
            // the result as an exact match for the requested size.
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(context, data, options)
    }

    private companion object {
        /** Used when the layout has not measured the tile yet. */
        const val FALLBACK_PX = DEFAULT_THUMB_BUCKET
    }
}

/**
 * Shrinks a bitmap until its shorter side matches [target].
 *
 * The shorter side, not the longer one: tiles are square and crop what does not
 * fit, so scaling by the long edge would leave the crop working from fewer
 * pixels than the tile has and the grid looking soft.
 */
private fun Bitmap.fitShortestEdgeTo(target: Int): Bitmap {
    val shortest = minOf(width, height)
    if (shortest <= target) return this

    val ratio = target.toFloat() / shortest
    val scaled = Bitmap.createScaledBitmap(
        this,
        (width * ratio).roundToInt().coerceAtLeast(1),
        (height * ratio).roundToInt().coerceAtLeast(1),
        true,
    )
    // The source came from loadThumbnail and belongs to us, so it can go back
    // rather than waiting on the collector with the rest of the grid in flight.
    if (scaled !== this) recycle()
    return scaled
}

/**
 * What a photograph looks like before it has been read.
 *
 * Everywhere a picture is wanted quickly asks for this one request: the grid for
 * its tiles, the viewer for the thing it shows while the full-size file is being
 * decoded, and the warmers below before there is a screen at all. One request
 * means one name in the cache, so a thumbnail drawn in a grid is the very
 * bitmap the viewer puts up a moment later, and neither of them decodes twice.
 *
 * MediaStore keeps a thumbnail for everything it knows about, and asking it is
 * far cheaper than decoding the original. A file it does not know — one in the
 * app's own storage, or one another app handed over under a uri that names no
 * row — has to be decoded, but only down to [bucketPx], which is a fraction of
 * the work of decoding it whole.
 */
fun previewRequest(context: Context, item: MediaItem, bucketPx: Int): ImageRequest {
    val key = previewCacheKey(item, bucketPx)
    return ImageRequest.Builder(context)
        // The whole of the loading, off the thread that draws. By default Coil
        // starts and finishes a request on the main thread, which is nothing at
        // all for one picture and six hundred interruptions for a screenful of
        // grid at the smallest zoom.
        .interceptorCoroutineContext(Dispatchers.Default)
        .data(
            if (item.isPrivate || item.id == UNKNOWN_ID) {
                item.contentUri()
            } else {
                MediaThumb(item.id, item.isVideo)
            }
        )
        .size(bucketPx)
        .memoryCacheKey(key)
        // Draws what is already in memory immediately. Without it a picture that
        // has been decoded once still blinks empty for a frame while the request
        // goes round the loader again.
        .placeholderMemoryCacheKey(key)
        .build()
}

/**
 * What that request is called in memory.
 *
 * The row number wherever there is one, so the grid and the viewer agree without
 * having to be told. A photograph from another app that names no row is keyed by
 * where it came from instead — it is the only name it has.
 */
fun previewCacheKey(item: MediaItem, bucketPx: Int): String =
    if (item.id == UNKNOWN_ID) "preview-$bucketPx-${item.uri}" else thumbnailCacheKey(item.id, bucketPx)

/**
 * Starts a photograph's preview before there is any screen to show it on.
 *
 * Called the instant an intent from another app is read, which is earlier than
 * the screen that will show the photograph can be composed, laid out or drawn.
 * A video nobody can name is the one thing skipped: there is no decoder here for
 * a video file, only for the thumbnail MediaStore keeps of one.
 */
fun Context.startPreview(item: MediaItem, bucketPx: Int) {
    if (item.isVideo && item.id == UNKNOWN_ID) return
    SingletonImageLoader.get(this).enqueue(previewRequest(applicationContext, item, bucketPx))
}

/**
 * As [startPreview], but does not return until the preview is in memory.
 *
 * For the one moment where the difference matters: the viewer is about to be
 * rebuilt on the folder the photograph turned out to live in, and a rebuilt page
 * asks for its picture once. If the answer is not there by then the photograph
 * goes black at exactly the moment the neighbours arrive. Free when the picture
 * is already up, which is the usual case.
 */
suspend fun Context.awaitPreview(item: MediaItem, bucketPx: Int) {
    if (item.isVideo && item.id == UNKNOWN_ID) return
    SingletonImageLoader.get(this).execute(previewRequest(applicationContext, item, bucketPx))
}
