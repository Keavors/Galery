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

    /** Returns the new album so the caller can open it straight away. */
    suspend fun createUserAlbum(name: String, members: Set<Long> = emptySet()): UserAlbum {
        // Ids come from the clock rather than a counter: nothing else has to be
        // read to mint one, and two albums cannot be made in the same
        // millisecond by one pair of hands.
        val album = UserAlbum(id = System.currentTimeMillis(), name = name, memberIds = members)
        update { it.copy(userAlbums = it.userAlbums + album) }
        return album
    }

    suspend fun renameUserAlbum(albumId: Long, name: String) = update { prefs ->
        prefs.copy(
            userAlbums = prefs.userAlbums.map {
                if (it.id == albumId) it.copy(name = name) else it
            }
        )
    }

    suspend fun deleteUserAlbum(albumId: Long) = update { prefs ->
        val key = AlbumSource.User(albumId).key
        prefs.copy(
            userAlbums = prefs.userAlbums.filterNot { it.id == albumId },
            // The album is gone, so its opinions go with it rather than lingering
            // to be inherited by whatever id happens to come round again.
            pinned = prefs.pinned - key,
            hidden = prefs.hidden - key,
            covers = prefs.covers - key,
        )
    }

    suspend fun addToUserAlbum(albumId: Long, itemIds: Set<Long>) = update { prefs ->
        prefs.copy(
            userAlbums = prefs.userAlbums.map {
                if (it.id == albumId) it.copy(memberIds = it.memberIds + itemIds) else it
            }
        )
    }

    suspend fun removeFromUserAlbum(albumId: Long, itemIds: Set<Long>) = update { prefs ->
        prefs.copy(
            userAlbums = prefs.userAlbums.map {
                if (it.id == albumId) it.copy(memberIds = it.memberIds - itemIds) else it
            }
        )
    }
}
