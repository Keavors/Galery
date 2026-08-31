package com.keavors.gallery.ui

import android.net.Uri
import com.keavors.gallery.data.AlbumSource
import com.keavors.gallery.data.MediaItem

/** A photo another app asked this one to open. */
data class ExternalOpen(val uri: Uri, val declaredType: String?)

/** How the app was started: to browse, or to hand a photo back to another app. */
enum class LaunchMode { BROWSE, PICK }

/**
 * A folder being looked at on top of the tabs.
 *
 * Leaving one always lands on the timeline, however it was arrived at — from
 * the albums tab, or from a photograph another app sent over. Back walks one
 * step towards the pictures and never sideways.
 */
data class FolderRoute(
    val source: AlbumSource,
    val title: String,
)

/**
 * What the viewer is showing.
 *
 * The photos it pages through are worked out from [source] each time rather
 * than captured when it opened, so a library that reloads underneath — a new
 * shot arriving, something deleted — does not leave the pager holding a list
 * that no longer exists.
 *
 * The exception is a photo that has just arrived from another app: it was found
 * by asking the database about one file and one folder, which is far quicker
 * than indexing the library, so [items] carries that answer. It is used only
 * until the library catches up and can supply the same folder itself.
 *
 * @param source the album to page through, or null for the whole library.
 * @param items photos found ahead of the library, or null to derive them.
 * @param resolved false while this is a photo from another app that has been
 *   put on screen straight from its uri and has not yet been found in the
 *   library. It becomes true exactly once per opening, and the viewer is rebuilt
 *   when it does: a single picture at page zero and the same picture at page
 *   forty of its folder are not the same pager.
 */
data class ViewerRoute(
    val itemId: Long,
    val source: AlbumSource?,
    val thumbBucketPx: Int,
    val items: List<MediaItem>? = null,
    val resolved: Boolean = true,
)
