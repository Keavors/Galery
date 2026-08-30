package com.keavors.gallery.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.getSystemService

/**
 * Hands the file to another app.
 *
 * The read permission has to travel with the intent: the receiving app has no
 * standing right to a MediaStore uri just because it was sent one.
 */
fun Context.shareMedia(item: MediaItem, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType.ifEmpty { if (item.isVideo) "video/*" else "image/*" }
        putExtra(Intent.EXTRA_STREAM, item.contentUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/**
 * Hands several files over at once.
 *
 * A different action from the single-file one: ACTION_SEND with a list is simply
 * ignored by most apps, and the picture would vanish into nothing.
 */
fun Context.shareMedia(items: List<MediaItem>, chooserTitle: String) {
    if (items.isEmpty()) return
    if (items.size == 1) return shareMedia(items.single(), chooserTitle)

    val uris = ArrayList(items.map { it.contentUri() })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        // The narrowest type that covers everything selected; a mixed selection
        // falls back to anything, which is what the system expects.
        type = if (items.all { it.isVideo }) "video/*"
        else if (items.none { it.isVideo }) "image/*"
        else "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

/**
 * Puts the picture itself on the clipboard, not its path.
 *
 * ClipData.newUri reads the mime type from the resolver, which is what lets a
 * messenger paste the image rather than a line of text saying content://...
 */
fun Context.copyMediaToClipboard(item: MediaItem, label: String) {
    val clip = ClipData.newUri(contentResolver, label, item.contentUri())
    getSystemService<ClipboardManager>()?.setPrimaryClip(clip)
}
