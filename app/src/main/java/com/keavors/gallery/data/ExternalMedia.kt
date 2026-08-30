package com.keavors.gallery.data

/**
 * Everything that could be learned about a file another app handed over.
 *
 * Any of it may be missing. A file manager passes a MediaStore uri and all of
 * this is known; a messenger passes a uri into its own private cache and there
 * may be nothing but a name.
 */
data class ExternalRef(
    val mediaStoreId: Long? = null,
    val name: String? = null,
    val sizeBytes: Long? = null,
    val isVideo: Boolean = false,
    val mimeType: String = "",
    val uri: String = "",
)

/**
 * Finds the incoming file in the library.
 *
 * This is the part of opening a photo from another app that decides whether the
 * neighbours can be found, so it is written as a cascade of rules that get
 * progressively less certain, and it stops at the first one that answers.
 *
 * Returns null when the file is genuinely not in the library — a picture from a
 * messenger's private cache, say — and the viewer then shows it on its own.
 */
fun List<MediaItem>.matchExternal(ref: ExternalRef): MediaItem? {
    // The uri names a row in MediaStore. Nothing beats knowing the id.
    ref.mediaStoreId?.let { id ->
        firstOrNull { it.id == id }?.let { return it }
    }

    // The same file always has the same uri, whoever asked for it.
    if (ref.uri.isNotEmpty()) {
        firstOrNull { it.uri == ref.uri }?.let { return it }
    }

    val name = ref.name?.takeIf { it.isNotBlank() } ?: return null
    val byName = filter { it.name == name }
    if (byName.isEmpty()) return null
    if (byName.size == 1) return byName.single()

    // Several files share the name — DSC_0001.jpg lives in a dozen folders. The
    // size settles it; without one, guessing would be worse than not answering,
    // because the wrong guess silently shows the wrong folder's neighbours.
    val size = ref.sizeBytes ?: return null
    return byName.singleOrNull { it.sizeBytes == size }
}

/**
 * Everything in one folder, in the order the library already has it.
 *
 * Same folder rather than same day: opening a shot from a downloads folder
 * should page through that folder, not through everything taken that afternoon.
 */
fun List<MediaItem>.inFolder(bucketId: Long): List<MediaItem> =
    filter { it.bucketId == bucketId }

/** The photos alongside [item], including it. */
fun List<MediaItem>.folderOf(item: MediaItem): List<MediaItem> = inFolder(item.bucketId)

/**
 * Where the photo with this id sits in the list, or 0 if it is no longer there.
 *
 * Landing on the first neighbour beats an index of minus one: the library can
 * reload between opening a photo and looking at it.
 */
fun List<MediaItem>.indexOfId(id: Long): Int =
    indexOfFirst { it.id == id }.coerceAtLeast(0)
