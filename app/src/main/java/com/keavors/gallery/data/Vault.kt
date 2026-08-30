package com.keavors.gallery.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A file that has been taken out of the library and put away.
 *
 * Everything here is what the file used to be, because that is what putting it
 * back needs. MediaStore knows nothing about it any more: the point of the vault
 * is that no other app can see it, and the price is that this record is the only
 * thing that remembers where it came from.
 */
data class VaultEntry(
    val id: Long,
    /** File name inside the vault directory. Never the original name. */
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val takenAt: Long,
    /** Where it was, so it can go back there. */
    val relativePath: String,
)

fun encodeVault(entries: List<VaultEntry>): String {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(
            JSONObject()
                .put("id", entry.id)
                .put("file", entry.fileName)
                .put("name", entry.displayName)
                .put("mime", entry.mimeType)
                .put("video", entry.isVideo)
                .put("size", entry.sizeBytes)
                .put("taken", entry.takenAt)
                .put("path", entry.relativePath)
        )
    }
    return array.toString()
}

/**
 * Reads the index.
 *
 * An entry that cannot be read is dropped rather than taking the rest with it.
 * The files themselves are still on disk either way, and losing one record is
 * better than losing the list.
 */
fun decodeVault(json: String?): List<VaultEntry> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val fileName = o.optString("file").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            VaultEntry(
                id = o.optLong("id"),
                fileName = fileName,
                displayName = o.optString("name"),
                mimeType = o.optString("mime"),
                isVideo = o.optBoolean("video"),
                sizeBytes = o.optLong("size"),
                takenAt = o.optLong("taken"),
                relativePath = o.optString("path"),
            )
        }
    }.getOrElse { emptyList() }
}

/**
 * The name a file gets inside the vault.
 *
 * Not the original: a directory listing of the vault should say nothing about
 * what is in it, and two photos called IMG_0001.jpg from different folders would
 * otherwise collide.
 */
fun vaultFileName(id: Long): String = "v$id"

/**
 * Where a restored file should be put back.
 *
 * Falls back to the standard pictures or movies folder when the original path is
 * missing or absolute — a path from another volume would either fail the insert
 * or, worse, put the file somewhere unexpected.
 */
fun restorePathFor(entry: VaultEntry): String {
    val path = entry.relativePath.trim().trim('/')
    val usable = path.isNotEmpty() && !path.startsWith(".") && !entry.relativePath.startsWith("/")
    return if (usable) "$path/" else if (entry.isVideo) "Movies/" else "Pictures/"
}

/**
 * Whether a file can be moved into the vault.
 *
 * A file already in it cannot: hiding works by copying the file and then asking
 * the system to delete the original, and a vaulted file has no MediaStore row to
 * delete. The request refuses it outright — which is how the copy came to be
 * made and the original left in place, twice over.
 */
fun canBeHidden(item: MediaItem): Boolean = !item.isPrivate
