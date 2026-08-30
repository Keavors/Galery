package com.keavors.gallery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val SETTINGS_JSON = stringPreferencesKey("settings_json")

/**
 * Where the settings live.
 *
 * One JSON document, like the album preferences: writing them and exporting them
 * to a file become the same operation, and adding a field needs no migration —
 * an older document simply has no opinion about it.
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<GallerySettings> = context.settingsDataStore.data
        .map { decodeSettings(it[SETTINGS_JSON]) }

    suspend fun update(transform: (GallerySettings) -> GallerySettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[SETTINGS_JSON] = encodeSettings(transform(decodeSettings(prefs[SETTINGS_JSON])))
        }
    }

    /** Puts everything back the way it came out of the box. */
    suspend fun reset() = update { GallerySettings() }

    /** The document itself, for writing to a file. */
    suspend fun export(): String = encodeSettings(settings.first())

    /**
     * Replaces everything with what a file says.
     *
     * Unreadable input leaves the settings alone rather than resetting them: a
     * mistyped file should not cost what is already configured.
     */
    suspend fun import(json: String): Boolean {
        if (json.isBlank()) return false
        val decoded = decodeSettings(json)
        if (decoded == GallerySettings() && json.trim() != encodeSettings(GallerySettings())) {
            return false
        }
        update { decoded }
        return true
    }
}
