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

/**
 * A colour to put on top of the palette.
 *
 * The palette decides the whole scheme; this decides only the one colour that
 * marks things — buttons, the selected tab, the tint over a chosen photograph.
 * [DEFAULT] leaves the palette's own, which is the only choice that means
 * anything at all under a scheme taken from the wallpaper.
 */
enum class Accent { DEFAULT, AMBER, AZURE, MOSS, PLUM }

/** What the viewer writes across the top of a photograph. */
enum class ViewerTitle { DATE_AND_NAME, DATE, NAME, NOTHING }

/** How a save leaves the editor, without asking every time. */
enum class SaveChoice { ASK, COPY, OVERWRITE }

/**
 * How dates are written.
 *
 * [AUTO] follows the phone's own language and region, which is right for almost
 * everybody. The other two are for whoever wants one order regardless.
 */
enum class DateStyle { AUTO, DAY_FIRST, YEAR_FIRST }

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
    /** Percent of the ordinary duration: 50 is twice as quick, 200 half. */
    val animationSpeed: Int = 100,
    /** Percent of the phone's own text size, applied to the whole app. */
    val fontScale: Int = 100,
    val accent: Accent = Accent.DEFAULT,

    // Photos
    val defaultZoomColumns: Int = ZoomLevel.Default.columns,
    val sortBy: SortBy = SortBy.TAKEN,
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val showVideos: Boolean = true,
    val showScreenshots: Boolean = true,
    val showDownloads: Boolean = true,
    /** Whether folders switched off on the albums screen still fill the timeline. */
    val showHiddenFolders: Boolean = false,
    val tileBadges: Boolean = true,
    val tileShape: TileShape = TileShape.SQUARE,
    val relativeDates: Boolean = true,

    // Viewer
    val chromeOnOpen: Boolean = true,
    val autoHideSeconds: Int = 0,
    val swipeDownCloses: Boolean = true,
    val swipeUpDetails: Boolean = true,
    val doubleTapZoom: Boolean = true,
    /** How far a photograph can be pinched, as a multiple of what is on screen. */
    val maxZoom: Int = 8,
    val viewerTitle: ViewerTitle = ViewerTitle.DATE_AND_NAME,
    val loopPaging: Boolean = false,
    val maxBrightness: Boolean = false,
    val keepScreenOn: Boolean = false,

    // Video
    val videoAutoplay: Boolean = false,
    val videoSound: Boolean = false,
    val videoRepeat: Boolean = false,
    /** Percent of ordinary speed a video starts at. */
    val videoSpeed: Int = 100,

    // Trash and deleting
    /**
     * Delete without asking, and offer to undo it afterwards.
     *
     * The file goes to the system trash either way; this is only about whether
     * the question comes before the act or the apology comes after it.
     */
    val undoDelete: Boolean = false,
    val confirmForever: Boolean = true,
    val showRemainingDays: Boolean = true,

    // Editor
    val jpegQuality: Int = 95,
    val saveChoice: SaveChoice = SaveChoice.ASK,
    /** Save next to the original rather than in the pictures folder. */
    val saveBeside: Boolean = true,
    val keepExif: Boolean = true,

    // Security
    val appLock: Boolean = false,
    val hideInRecents: Boolean = false,

    // General
    /** BCP-47 tag, or empty to follow the system. */
    val language: String = "",
    val dateStyle: DateStyle = DateStyle.AUTO,
    /** Sizes in 1024s and their proper names — KiB, MiB — rather than in 1000s. */
    val binarySizes: Boolean = false,
    /** How much of the disk the thumbnail cache may take, in megabytes. */
    val cacheLimitMb: Int = 256,
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
    put("animationSpeed", s.animationSpeed)
    put("fontScale", s.fontScale)
    put("accent", s.accent.name)
    put("defaultZoomColumns", s.defaultZoomColumns)
    put("sortBy", s.sortBy.name)
    put("sortOrder", s.sortOrder.name)
    put("showVideos", s.showVideos)
    put("showScreenshots", s.showScreenshots)
    put("showDownloads", s.showDownloads)
    put("showHiddenFolders", s.showHiddenFolders)
    put("tileBadges", s.tileBadges)
    put("tileShape", s.tileShape.name)
    put("relativeDates", s.relativeDates)
    put("chromeOnOpen", s.chromeOnOpen)
    put("autoHideSeconds", s.autoHideSeconds)
    put("swipeDownCloses", s.swipeDownCloses)
    put("swipeUpDetails", s.swipeUpDetails)
    put("doubleTapZoom", s.doubleTapZoom)
    put("maxZoom", s.maxZoom)
    put("viewerTitle", s.viewerTitle.name)
    put("loopPaging", s.loopPaging)
    put("maxBrightness", s.maxBrightness)
    put("keepScreenOn", s.keepScreenOn)
    put("videoAutoplay", s.videoAutoplay)
    put("videoSound", s.videoSound)
    put("videoRepeat", s.videoRepeat)
    put("videoSpeed", s.videoSpeed)
    put("undoDelete", s.undoDelete)
    put("confirmForever", s.confirmForever)
    put("showRemainingDays", s.showRemainingDays)
    put("jpegQuality", s.jpegQuality)
    put("saveChoice", s.saveChoice.name)
    put("saveBeside", s.saveBeside)
    put("keepExif", s.keepExif)
    put("appLock", s.appLock)
    put("hideInRecents", s.hideInRecents)
    put("language", s.language)
    put("dateStyle", s.dateStyle.name)
    put("binarySizes", s.binarySizes)
    put("cacheLimitMb", s.cacheLimitMb)
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
            animationSpeed = o.optInt("animationSpeed", d.animationSpeed).coerceIn(25, 300),
            fontScale = o.optInt("fontScale", d.fontScale).coerceIn(80, 140),
            accent = o.enumOr("accent", d.accent),
            defaultZoomColumns = o.optInt("defaultZoomColumns", d.defaultZoomColumns),
            sortBy = o.enumOr("sortBy", d.sortBy),
            sortOrder = o.enumOr("sortOrder", d.sortOrder),
            showVideos = o.optBoolean("showVideos", d.showVideos),
            showScreenshots = o.optBoolean("showScreenshots", d.showScreenshots),
            showDownloads = o.optBoolean("showDownloads", d.showDownloads),
            showHiddenFolders = o.optBoolean("showHiddenFolders", d.showHiddenFolders),
            tileBadges = o.optBoolean("tileBadges", d.tileBadges),
            tileShape = o.enumOr("tileShape", d.tileShape),
            relativeDates = o.optBoolean("relativeDates", d.relativeDates),
            chromeOnOpen = o.optBoolean("chromeOnOpen", d.chromeOnOpen),
            autoHideSeconds = o.optInt("autoHideSeconds", d.autoHideSeconds).coerceIn(0, 60),
            swipeDownCloses = o.optBoolean("swipeDownCloses", d.swipeDownCloses),
            swipeUpDetails = o.optBoolean("swipeUpDetails", d.swipeUpDetails),
            doubleTapZoom = o.optBoolean("doubleTapZoom", d.doubleTapZoom),
            maxZoom = o.optInt("maxZoom", d.maxZoom).coerceIn(2, 32),
            viewerTitle = o.enumOr("viewerTitle", d.viewerTitle),
            loopPaging = o.optBoolean("loopPaging", d.loopPaging),
            maxBrightness = o.optBoolean("maxBrightness", d.maxBrightness),
            keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
            videoAutoplay = o.optBoolean("videoAutoplay", d.videoAutoplay),
            videoSound = o.optBoolean("videoSound", d.videoSound),
            videoRepeat = o.optBoolean("videoRepeat", d.videoRepeat),
            videoSpeed = o.optInt("videoSpeed", d.videoSpeed).coerceIn(25, 400),
            undoDelete = o.optBoolean("undoDelete", d.undoDelete),
            confirmForever = o.optBoolean("confirmForever", d.confirmForever),
            showRemainingDays = o.optBoolean("showRemainingDays", d.showRemainingDays),
            jpegQuality = o.optInt("jpegQuality", d.jpegQuality).coerceIn(60, 100),
            saveChoice = o.enumOr("saveChoice", d.saveChoice),
            saveBeside = o.optBoolean("saveBeside", d.saveBeside),
            keepExif = o.optBoolean("keepExif", d.keepExif),
            appLock = o.optBoolean("appLock", d.appLock),
            hideInRecents = o.optBoolean("hideInRecents", d.hideInRecents),
            language = o.optString("language", d.language),
            dateStyle = o.enumOr("dateStyle", d.dateStyle),
            binarySizes = o.optBoolean("binarySizes", d.binarySizes),
            cacheLimitMb = o.optInt("cacheLimitMb", d.cacheLimitMb).coerceIn(32, 2048),
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
fun List<MediaItem>.filteredFor(settings: GallerySettings): List<MediaItem> = filter { item ->
    when {
        item.isVideo && !settings.showVideos -> false
        !settings.showScreenshots && item.isScreenshot() -> false
        !settings.showDownloads && item.isDownload() -> false
        else -> true
    }
}

/**
 * Whether a file is a screenshot, as far as anything can tell.
 *
 * By where it lives, because that is all there is to go on: nothing in a PNG
 * says it came from a screen. Android puts them in Pictures/Screenshots or
 * DCIM/Screenshots depending on the phone, and Samsung translates the folder
 * name in some languages, so the English name is checked as a word in the path.
 */
fun MediaItem.isScreenshot(): Boolean =
    relativePath.contains("screenshot", ignoreCase = true) ||
        bucketName.contains("screenshot", ignoreCase = true)

/** Whether a file came from a download, by the folder it is in. */
fun MediaItem.isDownload(): Boolean =
    relativePath.contains("download", ignoreCase = true) ||
        bucketName.contains("download", ignoreCase = true)

/**
 * The pattern a whole date is written with, under a chosen style.
 *
 * [DateStyle.AUTO] hands back null: there is no one pattern for "whatever the
 * phone does", and the caller falls back to the wording it would have used
 * anyway. The other two are the two orders anybody actually asks for.
 */
fun DateStyle.datePattern(): String? = when (this) {
    DateStyle.AUTO -> null
    DateStyle.DAY_FIRST -> "dd.MM.yyyy"
    DateStyle.YEAR_FIRST -> "yyyy-MM-dd"
}
