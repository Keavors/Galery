package com.keavors.gallery.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Works out what the incoming uri is, so [matchExternal] can look for it.
 *
 * The uri arrives in one of several shapes depending on which app sent it, and
 * each step here is a way of turning one of those shapes into something the
 * library can be searched by. Every step is guarded: a foreign content provider
 * is free to refuse any of these questions, and refusing must cost a fallback,
 * not a crash.
 */
suspend fun Context.probeExternal(uri: Uri, declaredType: String?): ExternalRef =
    withContext(Dispatchers.IO) {
        val mime = declaredType
            ?: runCatching { contentResolver.getType(uri) }.getOrNull()
            ?: ""
        val isVideo = mime.startsWith("video")

        // A MediaStore uri names its row in the last path segment. This is the
        // common case — file managers and other galleries send these.
        var id = uri.mediaStoreId()

        // Someone else's FileProvider. The system can often say which MediaStore
        // row it stands for, which puts us back in the easy case.
        if (id == null) {
            id = runCatching { MediaStore.getMediaUri(this@probeExternal, uri) }
                .getOrNull()
                ?.mediaStoreId()
        }

        var name: String? = null
        var size: Long? = null
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameCol = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameCol >= 0 && !c.isNull(nameCol)) name = c.getString(nameCol)
                    if (sizeCol >= 0 && !c.isNull(sizeCol)) size = c.getLong(sizeCol)
                }
            }
        }

        // A plain file uri, or a provider that answered nothing: the last path
        // segment is still usually the file name.
        if (name.isNullOrBlank()) name = uri.lastPathSegment?.substringAfterLast('/')

        ExternalRef(
            mediaStoreId = id,
            name = name,
            sizeBytes = size,
            isVideo = isVideo,
            mimeType = mime,
            uri = uri.toString(),
        )
    }

/**
 * Builds a stand-in for a file that is not in the library.
 *
 * Everything works on it except moving to a neighbour, because it genuinely has
 * none — it is sitting in some other app's private storage.
 */
fun ExternalRef.asStandaloneItem(): MediaItem = MediaItem(
    id = -1,
    uri = uri,
    name = name.orEmpty(),
    mimeType = mimeType,
    isVideo = isVideo,
    sizeBytes = sizeBytes ?: 0,
    width = 0,
    height = 0,
    durationMs = 0,
    takenAt = 0,
    addedAt = 0,
    modifiedAt = 0,
    bucketId = -1,
    bucketName = "",
    relativePath = "",
    isFavorite = false,
    orientation = 0,
    expiresAt = 0,
)

/** The MediaStore row id in a uri, or null if this is not one of ours. */
private fun Uri.mediaStoreId(): Long? {
    if (authority != MediaStore.AUTHORITY) return null
    return lastPathSegment?.toLongOrNull()
}
