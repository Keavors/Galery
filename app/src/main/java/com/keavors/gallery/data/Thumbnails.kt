package com.keavors.gallery.data

import android.content.Context
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the grid asks the image loader for.
 *
 * A type of its own rather than a plain Uri: it keeps the thumbnail path from
 * colliding with ordinary image loading, and it doubles as the memory cache key.
 */
data class MediaThumb(val id: Long, val isVideo: Boolean)

/**
 * Loads grid thumbnails through MediaStore instead of decoding the originals.
 *
 * The phone already keeps a thumbnail for every photo and video it knows about,
 * and asking for it is the difference between reading a few kilobytes and
 * decoding a hundred-megapixel JPEG for a tile the size of a fingernail. It also
 * gets video covers for free, with no frame extraction.
 */
class MediaThumbnailFetcher(
    private val context: Context,
    private val thumb: MediaThumb,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val width = options.size.width.pxOrElse { FALLBACK_PX }
        val height = options.size.height.pxOrElse { FALLBACK_PX }
        val bitmap = context.contentResolver.loadThumbnail(
            mediaContentUri(thumb.id, thumb.isVideo),
            Size(width.coerceAtLeast(1), height.coerceAtLeast(1)),
            null,
        )
        ImageFetchResult(
            image = bitmap.asImage(),
            // The bitmap comes back at whatever size MediaStore had, not at the
            // exact size requested, so Coil must not treat it as pixel-perfect.
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
        const val FALLBACK_PX = 512
    }
}
