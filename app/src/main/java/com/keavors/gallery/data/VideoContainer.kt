package com.keavors.gallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "VideoContainer"

/** How much of a file has to be read to know what kind of file it is. */
const val CONTAINER_SIGNATURE_BYTES = 16

/**
 * Whether this file needs the player that opens everything.
 *
 * Decided by what is at the front of the file rather than by its name or by what
 * the library calls it, and this case is exactly why: the six files that started
 * all this are RealMedia named .mp4, and both the name and the library agreed
 * they were MP4. The bytes did not.
 *
 * Only the containers Media3 genuinely cannot take apart are listed. Everything
 * else — MP4, Matroska, WebM, AVI, MPEG streams, Ogg — goes to ExoPlayer, which
 * is better integrated here and cheaper to run; the second engine is a fallback,
 * not a replacement.
 */
fun needsFullPlayer(head: ByteArray): Boolean =
    UNSUPPORTED_CONTAINERS.any { head.startsWith(it) }

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) if (this[index] != prefix[index]) return false
    return true
}

/** ".RMF" — RealMedia, which is also what RMVB is. */
private val REALMEDIA = byteArrayOf(0x2E, 0x52, 0x4D, 0x46)

/** The ASF header guid, which is what WMV and WMA are wrapped in. */
private val ASF = byteArrayOf(0x30, 0x26.toByte(), 0xB2.toByte(), 0x75)

/** "FWS"/"CWS" — Flash, which still turns up in old download folders. */
private val FLASH_UNCOMPRESSED = byteArrayOf(0x46, 0x57, 0x53)
private val FLASH_COMPRESSED = byteArrayOf(0x43, 0x57, 0x53)

private val UNSUPPORTED_CONTAINERS = listOf(REALMEDIA, ASF, FLASH_UNCOMPRESSED, FLASH_COMPRESSED)

/**
 * Reads the few bytes that say which player a file needs.
 *
 * Sixteen bytes off the front, on a background thread, once per video opened.
 * A file that cannot be read at all is left to ExoPlayer: it will report the
 * failure through the ordinary path rather than being quietly handed to a
 * second engine that would fail at it too.
 */
suspend fun Context.videoNeedsFullPlayer(item: MediaItem): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openInputStream(item.contentUri())?.use { stream ->
                val head = ByteArray(CONTAINER_SIGNATURE_BYTES)
                val read = stream.read(head)
                read > 0 && needsFullPlayer(head.copyOf(read))
            } ?: false
        }.onFailure { Log.w(TAG, "could not look at ${item.name}", it) }.getOrDefault(false)
    }
