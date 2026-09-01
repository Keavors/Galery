package com.keavors.gallery.data

import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * What one word of a search can be looking for.
 *
 * The query is understood once, before the library is walked, rather than
 * re-understood five thousand times. That is also why a word carries whatever it
 * could be read as rather than one interpretation: "2026" is a year and it is
 * also a piece of a file name, and a photograph matches if it answers to either.
 */
data class SearchWord(
    val text: String,
    /** Set when the word reads as a year, as 2026 does. */
    val year: Int? = null,
    /** Set when the word names a month in the app's language, or in English. */
    val month: Int? = null,
    /** Set when the word is a whole date: 01.09.2026, 2026-09-01, 1/9/2026. */
    val day: java.time.LocalDate? = null,
    /** Set when the word means "a video" or "a photograph" rather than a name. */
    val video: Boolean? = null,
)

/** A search, understood. Empty means everything, which is not the same as nothing. */
data class SearchTerms(val words: List<SearchWord>) {
    val isEmpty: Boolean get() = words.isEmpty()
}

private val SEPARATORS = Regex("[\\s,]+")

/** Words that mean "show me the videos" and "show me the photographs". */
private val VIDEO_WORDS = setOf("видео", "video", "видеo", "клип", "movie", "clip")
private val PHOTO_WORDS = setOf("фото", "photo", "photos", "фотография", "снимок", "picture")

/**
 * Reads a query.
 *
 * Every word has to match something about a photograph for it to be found —
 * "море 2019" means both, not either — because narrowing is what somebody typing
 * a second word is trying to do.
 */
fun parseSearch(raw: String, locale: Locale = Locale.getDefault()): SearchTerms {
    val words = raw.trim().split(SEPARATORS).filter { it.isNotBlank() }.map { word ->
        val lower = word.lowercase(locale)
        SearchWord(
            text = lower,
            year = lower.toIntOrNull()?.takeIf { it in 1900..2999 },
            month = monthOf(lower, locale),
            day = dateOf(lower),
            video = when {
                lower in VIDEO_WORDS -> true
                lower in PHOTO_WORDS -> false
                else -> null
            },
        )
    }
    return SearchTerms(words)
}

/**
 * The month a word names, or null.
 *
 * Matched by prefix and in two forms, because Russian has two — "август" as a
 * name and "августа" as a date — and somebody searching types whichever comes to
 * mind. Three letters is the shortest prefix that is not ambiguous between
 * months in either language; "ма" would have to choose between май and март.
 */
private fun monthOf(word: String, locale: Locale): Int? {
    if (word.length < 3) return null
    for (month in Month.entries) {
        val names = listOf(
            month.getDisplayName(TextStyle.FULL, locale),
            month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
            month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
        )
        if (names.any { it.lowercase(locale).startsWith(word) }) return month.value
    }
    return null
}

private val DATE_PARTS = Regex("^(\\d{1,4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,4})$")

/** A whole date, written the way anybody writes one. Null if it is not one. */
private fun dateOf(word: String): java.time.LocalDate? {
    val parts = DATE_PARTS.find(word)?.destructured ?: return null
    val (first, middle, last) = parts
    return runCatching {
        // 2026-09-01 leads with the year; 01.09.2026 ends with it. Nothing else
        // is guessed at: a bare 09.01 could be either day and either order.
        if (first.length == 4) {
            java.time.LocalDate.of(first.toInt(), middle.toInt(), last.toInt())
        } else {
            java.time.LocalDate.of(last.toInt(), middle.toInt(), first.toInt())
        }
    }.getOrNull()
}

/**
 * Whether one photograph answers to one word.
 *
 * Name, folder and kind, and the date in the three ways a date gets asked for.
 * The file name is checked first because it is the cheapest and the likeliest.
 */
fun MediaItem.matches(word: SearchWord, zone: ZoneId): Boolean {
    if (name.contains(word.text, ignoreCase = true)) return true
    if (bucketName.contains(word.text, ignoreCase = true)) return true
    if (relativePath.contains(word.text, ignoreCase = true)) return true

    word.video?.let { wanted -> if (isVideo == wanted) return true }

    if (word.year != null || word.month != null || word.day != null) {
        val date = Instant.ofEpochMilli(takenAt).atZone(zone).toLocalDate()
        if (word.day != null && date == word.day) return true
        if (word.day == null) {
            if (word.year != null && date.year == word.year) return true
            if (word.month != null && date.monthValue == word.month) return true
        }
    }
    return false
}

/**
 * The photographs a search finds, in the order the library already has them.
 *
 * An empty search is not a search: it hands back everything rather than nothing,
 * so clearing the box puts the library back rather than emptying the screen.
 */
fun List<MediaItem>.matching(terms: SearchTerms, zone: ZoneId): List<MediaItem> {
    if (terms.isEmpty) return this
    return filter { item -> terms.words.all { item.matches(it, zone) } }
}
