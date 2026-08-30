package com.keavors.gallery.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * An album someone made themselves.
 *
 * The only thing in this app that cannot be worked out from MediaStore: a folder
 * is a place on the disk and favourites are a flag on a file, but "holidays" is
 * a decision, and nowhere but here remembers it.
 */
data class UserAlbum(
    val id: Long,
    val name: String,
    val memberIds: Set<Long>,
)

/** Everything the albums screen remembers between runs. */
data class AlbumPreferences(
    val pinned: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    /** Album key to the id of the photo chosen as its cover. */
    val covers: Map<String, Long> = emptyMap(),
    val userAlbums: List<UserAlbum> = emptyList(),
) {
    fun isPinned(source: AlbumSource) = source.key in pinned
    fun isHidden(source: AlbumSource) = source.key in hidden
    fun coverId(source: AlbumSource): Long? = covers[source.key]
}

/**
 * A stable name for an album.
 *
 * Folders are keyed by their bucket id rather than their name, so renaming one
 * on the phone does not lose whether it was pinned.
 */
val AlbumSource.key: String
    get() = when (this) {
        is AlbumSource.Folder -> "folder:$bucketId"
        AlbumSource.Favourites -> "favourites"
        AlbumSource.Videos -> "videos"
        is AlbumSource.User -> "user:$albumId"
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
private const val KEY_ALBUMS = "albums"
private const val KEY_ID = "id"
private const val KEY_NAME = "name"
private const val KEY_MEMBERS = "members"

fun encodeAlbumPreferences(prefs: AlbumPreferences): String {
    val root = JSONObject()
    root.put(KEY_PINNED, JSONArray(prefs.pinned.toList()))
    root.put(KEY_HIDDEN, JSONArray(prefs.hidden.toList()))

    val covers = JSONObject()
    prefs.covers.forEach { (key, id) -> covers.put(key, id) }
    root.put(KEY_COVERS, covers)

    val albums = JSONArray()
    prefs.userAlbums.forEach { album ->
        albums.put(
            JSONObject()
                .put(KEY_ID, album.id)
                .put(KEY_NAME, album.name)
                .put(KEY_MEMBERS, JSONArray(album.memberIds.toList()))
        )
    }
    root.put(KEY_ALBUMS, albums)
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
            userAlbums = root.optJSONArray(KEY_ALBUMS)?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val obj = array.optJSONObject(index) ?: return@mapNotNull null
                    UserAlbum(
                        id = obj.optLong(KEY_ID),
                        name = obj.optString(KEY_NAME),
                        memberIds = obj.optJSONArray(KEY_MEMBERS).toLongSet(),
                    )
                }
            }.orEmpty(),
        )
    }.getOrElse { AlbumPreferences() }
}

private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return (0 until length()).mapNotNullTo(LinkedHashSet()) { optString(it).takeIf { s -> s.isNotEmpty() } }
}

private fun JSONArray?.toLongSet(): Set<Long> {
    if (this == null) return emptySet()
    return (0 until length()).mapTo(LinkedHashSet()) { optLong(it) }
}
