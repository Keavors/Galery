package com.keavors.gallery.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * Tells the library the day a file was taken, after its bytes have been
 * replaced.
 *
 * Belt and braces, deliberately. The day is already written inside the file
 * where a scan would find it — but a scan is the library's business and happens
 * when the library decides, and a photograph that has quietly moved to today has
 * already been lost among things it has nothing to do with. So the row is told
 * as well, and the two agree, so whichever of them is believed is right.
 *
 * The taken date rather than the modified date, because the modified date
 * belongs to the file system and because the taken date is the one every gallery
 * prefers, this one included.
 */
suspend fun Context.keepTakenAt(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
    if (item.takenAt <= 0L) return@withContext true

    // Written more than once, and read back each time. Replacing a file's bytes
    // makes the library look at that file again on a schedule of its own, and a
    // look that lands after this has written the date can undo it.
    repeat(DATE_ATTEMPTS) {
        runCatching {
            contentResolver.update(
                item.contentUri(),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
                },
                null,
                null,
            )
        }.onFailure { Log.w("MediaWrite", "could not put the date back", it) }

        delay(DATE_SETTLE_MS)
        val stored = runCatching {
            contentResolver.query(
                item.contentUri(),
                arrayOf(MediaStore.MediaColumns.DATE_TAKEN),
                null,
                null,
                null,
            )?.use { row -> if (row.moveToFirst() && !row.isNull(0)) row.getLong(0) else null }
        }.getOrNull()

        if (stored == item.takenAt) return@withContext true
    }
    false
}

/** How many times the date is put back before leaving it to the file. */
private const val DATE_ATTEMPTS = 3

/** How long the library is given to change its mind, in milliseconds. */
private const val DATE_SETTLE_MS = 250L

/**
 * Goes on asserting the day after [keepTakenAt] has gone home.
 *
 * Replacing a file's bytes makes the library read it again, and that reading
 * lands whenever the library pleases — sometimes after every attempt above has
 * finished and verified. This watches from a distance for a few seconds more
 * and puts the day back if a late look took it away. It holds the application
 * context rather than the caller's, so it cannot keep a screen alive.
 */
fun Context.guardTakenAt(item: MediaItem) {
    if (item.takenAt <= 0L) return
    val app = applicationContext
    dateGuardScope.launch {
        GUARD_LOOKS_MS.forEach { pause ->
            delay(pause)
            val stored = runCatching {
                app.contentResolver.query(
                    item.contentUri(),
                    arrayOf(MediaStore.MediaColumns.DATE_TAKEN),
                    null,
                    null,
                    null,
                )?.use { row ->
                    if (row.moveToFirst() && !row.isNull(0)) row.getLong(0) else null
                }
            }.getOrNull()

            if (stored != item.takenAt) {
                runCatching {
                    app.contentResolver.update(
                        item.contentUri(),
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
                        },
                        null,
                        null,
                    )
                }.onFailure { Log.w("MediaWrite", "the guard could not put the date back", it) }
            }
        }
    }
}

/** When the guard looks again, counted from the save. */
private val GUARD_LOOKS_MS = listOf(1_000L, 3_000L, 8_000L)

/** Off the save's own coroutine, so the guard outlives the screen that saved. */
private val dateGuardScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
