package com.keavors.gallery.data

import java.time.Instant
import java.time.ZoneId

/** How coarsely the timeline groups shots at a given zoom level. */
enum class Grouping { DAY, MONTH, YEAR }

/**
 * The five steps the grid moves through when pinched.
 *
 * Columns and grouping move together on purpose: at twenty-five across, a
 * per-day heading would appear every other row and the headings would outweigh
 * the photos, so the grid coarsens its dates as it coarsens its tiles.
 */
enum class ZoomLevel(val columns: Int, val grouping: Grouping) {
    HUGE(2, Grouping.DAY),
    LARGE(4, Grouping.DAY),
    MEDIUM(7, Grouping.DAY),
    SMALL(12, Grouping.MONTH),
    TINY(25, Grouping.YEAR);

    /** One step towards bigger tiles, or the same level if already there. */
    fun zoomIn(): ZoomLevel = entries.getOrElse(ordinal - 1) { this }

    /** One step towards smaller tiles. */
    fun zoomOut(): ZoomLevel = entries.getOrElse(ordinal + 1) { this }

    /** Moves [steps] levels; positive means bigger tiles. Stops at either end. */
    fun stepBy(steps: Int): ZoomLevel =
        entries[(ordinal - steps).coerceIn(0, entries.lastIndex)]

    companion object {
        val Default = LARGE
    }
}

/** How far a pinch has to stretch the picture before it is worth one step. */
private const val ZOOM_STEP_RATIO = 1.3f

/**
 * Levels a single pinch may move.
 *
 * One. A pinch that could cross the whole range at once makes the grid feel like
 * it is guessing, and the way back is another gesture of exactly the right size.
 * Raise this if stepping through five levels ever starts to feel slow.
 */
private const val MAX_ZOOM_STEPS = 1

/**
 * Turns the scale a finished pinch ended on into a number of levels to move.
 *
 * Positive means bigger tiles. Anything short of the threshold is nothing: a
 * grid that changed on every stray two-finger touch would be unusable.
 */
fun zoomSteps(scale: Float): Int {
    if (!scale.isFinite() || scale <= 0f) return 0

    val stretch = if (scale >= 1f) scale else 1f / scale
    var steps = 0
    var remaining = stretch
    while (remaining >= ZOOM_STEP_RATIO && steps < MAX_ZOOM_STEPS) {
        remaining /= ZOOM_STEP_RATIO
        steps++
    }
    return if (scale >= 1f) steps else -steps
}

/**
 * A point on the calendar, as coarse as the current grouping.
 *
 * Kept as numbers rather than a formatted string so the heading can be rendered
 * in whatever language the app is running in, and so grouping can be tested
 * without a locale.
 */
data class DateBucket(val year: Int, val month: Int, val day: Int) {

    companion object {
        fun of(epochMillis: Long, grouping: Grouping, zone: ZoneId): DateBucket {
            val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            return when (grouping) {
                Grouping.DAY -> DateBucket(date.year, date.monthValue, date.dayOfMonth)
                Grouping.MONTH -> DateBucket(date.year, date.monthValue, 0)
                Grouping.YEAR -> DateBucket(date.year, 0, 0)
            }
        }
    }
}

/**
 * One line of the timeline.
 *
 * The grid is built as a list of rows rather than a flat list of photos because
 * headings have to stick to the top while their own section scrolls past, and
 * that needs the section structure to survive into the list itself.
 */
sealed interface TimelineRow {

    /** Stable across reloads and zoom changes, which is what keeps scroll position. */
    val key: String

    data class Header(
        val bucket: DateBucket,
        val grouping: Grouping,
        val count: Int,
        /**
         * The first photo of the section.
         *
         * The heading is keyed on it rather than on the date, because a date can
         * turn up in two sections if the library ever arrives out of order — and
         * a repeated key in a lazy list is a crash, not a glitch.
         */
        val anchorId: Long,
    ) : TimelineRow {
        override val key: String get() = "h$anchorId"
    }

    data class Photos(val items: List<MediaItem>) : TimelineRow {
        override val key: String get() = "p${items.first().id}"
    }

    /**
     * The search box.
     *
     * A row of the list rather than a bar above it, for two reasons. It scrolls
     * away with the photographs, so it costs nothing at all once somebody is
     * looking at pictures. And every other piece of machinery here — the anchor
     * of a zoom, the photo under a dragging finger, the scrollbar — counts rows,
     * so a box that is a row is counted correctly by all of them without any of
     * them being told about it.
     */
    data object Search : TimelineRow {
        override val key: String get() = "search"
    }
}

/**
 * Turns the library into the rows the grid draws.
 *
 * Expects [items] already sorted newest first, which is how the repository hands
 * them over: this walks the list once and cuts it where the date bucket changes,
 * instead of grouping into a map and sorting the groups back.
 */
fun buildTimeline(
    items: List<MediaItem>,
    level: ZoomLevel,
    zone: ZoneId,
    withSearch: Boolean = false,
): List<TimelineRow> {
    val rows = ArrayList<TimelineRow>(items.size / level.columns + 16)
    // Before the emptiness check, deliberately: a search that finds nothing must
    // still leave the box on the screen, or there is no way to change it.
    if (withSearch) rows += TimelineRow.Search
    if (items.isEmpty()) return rows

    var sectionStart = 0
    var current = DateBucket.of(items[0].takenAt, level.grouping, zone)

    fun flush(endExclusive: Int) {
        val section = items.subList(sectionStart, endExclusive)
        rows += TimelineRow.Header(current, level.grouping, section.size, section.first().id)
        section.chunked(level.columns) { row -> rows += TimelineRow.Photos(row.toList()) }
    }

    for (index in 1 until items.size) {
        val bucket = DateBucket.of(items[index].takenAt, level.grouping, zone)
        if (bucket != current) {
            flush(index)
            sectionStart = index
            current = bucket
        }
    }
    flush(items.size)
    return rows
}

/**
 * Where the given photo ended up after a zoom change.
 *
 * Changing zoom re-cuts every row, so the only way to stay looking at the same
 * picture is to find it again by id. Returns -1 when it is gone, which happens
 * when the library reloads under the gesture.
 */
fun List<TimelineRow>.rowOf(itemId: Long): Int {
    forEachIndexed { index, row ->
        if (row is TimelineRow.Photos && row.items.any { it.id == itemId }) return index
    }
    return -1
}

/** The first photo at or after [rowIndex], used to anchor a zoom change. */
fun List<TimelineRow>.firstItemFrom(rowIndex: Int): MediaItem? {
    for (index in rowIndex.coerceAtLeast(0) until size) {
        val row = this[index]
        if (row is TimelineRow.Photos) return row.items.first()
    }
    return null
}

/**
 * The heading that governs [rowIndex] — the nearest one at or above it.
 *
 * The scrollbar bubble needs the date of whatever is under the thumb, and the
 * row under the thumb is usually a row of photos rather than a heading.
 */
fun List<TimelineRow>.headerAt(rowIndex: Int): TimelineRow.Header? {
    for (index in rowIndex.coerceIn(0, lastIndex.coerceAtLeast(0)) downTo 0) {
        val row = getOrNull(index)
        if (row is TimelineRow.Header) return row
    }
    return null
}

/**
 * The photos governed by the heading at [headerIndex].
 *
 * Walks forward to the next heading rather than re-grouping the library: the
 * rows already carry the section boundaries, and a heading needs to know what it
 * covers so tapping it can select the whole day at once.
 */
fun List<TimelineRow>.sectionItems(headerIndex: Int): List<MediaItem> {
    if (getOrNull(headerIndex) !is TimelineRow.Header) return emptyList()
    val items = ArrayList<MediaItem>()
    for (index in headerIndex + 1 until size) {
        when (val row = this[index]) {
            is TimelineRow.Photos -> items += row.items
            is TimelineRow.Header -> break
            TimelineRow.Search -> Unit
        }
    }
    return items
}
