package com.keavors.gallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MotionPhoto"

/** Samsung writes this immediately before the video it hides in a photograph. */
private val SAMSUNG_MARKER = "MotionPhoto_Data".toByteArray(Charsets.US_ASCII)

/** Every MP4 begins with a box length and then these four bytes. */
private val FTYP = "ftyp".toByteArray(Charsets.US_ASCII)

/**
 * The largest photograph worth searching for a video inside.
 *
 * A motion photo is a still and a second or two of video in one file, which puts
 * it in the low tens of megabytes at the very most. Reading a file larger than
 * this into memory to look for something that cannot be there is how a viewer
 * runs out of memory on a long press.
 */
const val MOTION_SEARCH_LIMIT_BYTES = 64L * 1024 * 1024

/**
 * Where the video starts inside a motion photo, or null if there is none.
 *
 * Two makers, two conventions, and no documentation for either. Samsung writes a
 * plain marker before the video, which is unambiguous when it is there. Google
 * and everyone else simply append the MP4, and an MP4 announces itself with a
 * length and the letters "ftyp" — so the last such signature past the start of
 * the file is where the video begins.
 *
 * The last one rather than the first, because a photograph may legitimately
 * contain those four letters in its pixels, and the video is at the end by
 * construction. Nothing is guessed at beyond that: if neither sign is there, the
 * file is an ordinary photograph and is treated as one.
 */
fun motionVideoStart(bytes: ByteArray): Int? {
    val marked = lastIndexOf(bytes, SAMSUNG_MARKER)
    if (marked >= 0) {
        val start = marked + SAMSUNG_MARKER.size
        return start.takeIf { it < bytes.size }
    }

    var at = lastIndexOf(bytes, FTYP)
    while (at > 4) {
        // The four bytes before "ftyp" are the box length, and a real one is
        // small: this is what tells a box apart from the same letters occurring
        // inside compressed pixels.
        val length = boxLengthAt(bytes, at - 4)
        if (length in 8..1024) return at - 4
        at = lastIndexOf(bytes, FTYP, before = at)
    }
    return null
}

private fun boxLengthAt(bytes: ByteArray, offset: Int): Int {
    if (offset < 0 || offset + 4 > bytes.size) return -1
    var value = 0
    for (index in offset until offset + 4) {
        value = (value shl 8) or (bytes[index].toInt() and 0xFF)
    }
    return value
}

/** The last place [needle] appears in [haystack] before [before], or -1. */
private fun lastIndexOf(haystack: ByteArray, needle: ByteArray, before: Int = haystack.size): Int {
    outer@ for (start in (minOf(before, haystack.size) - needle.size) downTo 0) {
        for (index in needle.indices) {
            if (haystack[start + index] != needle[index]) continue@outer
        }
        return start
    }
    return -1
}

/**
 * Pulls the video out of a motion photo and leaves it in the cache.
 *
 * Written to a file rather than handed over as bytes because the player wants
 * something it can seek in, and kept because the second long press on the same
 * photograph should cost nothing. The cache is the right place for it: the file
 * is a copy of something that is already on the phone, and losing it costs one
 * extraction.
 *
 * Returns null when the photograph has no video in it, which is almost all of
 * them, and that is not a failure worth telling anybody about.
 */
suspend fun Context.motionVideoOf(item: MediaItem): File? = withContext(Dispatchers.IO) {
    if (item.isVideo || item.sizeBytes > MOTION_SEARCH_LIMIT_BYTES) return@withContext null

    val kept = File(cacheDir, "motion/${item.id}.mp4")
    if (kept.isFile && kept.length() > 0) return@withContext kept

    val bytes = runCatching {
        contentResolver.openInputStream(item.contentUri())?.use { it.readBytes() }
    }.onFailure { Log.w(TAG, "could not read ${item.name}", it) }.getOrNull()
        ?: return@withContext null

    val start = motionVideoStart(bytes) ?: return@withContext null

    runCatching {
        kept.parentFile?.mkdirs()
        kept.outputStream().use { it.write(bytes, start, bytes.size - start) }
        kept
    }.onFailure { Log.w(TAG, "could not keep the video of ${item.name}", it) }.getOrNull()
}
