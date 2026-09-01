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
 * The id of a photograph nobody has identified yet.
 *
 * A file from another app's private storage has no MediaStore row and never
 * will; one that has only just arrived may have a row nobody has looked up yet.
 * Both are this, and both mean the same downstream: there is no number to ask
 * the library about.
 */
const val UNKNOWN_ID = -1L

/**
 * The photo as it can be known before anything has been asked of anybody.
 *
 * Opening a picture from another app used to wait for the database: probe the
 * uri, find the row, read the folder, and only then put something on screen.
 * All of that is worth doing — it is what makes the neighbours swipeable — but
 * none of it is needed to show the photograph, because the uri that arrived is
 * already the photograph. So it goes up first, from this, and the answer from
 * the database replaces it when it comes.
 */
fun provisionalItem(uri: Uri, declaredType: String?): MediaItem = ExternalRef(
    // Reading the row number out of the uri costs a string split, and it is what
    // decides whether the photograph is on screen in the first frame: with the
    // number, the thumbnail the grid drew a moment ago is found in memory and
    // put up at once. Without it there is nothing to show until the file has
    // been decoded, which is a tenth of a second of black.
    mediaStoreId = uri.mediaStoreId(),
    name = uri.lastPathSegment?.substringAfterLast('/'),
    isVideo = looksLikeVideo(declaredType, uri.toString()),
    mimeType = declaredType.orEmpty(),
    uri = uri.toString(),
).asStandaloneItem()

/**
 * A first guess at whether this is a video.
 *
 * The type the intent declared, and failing that the end of the file name.
 * Deliberately no question to the content provider: that is a call into another
 * app, another app can be slow, and the whole point of this is to be instant.
 * The considered answer comes from [probeExternal] a moment later.
 */
fun looksLikeVideo(declaredType: String?, uri: String): Boolean {
    if (!declaredType.isNullOrBlank()) return declaredType.startsWith("video")
    val extension = uri.substringBefore('?').substringAfterLast('.', "").lowercase()
    return extension in VIDEO_EXTENSIONS
}

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "m4v", "mkv", "webm", "3gp", "3gpp", "mov", "avi", "ts", "mts",
    "mpg", "mpeg", "flv", "wmv",
)

/**
 * Builds a stand-in for a file the library has not placed.
 *
 * Either it is not in the library at all — sitting in some other app's private
 * storage, with no neighbours to move to — or it has not been looked for yet.
 * Whatever the uri gave away is kept, the row number above all: that number is
 * how everything downstream recognises the same photograph again.
 */
fun ExternalRef.asStandaloneItem(): MediaItem = MediaItem(
    id = mediaStoreId ?: UNKNOWN_ID,
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
