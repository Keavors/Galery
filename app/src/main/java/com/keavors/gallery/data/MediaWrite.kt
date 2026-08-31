package com.keavors.gallery.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Changing someone else's media.
 *
 * Every one of these goes through a system request rather than a direct write:
 * the files belong to the camera, to a messenger, to whatever put them there,
 * and Android will not let one app quietly alter another's. With the media
 * management access granted the requests are carried out without a dialog; with
 * it withheld, the system asks each time. Either way the code is the same.
 */
class MediaWriter(
    private val context: Context,
    private val managesMedia: Boolean,
    private val launch: (IntentSenderRequest) -> Unit,
) {
    /**
     * Whether the app has to ask before deleting, because the system will not.
     *
     * Without the media management access Android puts up its own confirmation
     * for every trash and delete, and asking first as well means two dialogs in
     * a row saying the same thing. With that access granted the system stays
     * quiet — and then something has to ask, or a tap on the bin would delete
     * with no way back.
     */
    val needsOwnConfirmation: Boolean get() = managesMedia

    fun setFavorite(items: List<MediaItem>, favorite: Boolean) {
        if (items.isEmpty()) return
        launch(
            MediaStore.createFavoriteRequest(
                context.contentResolver,
                items.map { it.contentUri() },
                favorite,
            ).request()
        )
    }

    /** Into the system trash, or back out of it. */
    fun setTrashed(items: List<MediaItem>, trashed: Boolean) {
        if (items.isEmpty()) return
        launch(
            MediaStore.createTrashRequest(
                context.contentResolver,
                items.map { it.contentUri() },
                trashed,
            ).request()
        )
    }

    /** Gone for good. There is no undoing this one. */
    fun deleteForever(items: List<MediaItem>) {
        if (items.isEmpty()) return
        launch(
            MediaStore.createDeleteRequest(
                context.contentResolver,
                items.map { it.contentUri() },
            ).request()
        )
    }
}

/**
 * Puts the day a file was taken back, after its bytes have been replaced.
 *
 * Writing over a file moves its modification date to now, and for most videos —
 * and for screenshots, and for anything downloaded — that date is the only one
 * the library has. A camera writes the day into the file itself; a screen
 * recorder does not. So without this, editing a video quietly moves it to
 * today: to the top of the timeline, away from the day it belongs to and away
 * from everything it was taken alongside.
 *
 * The taken date is written rather than the modification date because that is
 * the one that can be written at all — the modification date belongs to the
 * file system — and because it is the one every gallery prefers when it is
 * there, this one included.
 */
suspend fun Context.keepTakenAt(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
    // Nothing to put back: a file the library never had a date for is one this
    // cannot invent one for either.
    if (item.takenAt <= 0L) return@withContext true

    // Written more than once, and read back each time, because writing it once
    // was not enough. Replacing a file's bytes makes the library look at that
    // file again, on its own schedule — and a look that lands after the date
    // has been put back takes it away again. So: write, wait, ask what it says
    // now, and write again if the library has changed its mind. What comes back
    // from here is what the library actually holds, not what it was told.
    repeat(DATE_ATTEMPTS) {
        writeTakenAt(item, item.takenAt)
        delay(DATE_SETTLE_MS)
        if (storedTakenAt(item) == item.takenAt) return@withContext true
    }
    false
}

/** How many times the date is put back before giving up and saying so. */
private const val DATE_ATTEMPTS = 3

/** How long the library is given to change its mind, in milliseconds. */
private const val DATE_SETTLE_MS = 250L

private fun Context.writeTakenAt(item: MediaItem, takenAt: Long) {
    runCatching {
        contentResolver.update(
            item.contentUri(),
            ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, takenAt) },
            null,
            null,
        )
    }.onFailure { Log.w("MediaWrite", "could not put the date back", it) }
}

/** What the library says the date is now, or null if it will not say. */
private fun Context.storedTakenAt(item: MediaItem): Long? = runCatching {
    contentResolver.query(
        item.contentUri(),
        arrayOf(MediaStore.MediaColumns.DATE_TAKEN),
        null,
        null,
        null,
    )?.use { row ->
        if (row.moveToFirst() && !row.isNull(0)) row.getLong(0) else null
    }
}.getOrNull()

/**
 * A permanent-delete request the caller launches itself.
 *
 * Used where the outcome matters: moving a file into the vault has to know
 * whether the original really went, so it can put the copy back if it did not.
 */
fun deleteRequestFor(context: Context, items: List<MediaItem>): IntentSenderRequest =
    MediaStore.createDeleteRequest(
        context.contentResolver,
        // Only files MediaStore knows about. It throws on anything else, and a
        // private file handed to it would take the app down with it.
        items.filterNot { it.isPrivate }.map { it.contentUri() },
    ).request()

private fun android.app.PendingIntent.request(): IntentSenderRequest =
    IntentSenderRequest.Builder(intentSender).build()

/** A day, in milliseconds. */
private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Days left before the system deletes a trashed file for good.
 *
 * Rounded up, because a file with eight hours left has a day left as far as
 * anyone reading it is concerned, and saying zero would imply it is already
 * gone. Returns null when the file carries no expiry, which is every file that
 * is not in the trash.
 */
fun daysUntilExpiry(expiresAt: Long, now: Long): Int? {
    if (expiresAt <= 0) return null
    val remaining = expiresAt - now
    if (remaining <= 0) return 0
    return ceil(remaining.toDouble() / DAY_MS).toInt()
}
