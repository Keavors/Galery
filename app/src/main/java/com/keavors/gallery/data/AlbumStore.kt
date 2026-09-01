package com.keavors.gallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.albumDataStore: DataStore<Preferences> by preferencesDataStore(name = "albums")

private val ALBUMS_JSON = stringPreferencesKey("albums_json")

/**
 * What the app decided about albums, kept between runs.
 *
 * Everything else on the albums screen is a fact about the phone — which folders
 * exist, what is in them, what is favourited. This holds only the opinions:
 * pinned, hidden, chosen covers, and albums that exist nowhere but here.
 *
 * One JSON document under one key. The whole thing is a few hundred bytes and is
 * read into memory whole, so anything more structured would be machinery around
 * a string.
 */
class AlbumStore(private val context: Context) {

    val preferences: Flow<AlbumPreferences> = context.albumDataStore.data
        .map { decodeAlbumPreferences(it[ALBUMS_JSON]) }

    private suspend fun update(transform: (AlbumPreferences) -> AlbumPreferences) {
        context.albumDataStore.edit { prefs ->
            val current = decodeAlbumPreferences(prefs[ALBUMS_JSON])
            prefs[ALBUMS_JSON] = encodeAlbumPreferences(transform(current))
        }
    }

    suspend fun togglePinned(source: AlbumSource) = update { prefs ->
        val key = source.key
        prefs.copy(pinned = if (key in prefs.pinned) prefs.pinned - key else prefs.pinned + key)
    }

    suspend fun setHidden(source: AlbumSource, hidden: Boolean) = update { prefs ->
        val key = source.key
        prefs.copy(hidden = if (hidden) prefs.hidden + key else prefs.hidden - key)
    }

    suspend fun setCover(source: AlbumSource, itemId: Long?) = update { prefs ->
        val key = source.key
        prefs.copy(
            covers = if (itemId == null) prefs.covers - key else prefs.covers + (key to itemId)
        )
    }

    /** Moves what was decided about one album onto another. See [movedTo]. */
    suspend fun moveOpinions(from: AlbumSource, to: AlbumSource) = update { it.movedTo(from, to) }
}
