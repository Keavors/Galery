package com.keavors.gallery.ui

import android.net.Uri
import com.keavors.gallery.data.MediaItem

/** A photo another app asked this one to open. */
data class ExternalOpen(val uri: Uri, val declaredType: String?)

/** How the app was started: to browse, or to hand a photo back to another app. */
enum class LaunchMode { BROWSE, PICK }

/**
 * A folder being looked at on top of the tabs.
 *
 * [fromExternal] decides what leaving it does. Arrived at from another app's
 * photo, going back leaves the gallery entirely; opened from inside the app, it
 * returns to the tabs.
 */
data class FolderRoute(
    val bucketId: Long,
    val title: String,
    val fromExternal: Boolean,
)

/**
 * What the viewer is showing.
 *
 * The photos it pages through are worked out from [bucketId] each time rather
 * than captured when it opened, so a library that reloads underneath — a new
 * shot arriving, something deleted — does not leave the pager holding a list
 * that no longer exists.
 *
 * The exception is a photo that has just arrived from another app: it was found
 * by asking the database about one file and one folder, which is far quicker
 * than indexing the library, so [items] carries that answer. It is used only
 * until the library catches up and can supply the same folder itself.
 *
 * @param bucketId the folder to page through, or null for the whole library.
 * @param items photos found ahead of the library, or null to derive them.
 */
data class ViewerRoute(
    val itemId: Long,
    val bucketId: Long?,
    val thumbBucketPx: Int,
    val items: List<MediaItem>? = null,
)
