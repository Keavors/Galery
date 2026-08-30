package com.keavors.gallery.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "vault")

private val VAULT_JSON = stringPreferencesKey("vault_json")

/**
 * Files taken out of the library entirely.
 *
 * Hiding here is not a flag: the bytes are moved into the app's own storage,
 * where MediaStore cannot see them and neither can any other gallery, file
 * manager or backup. That is the only kind of hiding that actually hides.
 *
 * The cost is that this is the only copy. Uninstalling the app takes the vault
 * with it, which is why the screen that offers this says so and why putting a
 * file back is one tap.
 */
class VaultStore(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "vault").apply { mkdirs() }

    val entries: Flow<List<VaultEntry>> = context.vaultDataStore.data
        .map { decodeVault(it[VAULT_JSON]) }

    private suspend fun update(transform: (List<VaultEntry>) -> List<VaultEntry>) {
        context.vaultDataStore.edit { prefs ->
            prefs[VAULT_JSON] = encodeVault(transform(decodeVault(prefs[VAULT_JSON])))
        }
    }

    /** The vault as the viewer and the grid understand it. */
    fun asMediaItem(entry: VaultEntry): MediaItem = MediaItem(
        id = -entry.id,
        uri = Uri.fromFile(File(directory, entry.fileName)).toString(),
        name = entry.displayName,
        mimeType = entry.mimeType,
        isVideo = entry.isVideo,
        sizeBytes = entry.sizeBytes,
        width = 0,
        height = 0,
        durationMs = 0,
        takenAt = entry.takenAt,
        addedAt = entry.takenAt,
        modifiedAt = entry.takenAt,
        bucketId = VAULT_BUCKET,
        bucketName = "",
        relativePath = entry.relativePath,
        isFavorite = false,
        orientation = 0,
        expiresAt = 0,
        isPrivate = true,
    )

    /**
     * Copies a file into the vault and records where it came from.
     *
     * Copy first, record second, and only then may the caller delete the
     * original. Any other order risks the one outcome that cannot be undone: the
     * original gone and nothing kept in its place.
     *
     * Returns the entry, or null if the copy did not come through whole.
     */
    suspend fun take(item: MediaItem): VaultEntry? = withContext(Dispatchers.IO) {
        if (!canBeHidden(item)) return@withContext null

        val id = System.nanoTime()
        val target = File(directory, vaultFileName(id))

        val copied = runCatching {
            context.contentResolver.openInputStream(item.contentUri())?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            // A short file means the read was cut off. Better to fail now than
            // to delete the original and find out later.
            item.sizeBytes <= 0 || target.length() == item.sizeBytes
        }.getOrElse { false }

        if (!copied) {
            target.delete()
            return@withContext null
        }

        val entry = VaultEntry(
            id = id,
            fileName = target.name,
            displayName = item.name,
            mimeType = item.mimeType,
            isVideo = item.isVideo,
            sizeBytes = target.length(),
            takenAt = item.takenAt,
            relativePath = item.relativePath,
        )
        update { it + entry }
        entry
    }

    /** Undoes a [take] that was never finished, because the delete was refused. */
    suspend fun discard(entry: VaultEntry) = withContext(Dispatchers.IO) {
        File(directory, entry.fileName).delete()
        update { list -> list.filterNot { it.id == entry.id } }
    }

    /**
     * Puts a file back where it came from.
     *
     * The new row is created pending, filled, and only then published, so no
     * other app ever sees a half-written photo appear in the library.
     */
    suspend fun restore(entry: VaultEntry): Boolean = withContext(Dispatchers.IO) {
        val source = File(directory, entry.fileName)
        if (!source.exists()) {
            // The record outlived its file. Dropping it is the only honest
            // answer, and leaving it would offer a photo that cannot be opened.
            update { list -> list.filterNot { it.id == entry.id } }
            Log.w(TAG, "vault file missing: " + entry.displayName)
            return@withContext false
        }

        val collection = if (entry.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        // Twice, because the second attempt drops everything optional. Coming
        // back to the wrong folder with the wrong date beats not coming back:
        // MediaStore can refuse a path or a column, and the file is the point.
        val uri = insertRow(collection, entry, full = true)
            ?: insertRow(collection, entry, full = false)
            ?: return@withContext false

        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.onFailure { Log.w(TAG, "restore write failed", it) }.getOrElse { false }

        if (!written) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            return@withContext false
        }

        runCatching {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }.onFailure { Log.w(TAG, "publishing restored file failed", it) }

        source.delete()
        update { list -> list.filterNot { it.id == entry.id } }
        true
    }

    /**
     * Creates the row the file will be written into.
     *
     * [full] adds the remembered folder and date. Without them the file still
     * comes back, into the standard folder — which is why the caller tries again
     * without them rather than giving up.
     */
    private fun insertRow(collection: Uri, entry: VaultEntry, full: Boolean): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, entry.displayName)
            if (entry.mimeType.isNotBlank()) {
                put(MediaStore.MediaColumns.MIME_TYPE, entry.mimeType)
            }
            if (full) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, restorePathFor(entry))
                if (entry.takenAt > 0) put(MediaStore.MediaColumns.DATE_TAKEN, entry.takenAt)
            }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return runCatching { context.contentResolver.insert(collection, values) }
            .onFailure { Log.w(TAG, "restore insert failed, full=" + full, it) }
            .getOrNull()
    }

    /** Total bytes held, for telling someone what they would lose. */
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        entries.first().sumOf { File(directory, it.fileName).length() }
    }

    companion object {
        /** Not a real MediaStore bucket; nothing in the library can collide with it. */
        const val VAULT_BUCKET = -100L

        private const val TAG = "VaultStore"
    }
}
