package com.keavors.gallery.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FolderOps"

/**
 * Where a folder made from inside the app is put.
 *
 * Android takes only a handful of top-level directories and invents none, so a
 * new album has to live inside one of them. Pictures is the one meant for
 * photographs that did not come from the camera.
 */
private const val NEW_FOLDER_PARENT = "Pictures"

/** Characters that would make a name into a path, or into nothing at all. */
private val FORBIDDEN_IN_NAME = charArrayOf(
    '/', '\\', ':', '*', '?', '"', '<', '>', '|', '\n', '\r',
)

/**
 * Whether a folder can be called this.
 *
 * A folder name is one path segment, so anything that would make it two is out.
 * So is a name beginning with a dot: that is how a folder is hidden from every
 * gallery on the phone, and hiding is something this app does deliberately and
 * reversibly elsewhere, not by accident here.
 */
fun isUsableFolderName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.startsWith('.')) return false
    return trimmed.none { it in FORBIDDEN_IN_NAME }
}

/**
 * The path a folder's files move to when the folder is renamed.
 *
 * Only the last part of the path changes: renaming DCIM/Camera to "Holiday"
 * makes DCIM/Holiday, not Holiday.
 *
 * Null for a name that will not do, and null for a folder sitting directly in
 * one of Android's own top-level directories — DCIM, Pictures and the rest are
 * the system's furniture, and renaming one would move every photograph on the
 * phone somewhere no other app thinks to look.
 */
fun renamedFolderPath(relativePath: String, newName: String): String? {
    if (!isUsableFolderName(newName) || !isRenamableFolder(relativePath)) return null
    val segments = relativePath.split('/').filter { it.isNotBlank() }
    return (segments.dropLast(1) + newName.trim()).joinToString("/", postfix = "/")
}

/**
 * Whether a folder is one the app may rename at all.
 *
 * Anything sitting directly in one of Android's own top-level directories is
 * not: see [renamedFolderPath]. Asked separately so the albums screen can leave
 * the choice off the card rather than offering it and then refusing.
 */
fun isRenamableFolder(relativePath: String): Boolean =
    relativePath.split('/').count { it.isNotBlank() } >= 2

/** The path of a folder made from inside the app, or null if the name will not do. */
fun newFolderPath(name: String): String? =
    if (isUsableFolderName(name)) "$NEW_FOLDER_PARENT/${name.trim()}/" else null

/**
 * Whether a file is already where it is being sent.
 *
 * Moving a photograph into the folder it is in is not an error, but it is not
 * work either, and counting it as done would report a move that never happened.
 */
fun isAlreadyIn(item: MediaItem, relativePath: String): Boolean =
    item.relativePath.trim('/').equals(relativePath.trim('/'), ignoreCase = true)

/**
 * How a batch of files fared.
 *
 * Counted rather than reduced to worked-or-did-not, because a move of forty
 * photographs can half succeed, and "moved 38 of 40" is the only honest thing to
 * say about it. [reason] carries the last thing that went wrong, in the system's
 * own words: a batch usually fails for one reason, and one reason is what fits
 * in a message anybody will read.
 */
data class BatchOutcome(val done: Int, val failed: Int, val reason: String = "") {
    val whole: Boolean get() = failed == 0
}

/**
 * Moves files into another folder.
 *
 * A move in MediaStore is a change of one column: the row keeps its id, its
 * date and its favourite flag, and every album, cover and selection that refers
 * to it goes on referring to the same photograph. Copying the bytes to the new
 * place and deleting the old ones would produce a different photograph that
 * merely looks the same.
 *
 * The caller must already hold write permission for these files — see
 * [writeRequestFor]. Without it the system throws on the first one and the
 * whole batch reports that reason.
 */
suspend fun Context.moveItemsTo(items: List<MediaItem>, relativePath: String): BatchOutcome =
    withContext(Dispatchers.IO) {
        var done = 0
        var failed = 0
        var reason = ""

        for (item in items) {
            if (isAlreadyIn(item, relativePath)) continue
            val moved = runCatching {
                contentResolver.update(
                    item.contentUri(),
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    },
                    null,
                    null,
                ) > 0
            }.onFailure { Log.w(TAG, "could not move ${item.name}", it) }

            if (moved.getOrDefault(false)) {
                done++
            } else {
                failed++
                reason = moved.exceptionOrNull()?.describe() ?: "the library refused the move"
            }
        }
        BatchOutcome(done, failed, reason)
    }

/**
 * Copies files into another folder.
 *
 * Byte for byte, so everything written inside the file — the day it was taken,
 * the camera, the place — travels with it. The row is told the day as well,
 * because a copy that sorts to today is a copy nobody can find again, and the
 * library believes the row before it believes the file.
 *
 * Nothing is asked of the system here: these are new files the app makes, and
 * an app may always make a file of its own.
 */
suspend fun Context.copyItemsTo(items: List<MediaItem>, relativePath: String): BatchOutcome =
    withContext(Dispatchers.IO) {
        var done = 0
        var failed = 0
        var reason = ""

        for (item in items) {
            val result = runCatching { copyOne(item, relativePath) }
                .onFailure { Log.w(TAG, "could not copy ${item.name}", it) }
                .getOrElse { it.describe() }

            if (result == null) done++ else { failed++; reason = result }
        }
        BatchOutcome(done, failed, reason)
    }

/** Copies one file, and says what stopped it if anything did. */
private fun Context.copyOne(item: MediaItem, relativePath: String): String? {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        if (item.takenAt > 0) put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
        // Nothing sees a half-written photograph: the row is published below,
        // once every byte of it is there.
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection = if (item.isVideo) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    val uri = contentResolver.insert(collection, values)
        ?: return "the library refused a new file"

    val written = runCatching {
        val source = contentResolver.openInputStream(item.contentUri())
            ?: return@runCatching "the original would not open"
        val sink = contentResolver.openOutputStream(uri)
            ?: return@runCatching "the copy would not open for writing"
        source.use { input -> sink.use { output -> input.copyTo(output) } }
        null
    }.getOrElse { it.describe() }

    if (written != null) {
        // A row with nothing behind it is worse than no row: it would show as a
        // broken tile for as long as anybody left it there.
        runCatching { contentResolver.delete(uri, null, null) }
        return written
    }

    runCatching {
        contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
    }
    return null
}
