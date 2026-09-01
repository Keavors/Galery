package com.keavors.gallery.data

/**
 * Where an album's photos come from.
 *
 * An album is a folder on the disk — the same thing under two names, because
 * anything else would be an album this app can see and nothing else can. The
 * others here are not albums but questions asked of the whole library, and they
 * are in the same type so that the album screen, the viewer and the back stack
 * do not each need to know which kind they are looking at.
 */
sealed interface AlbumSource {
    data class Folder(val bucketId: Long) : AlbumSource
    data object Favourites : AlbumSource
    data object Videos : AlbumSource

    /** Files taken out of the library entirely. Nothing here comes from it. */
    data object Vault : AlbumSource
}

/** Everything in one album, in the order the library already has it. */
fun List<MediaItem>.inAlbum(source: AlbumSource): List<MediaItem> = when (source) {
    is AlbumSource.Folder -> inFolder(source.bucketId)
    AlbumSource.Favourites -> filter { it.isFavorite }
    AlbumSource.Videos -> filter { it.isVideo }
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
    /**
     * Where the folder actually is, as MediaStore says it: "DCIM/Camera/".
     *
     * Carried rather than derived from the name, because the name is only the
     * last part of it and two folders in different places may share one.
     */
    val path: String,
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
            // Blank when the files sit in the root of the storage: there is no
            // folder there to take a name from, and the screen supplies one.
            name = cover.bucketName.ifBlank {
                cover.relativePath.trim('/').substringAfterLast('/')
            },
            count = counts[bucketId] ?: 0,
            cover = cover,
            newestAt = cover.takenAt,
            path = cover.relativePath,
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

/**
 * The library without the folders somebody switched off.
 *
 * Hiding an album on the albums screen is how a folder is taken out of the
 * gallery — the specification calls it "любую папку можно выключить" — and a
 * folder that is out of the gallery has no business filling the timeline.
 * Switching the setting back on shows them again without unhiding anything.
 */
fun List<MediaItem>.visibleIn(prefs: AlbumPreferences, showHidden: Boolean): List<MediaItem> {
    if (showHidden || prefs.hidden.isEmpty()) return this
    return filterNot { prefs.isHidden(AlbumSource.Folder(it.bucketId)) }
}
