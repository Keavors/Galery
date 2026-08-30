package com.keavors.gallery.data

import org.json.JSONObject

/** How the app picks between the light and dark sides of a palette. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Which set of colours the app is painted with. Independent of light and dark:
 * every palette has both.
 */
enum class Palette {
    /** Warm neutrals with a single amber accent. The default. */
    COFFEE,

    /** Pure white and pure black with grey accents; photos supply all the colour. */
    MONO,

    /** Taken from the wallpaper (Material You). */
    DYNAMIC,
}

/** What the timeline is ordered by. */
enum class SortBy { TAKEN, MODIFIED, NAME, SIZE }

enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST }

/**
 * Everything the app has been told to do differently.
 *
 * One flat record with a default for every field, so a setting that has never
 * been touched and a settings file that has never been written are the same
 * thing. Nothing here is nullable: "not set" is a value, not an absence.
 */
data class GallerySettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.COFFEE,
    val pureBlack: Boolean = false,
    val tileGapDp: Int = 2,
    val tileCornerDp: Int = 3,
    val animations: Boolean = true,

    // Photos
    val defaultZoomColumns: Int = ZoomLevel.Default.columns,
    val sortBy: SortBy = SortBy.TAKEN,
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val showVideos: Boolean = true,
    val tileBadges: Boolean = true,
    val relativeDates: Boolean = true,

    // Viewer
    val chromeOnOpen: Boolean = true,
    val autoHideSeconds: Int = 0,
    val swipeDownCloses: Boolean = true,
    val swipeUpDetails: Boolean = true,
    val doubleTapZoom: Boolean = true,
    val loopPaging: Boolean = false,
    val maxBrightness: Boolean = false,
    val keepScreenOn: Boolean = false,

    // Video
    val videoAutoplay: Boolean = false,
    val videoSound: Boolean = false,
    val videoRepeat: Boolean = false,

    // Editor
    val jpegQuality: Int = 95,

    // Security
    val appLock: Boolean = false,
    val hideInRecents: Boolean = false,

    // General
    /** BCP-47 tag, or empty to follow the system. */
    val language: String = "",
) {
    /** The zoom level the timeline starts at, matched by column count. */
    val defaultZoom: ZoomLevel
        get() = ZoomLevel.entries.firstOrNull { it.columns == defaultZoomColumns } ?: ZoomLevel.Default
}

// ------------------------------------------------------------- json ---------

/*
 * Stored the same way the album preferences are: one JSON document. It makes
 * exporting the settings to a file the same operation as saving them, and there
 * is no schema to migrate when a field is added — an older document simply has
 * no opinion about the new one, and the default stands.
 */

fun encodeSettings(s: GallerySettings): String = JSONObject().apply {
    put("themeMode", s.themeMode.name)
    put("palette", s.palette.name)
    put("pureBlack", s.pureBlack)
    put("tileGapDp", s.tileGapDp)
    put("tileCornerDp", s.tileCornerDp)
    put("animations", s.animations)
    put("defaultZoomColumns", s.defaultZoomColumns)
    put("sortBy", s.sortBy.name)
    put("sortOrder", s.sortOrder.name)
    put("showVideos", s.showVideos)
    put("tileBadges", s.tileBadges)
    put("relativeDates", s.relativeDates)
    put("chromeOnOpen", s.chromeOnOpen)
    put("autoHideSeconds", s.autoHideSeconds)
    put("swipeDownCloses", s.swipeDownCloses)
    put("swipeUpDetails", s.swipeUpDetails)
    put("doubleTapZoom", s.doubleTapZoom)
    put("loopPaging", s.loopPaging)
    put("maxBrightness", s.maxBrightness)
    put("keepScreenOn", s.keepScreenOn)
    put("videoAutoplay", s.videoAutoplay)
    put("videoSound", s.videoSound)
    put("videoRepeat", s.videoRepeat)
    put("jpegQuality", s.jpegQuality)
    put("appLock", s.appLock)
    put("hideInRecents", s.hideInRecents)
    put("language", s.language)
}.toString()

/**
 * Reads a settings document.
 *
 * Every field falls back to its default independently, so a document written by
 * an older version, or damaged, costs only the settings it actually lost.
 */
fun decodeSettings(json: String?): GallerySettings {
    if (json.isNullOrBlank()) return GallerySettings()
    val d = GallerySettings()
    return runCatching {
        val o = JSONObject(json)
        GallerySettings(
            themeMode = o.enumOr("themeMode", d.themeMode),
            palette = o.enumOr("palette", d.palette),
            pureBlack = o.optBoolean("pureBlack", d.pureBlack),
            tileGapDp = o.optInt("tileGapDp", d.tileGapDp).coerceIn(0, 8),
            tileCornerDp = o.optInt("tileCornerDp", d.tileCornerDp).coerceIn(0, 16),
            animations = o.optBoolean("animations", d.animations),
            defaultZoomColumns = o.optInt("defaultZoomColumns", d.defaultZoomColumns),
            sortBy = o.enumOr("sortBy", d.sortBy),
            sortOrder = o.enumOr("sortOrder", d.sortOrder),
            showVideos = o.optBoolean("showVideos", d.showVideos),
            tileBadges = o.optBoolean("tileBadges", d.tileBadges),
            relativeDates = o.optBoolean("relativeDates", d.relativeDates),
            chromeOnOpen = o.optBoolean("chromeOnOpen", d.chromeOnOpen),
            autoHideSeconds = o.optInt("autoHideSeconds", d.autoHideSeconds).coerceIn(0, 60),
            swipeDownCloses = o.optBoolean("swipeDownCloses", d.swipeDownCloses),
            swipeUpDetails = o.optBoolean("swipeUpDetails", d.swipeUpDetails),
            doubleTapZoom = o.optBoolean("doubleTapZoom", d.doubleTapZoom),
            loopPaging = o.optBoolean("loopPaging", d.loopPaging),
            maxBrightness = o.optBoolean("maxBrightness", d.maxBrightness),
            keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
            videoAutoplay = o.optBoolean("videoAutoplay", d.videoAutoplay),
            videoSound = o.optBoolean("videoSound", d.videoSound),
            videoRepeat = o.optBoolean("videoRepeat", d.videoRepeat),
            jpegQuality = o.optInt("jpegQuality", d.jpegQuality).coerceIn(60, 100),
            appLock = o.optBoolean("appLock", d.appLock),
            hideInRecents = o.optBoolean("hideInRecents", d.hideInRecents),
            language = o.optString("language", d.language),
        )
    }.getOrElse { d }
}

private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, fallback: E): E {
    val raw = optString(key).takeIf { it.isNotEmpty() } ?: return fallback
    return runCatching { enumValueOf<E>(raw) }.getOrElse { fallback }
}

// ------------------------------------------------------------- sorting ------

/**
 * Puts the library in the order the settings ask for.
 *
 * Sorted here rather than in the database query so the rule lives in Kotlin,
 * where the timeline can change it without a round trip, and where it can be
 * tested without a phone.
 */
fun List<MediaItem>.sortedFor(by: SortBy, order: SortOrder): List<MediaItem> {
    val ascending = when (by) {
        SortBy.TAKEN -> sortedBy { it.takenAt }
        SortBy.MODIFIED -> sortedBy { it.modifiedAt }
        // Case-insensitive: IMG_2.jpg and img_10.jpg belong next to each other,
        // not in separate blocks divided by capitalisation.
        SortBy.NAME -> sortedBy { it.name.lowercase() }
        SortBy.SIZE -> sortedBy { it.sizeBytes }
    }
    return if (order == SortOrder.OLDEST_FIRST) ascending else ascending.asReversed()
}

/** Applies the settings that decide what the timeline contains at all. */
fun List<MediaItem>.filteredFor(settings: GallerySettings): List<MediaItem> =
    if (settings.showVideos) this else filter { !it.isVideo }
