package com.keavors.gallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(name = "watched")

private val WATCHED_JSON = stringPreferencesKey("watched_json")

/**
 * Where each video was left off.
 *
 * Kept in insertion order so that forgetting the oldest is possible: this is a
 * convenience, not a record, and it must not grow without end on a phone with
 * twelve hundred videos on it.
 */
data class WatchPositions(val at: Map<Long, Long> = emptyMap()) {
    fun of(id: Long): Long? = at[id]
}

/** Below this a video has barely started, and starting it again is no hardship. */
private const val TOO_EARLY_MS = 10_000L

/** Within this of the end it has been watched, and starting again is the point. */
private const val TOO_LATE_MS = 15_000L

/** How many videos are remembered before the oldest is let go. */
private const val REMEMBERED = 200

/**
 * Files away where a video got to, or forgets it.
 *
 * Both halves matter. A video stopped ten seconds in is not a video anybody
 * needs taken back to; a video watched to the end is one somebody wants to start
 * again, and offering to resume it fifteen seconds from the credits is a way of
 * never seeing the beginning. Between those two it is remembered.
 */
fun WatchPositions.remembering(id: Long, positionMs: Long, durationMs: Long): WatchPositions {
    val forget = durationMs <= 0 ||
        positionMs < TOO_EARLY_MS ||
        positionMs > durationMs - TOO_LATE_MS

    if (forget) {
        if (id !in at) return this
        return WatchPositions(at - id)
    }

    // Rebuilt rather than mutated so the id moves to the end of the queue: the
    // one just watched is the last that should be forgotten.
    val kept = LinkedHashMap<Long, Long>(at.size + 1)
    kept.putAll(at - id)
    kept[id] = positionMs
    while (kept.size > REMEMBERED) {
        val oldest = kept.keys.first()
        kept.remove(oldest)
    }
    return WatchPositions(kept)
}

fun encodeWatchPositions(positions: WatchPositions): String = JSONObject().apply {
    positions.at.forEach { (id, at) -> put(id.toString(), at) }
}.toString()

/** Anything unreadable is no position at all, which costs a video its place. */
fun decodeWatchPositions(json: String?): WatchPositions {
    if (json.isNullOrBlank()) return WatchPositions()
    return runCatching {
        val root = JSONObject(json)
        val at = LinkedHashMap<Long, Long>()
        root.keys().forEach { key ->
            val id = key.toLongOrNull() ?: return@forEach
            at[id] = root.optLong(key)
        }
        WatchPositions(at)
    }.getOrElse { WatchPositions() }
}

/** The store behind [WatchPositions]. One JSON document, like everything else here. */
class WatchStore(private val context: Context) {

    val positions: Flow<WatchPositions> = context.watchDataStore.data
        .map { decodeWatchPositions(it[WATCHED_JSON]) }

    suspend fun remember(id: Long, positionMs: Long, durationMs: Long) {
        context.watchDataStore.edit { prefs ->
            val current = decodeWatchPositions(prefs[WATCHED_JSON])
            val updated = current.remembering(id, positionMs, durationMs)
            if (updated != current) prefs[WATCHED_JSON] = encodeWatchPositions(updated)
        }
    }
}
