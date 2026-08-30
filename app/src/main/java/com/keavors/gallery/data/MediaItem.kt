package com.keavors.gallery.data

/**
 * One photo or video in the library.
 *
 * Deliberately free of android types so the rules that decide what a date means
 * and how items group can be tested on the JVM. The content Uri is built from
 * [id] and [isVideo] where it is needed.
 */
data class MediaItem(
    val id: Long,
    /**
     * Where the file actually lives, as a string so the model stays free of
     * android types. Carried rather than derived from [id]: a photo handed over
     * by another app may have no MediaStore id at all.
     */
    val uri: String,
    val name: String,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    /** When the shot was taken, epoch millis. See [MediaTime.bestTimestamp]. */
    val takenAt: Long,
    val addedAt: Long,
    val modifiedAt: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val isFavorite: Boolean,
    val orientation: Int,
)

/**
 * Timestamp rules, kept apart from the query because they are the part that is
 * easy to get quietly wrong.
 */
object MediaTime {

    /**
     * MediaStore keeps three dates and none of them is always right.
     *
     * DATE_TAKEN comes from the photo's own metadata and is what a person means
     * by "when this was taken" — but it is missing on screenshots, downloads and
     * most videos. DATE_MODIFIED and DATE_ADDED are always present, and are in
     * seconds rather than milliseconds.
     *
     * @param dateTakenMillis MediaStore.DATE_TAKEN, 0 when absent.
     * @param dateModifiedSeconds MediaStore.DATE_MODIFIED, 0 when absent.
     * @param dateAddedSeconds MediaStore.DATE_ADDED, 0 when absent.
     */
    fun bestTimestamp(
        dateTakenMillis: Long,
        dateModifiedSeconds: Long,
        dateAddedSeconds: Long,
    ): Long = when {
        dateTakenMillis > 0 -> dateTakenMillis
        dateModifiedSeconds > 0 -> dateModifiedSeconds * 1000
        dateAddedSeconds > 0 -> dateAddedSeconds * 1000
        else -> 0
    }
}

/** Counts and totals for the whole library. */
data class LibrarySummary(
    val photos: Int,
    val videos: Int,
    val albums: Int,
    val totalBytes: Long,
    val oldest: Long?,
    val newest: Long?,
) {
    val total: Int get() = photos + videos

    companion object {
        val Empty = LibrarySummary(0, 0, 0, 0, null, null)
    }
}

/** Folds the library into the numbers the summary screen shows. */
fun List<MediaItem>.summarize(): LibrarySummary {
    if (isEmpty()) return LibrarySummary.Empty
    var photos = 0
    var videos = 0
    var bytes = 0L
    var oldest = Long.MAX_VALUE
    var newest = Long.MIN_VALUE
    val buckets = HashSet<Long>()
    for (item in this) {
        if (item.isVideo) videos++ else photos++
        bytes += item.sizeBytes
        buckets += item.bucketId
        // Items with no usable date at all must not drag the range to 1970.
        if (item.takenAt > 0) {
            if (item.takenAt < oldest) oldest = item.takenAt
            if (item.takenAt > newest) newest = item.takenAt
        }
    }
    return LibrarySummary(
        photos = photos,
        videos = videos,
        albums = buckets.size,
        totalBytes = bytes,
        oldest = oldest.takeIf { it != Long.MAX_VALUE },
        newest = newest.takeIf { it != Long.MIN_VALUE },
    )
}
