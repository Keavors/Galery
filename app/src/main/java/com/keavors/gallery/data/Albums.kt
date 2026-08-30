package com.keavors.gallery.data

/**
 * Where an album's photos come from.
 *
 * A folder is a real place on the disk; the others are questions asked of the
 * whole library. Keeping them in one type means the album screen, the viewer and
 * the back stack do not each need to know which kind they are looking at.
 */
sealed interface AlbumSource {
    data class Folder(val bucketId: Long) : AlbumSource
    data object Favourites : AlbumSource
    data object Videos : AlbumSource
}

/** Everything in one album, in the order the library already has it. */
fun List<MediaItem>.inAlbum(source: AlbumSource): List<MediaItem> = when (source) {
    is AlbumSource.Folder -> inFolder(source.bucketId)
    AlbumSource.Favourites -> filter { it.isFavorite }
    AlbumSource.Videos -> filter { it.isVideo }
}

/**
 * A folder as the albums screen shows it.
 *
 * @param newestAt when the most recent photo in it was taken, which is what the
 *   list is ordered by: the folder used this morning belongs above the one last
 *   touched in 2019, whatever they are called.
 */
data class FolderAlbum(
    val bucketId: Long,
    val name: String,
    val count: Int,
    val cover: MediaItem,
    val newestAt: Long,
)

/**
 * Groups the library into folders.
 *
 * Expects the library newest-first, as the repository hands it over, so the
 * first photo seen from a folder is both its cover and its date — no sorting
 * inside each group and no second pass.
 */
fun List<MediaItem>.folderAlbums(): List<FolderAlbum> {
    if (isEmpty()) return emptyList()

    val covers = LinkedHashMap<Long, MediaItem>()
    val counts = HashMap<Long, Int>()
    for (item in this) {
        covers.putIfAbsent(item.bucketId, item)
        counts[item.bucketId] = (counts[item.bucketId] ?: 0) + 1
    }

    return covers.map { (bucketId, cover) ->
        FolderAlbum(
            bucketId = bucketId,
            name = cover.bucketName.ifBlank { cover.relativePath.trimEnd('/').substringAfterLast('/') },
            count = counts[bucketId] ?: 0,
            cover = cover,
            newestAt = cover.takenAt,
        )
    }.sortedByDescending { it.newestAt }
}
