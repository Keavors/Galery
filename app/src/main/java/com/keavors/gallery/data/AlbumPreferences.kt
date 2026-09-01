package com.keavors.gallery.data

import org.json.JSONArray
import org.json.JSONObject

/** Everything the albums screen remembers between runs. */
data class AlbumPreferences(
    val pinned: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    /** Album key to the id of the photo chosen as its cover. */
    val covers: Map<String, Long> = emptyMap(),
) {
    fun isPinned(source: AlbumSource) = source.key in pinned
    fun isHidden(source: AlbumSource) = source.key in hidden
    fun coverId(source: AlbumSource): Long? = covers[source.key]
}

/**
 * Carries what was decided about one album over to another.
 *
 * For renaming a folder, which is not a rename at all as far as the phone is
 * concerned: a folder's id is worked out from its path, so a renamed folder is a
 * folder nothing has ever heard of, and pinning it, hiding it or choosing its
 * cover would otherwise have to be done again.
 */
fun AlbumPreferences.movedTo(from: AlbumSource, to: AlbumSource): AlbumPreferences {
    val old = from.key
    val new = to.key
    if (old == new) return this
    return copy(
        pinned = if (old in pinned) pinned - old + new else pinned,
        hidden = if (old in hidden) hidden - old + new else hidden,
        covers = covers[old]?.let { covers - old + (new to it) } ?: covers,
    )
}

/**
 * A stable name for an album.
 *
 * Folders are keyed by their bucket id, which Android works out from the path —
 * so a folder renamed anywhere, here or in a file manager, arrives under a new
 * key. Renaming from inside the app carries the old key's opinions across; a
 * rename done elsewhere is a folder this app has never seen before, and there is
 * nothing to be done about that.
 */
val AlbumSource.key: String
    get() = when (this) {
        is AlbumSource.Folder -> "folder:$bucketId"
        AlbumSource.Favourites -> "favourites"
        AlbumSource.Videos -> "videos"
        AlbumSource.Vault -> "vault"
    }

// ------------------------------------------------------------- json ---------

/*
 * Stored as one JSON document rather than in a database. What is kept here is a
 * few small sets of ids that are read into memory whole and never queried; a
 * schema, migrations and code generation would all be ceremony around a string.
 */

private const val KEY_PINNED = "pinned"
private const val KEY_HIDDEN = "hidden"
private const val KEY_COVERS = "covers"

fun encodeAlbumPreferences(prefs: AlbumPreferences): String {
    val root = JSONObject()
    root.put(KEY_PINNED, JSONArray(prefs.pinned.toList()))
    root.put(KEY_HIDDEN, JSONArray(prefs.hidden.toList()))

    val covers = JSONObject()
    prefs.covers.forEach { (key, id) -> covers.put(key, id) }
    root.put(KEY_COVERS, covers)
    return root.toString()
}

/**
 * Reads the document back.
 *
 * Anything unreadable produces the empty preferences rather than an exception:
 * a corrupt file should cost the pinned albums, not the ability to open the app.
 */
fun decodeAlbumPreferences(json: String?): AlbumPreferences {
    if (json.isNullOrBlank()) return AlbumPreferences()
    return runCatching {
        val root = JSONObject(json)
        AlbumPreferences(
            pinned = root.optJSONArray(KEY_PINNED).toStringSet(),
            hidden = root.optJSONArray(KEY_HIDDEN).toStringSet(),
            covers = root.optJSONObject(KEY_COVERS)?.let { covers ->
                covers.keys().asSequence().associateWith { covers.optLong(it) }
            }.orEmpty(),
        )
    }.getOrElse { AlbumPreferences() }
}

private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return (0 until length()).mapNotNullTo(LinkedHashSet()) { optString(it).takeIf { s -> s.isNotEmpty() } }
}
