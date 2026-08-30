package com.keavors.gallery.data

import java.util.Locale

/**
 * Human-readable file size.
 *
 * Uses the units a phone's storage screen uses — powers of 1024 labelled KB, MB,
 * GB — because matching what Android itself shows matters more than being right
 * about SI prefixes.
 */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    // One decimal below 100, none above: "1.4 GB" is useful, "148.3 GB" is noise.
    val pattern = if (value < 100) "%.1f %s" else "%.0f %s"
    return String.format(locale, pattern, value, UNITS[unit])
}

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

/** Groups digits so a five-figure photo count stays readable. */
fun formatCount(value: Int, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%,d", value).replace(',', ' ').replace(' ', ' ')
