package com.keavors.gallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The content Uri that opens an item with this id. */
fun mediaContentUri(id: Long, isVideo: Boolean): Uri = ContentUris.withAppendedId(
    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    id,
)

/** The content Uri that opens this item. */
fun MediaItem.contentUri(): Uri = uri.toUri()

/**
 * Reads the whole library out of MediaStore in one pass.
 *
 * Photos and videos are queried together through the Files collection rather
 * than as two separate cursors: one query means one sort, and no merging of two
 * differently-ordered lists afterwards. Trashed items are excluded by default,
 * which is what every screen except the trash wants.
 */
class MediaStoreSource(private val context: Context) {

    suspend fun query(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val items = ArrayList<MediaItem>(2048)
        context.contentResolver.query(collection, PROJECTION, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val favouriteCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.IS_FAVORITE)
            val orientationCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.ORIENTATION)

            while (c.moveToNext()) {
                val added = c.getLong(addedCol)
                val modified = c.getLong(modifiedCol)
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                items += MediaItem(
                    id = id,
                    uri = mediaContentUri(id, isVideo).toString(),
                    name = c.getString(nameCol) ?: "",
                    mimeType = c.getString(mimeCol) ?: "",
                    isVideo = isVideo,
                    sizeBytes = c.getLong(sizeCol),
                    width = c.getInt(widthCol),
                    height = c.getInt(heightCol),
                    durationMs = c.getLong(durationCol),
                    takenAt = MediaTime.bestTimestamp(c.getLong(takenCol), modified, added),
                    addedAt = added * 1000,
                    modifiedAt = modified * 1000,
                    bucketId = c.getLong(bucketIdCol),
                    bucketName = c.getString(bucketNameCol) ?: "",
                    relativePath = c.getString(pathCol) ?: "",
                    isFavorite = c.getInt(favouriteCol) == 1,
                    orientation = c.getInt(orientationCol),
                )
            }
        }
        // Newest first. Sorting here rather than in SQL keeps the ordering rule
        // in Kotlin, where the timeline can vary it without rewriting a query.
        items.sortByDescending { it.takenAt }
        items
    }

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.IS_FAVORITE,
            MediaStore.Files.FileColumns.ORIENTATION,
        )
    }
}
