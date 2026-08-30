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
 * @param bucketId the folder to page through, or null for the whole library.
 * @param standalone a file that is not in the library at all, which happens when
 *   another app sends a photo out of its own private storage. It has no
 *   neighbours because it genuinely has none.
 */
data class ViewerRoute(
    val itemId: Long,
    val bucketId: Long?,
    val thumbBucketPx: Int,
    val standalone: MediaItem? = null,
)
