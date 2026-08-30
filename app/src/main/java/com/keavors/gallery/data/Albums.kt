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

    /** One somebody made. Its contents live in [AlbumPreferences], not on disk. */
    data class User(val albumId: Long) : AlbumSource

    /** Files taken out of the library entirely. Nothing here comes from it. */
    data object Vault : AlbumSource
}

/**
 * Everything in one album, in the order the library already has it.
 *
 * User albums need [userAlbums] because their membership is the one thing in
 * this app that cannot be asked of MediaStore.
 */
fun List<MediaItem>.inAlbum(
    source: AlbumSource,
    userAlbums: List<UserAlbum> = emptyList(),
): List<MediaItem> = when (source) {
    is AlbumSource.Folder -> inFolder(source.bucketId)
    AlbumSource.Favourites -> filter { it.isFavorite }
    AlbumSource.Videos -> filter { it.isVideo }
    is AlbumSource.User -> {
        val members = userAlbums.firstOrNull { it.id == source.albumId }?.memberIds.orEmpty()
        filter { it.id in members }
    }
    // The library cannot answer for the vault: that is the whole point of it.
    AlbumSource.Vault -> emptyList()
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

/**
 * Pinned albums float to the top, everything else keeps the order it had.
 *
 * Sorting rather than filtering into two lists so the screen renders one list:
 * a pinned album that is later unpinned drops back into its old place instead of
 * appearing somewhere new.
 */
fun List<FolderAlbum>.pinnedFirst(prefs: AlbumPreferences): List<FolderAlbum> {
    val (pinned, rest) = partition { prefs.isPinned(AlbumSource.Folder(it.bucketId)) }
    return pinned + rest
}

/** Albums the person chose not to see, unless they asked to see them again. */
fun List<FolderAlbum>.withoutHidden(prefs: AlbumPreferences, showHidden: Boolean): List<FolderAlbum> =
    if (showHidden) this else filterNot { prefs.isHidden(AlbumSource.Folder(it.bucketId)) }
