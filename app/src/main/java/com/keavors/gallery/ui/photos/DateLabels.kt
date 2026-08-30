package com.keavors.gallery.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.keavors.gallery.R
import com.keavors.gallery.data.DateBucket
import com.keavors.gallery.data.Grouping
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The heading above a section.
 *
 * Today and yesterday are named rather than dated, and the year is dropped for
 * the current one — a heading that reads "30 August 2026" on the day it is
 * written spends most of its width telling you what you already know.
 */
@Composable
fun sectionTitle(
    bucket: DateBucket,
    grouping: Grouping,
    locale: Locale,
    today: LocalDate,
    relativeDates: Boolean,
): String = when (grouping) {
    Grouping.YEAR -> bucket.year.toString()

    Grouping.MONTH -> {
        val date = LocalDate.of(bucket.year, bucket.month, 1)
        val pattern = if (bucket.year == today.year) "LLLL" else "LLLL yyyy"
        DateTimeFormatter.ofPattern(pattern, locale).format(date).replaceFirstChar {
            it.titlecase(locale)
        }
    }

    Grouping.DAY -> {
        val date = LocalDate.of(bucket.year, bucket.month, bucket.day)
        when {
            relativeDates && date == today -> stringResource(R.string.date_today)
            relativeDates && date == today.minusDays(1) -> stringResource(R.string.date_yesterday)
            else -> {
                val pattern = if (bucket.year == today.year) "d MMMM, EEEE" else "d MMMM yyyy, EEEE"
                DateTimeFormatter.ofPattern(pattern, locale).format(date)
                    .replaceFirstChar { it.titlecase(locale) }
            }
        }
    }
}

/** Short label for the scrollbar bubble: never more than a month and a year. */
fun scrollLabel(bucket: DateBucket, locale: Locale): String {
    if (bucket.month == 0) return bucket.year.toString()
    val date = LocalDate.of(bucket.year, bucket.month, 1)
    return DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(date)
        .replaceFirstChar { it.titlecase(locale) }
}
