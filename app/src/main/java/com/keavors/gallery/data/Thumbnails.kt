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
 * Puts a photograph's thumbnail in memory, where the viewer looks for something
 * to show while the full-size file is still being read.
 *
 * The viewer asks the memory cache for its placeholder exactly once, when its
 * own request runs, so a thumbnail that arrives a moment after that arrives too
 * late to be seen at all. These two exist to make sure it is there by then:
 * [startPreview] from the instant an intent arrives, before there is any screen
 * to show it on, and [awaitPreview] before the viewer is rebuilt on the folder
 * the photograph turned out to live in.
 *
 * Neither costs anything when the grid has already drawn the photograph, which
 * is the usual case: it is the same request under the same name, so it is
 * answered out of memory and no file is touched.
 */
fun Context.startPreview(item: MediaItem, bucketPx: Int) {
    val request = previewRequest(item, bucketPx) ?: return
    SingletonImageLoader.get(this).enqueue(request)
}

/** As [startPreview], but waits for the thumbnail to be in memory. */
suspend fun Context.awaitPreview(item: MediaItem, bucketPx: Int) {
    val request = previewRequest(item, bucketPx) ?: return
    SingletonImageLoader.get(this).execute(request)
}

/**
 * The request both of those make, or null when there is no thumbnail to be had.
 *
 * Only MediaStore keeps thumbnails, so a file in the app's own storage or one
 * whose uri named no row has none to warm: for those the file itself is the
 * only picture there is, and the viewer decodes it.
 */
private fun Context.previewRequest(item: MediaItem, bucketPx: Int): ImageRequest? {
    if (item.isPrivate || item.id == UNKNOWN_ID) return null
    return ImageRequest.Builder(applicationContext)
        .data(MediaThumb(item.id, item.isVideo))
        .size(bucketPx)
        // The grid's own name for it, so warming it here and drawing it there
        // are one cached bitmap rather than two.
        .memoryCacheKey(thumbnailCacheKey(item.id, bucketPx))
        .build()
}
