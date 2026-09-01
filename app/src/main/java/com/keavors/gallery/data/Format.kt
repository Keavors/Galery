package com.keavors.gallery.data

import java.util.Locale

/**
 * Human-readable file size.
 *
 * Uses the units a phone's storage screen uses — powers of 1024 labelled KB, MB,
 * GB — because matching what Android itself shows matters more than being right
 * about SI prefixes.
 */
fun formatBytes(
    bytes: Long,
    locale: Locale = Locale.getDefault(),
    binary: Boolean = false,
): String {
    // Two conventions, both honest and both in use: phones and disks are sold in
    // millions of bytes, and the file system counts in 1024s. The names go with
    // the arithmetic — a MiB is not a MB — so choosing one never leaves a number
    // labelled with the other's unit.
    val step = if (binary) 1024.0 else 1000.0
    val units = if (binary) BINARY_UNITS else UNITS
    if (bytes < step) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= step && unit < units.lastIndex) {
        value /= step
        unit++
    }
    // One decimal below 100, none above: "1.4 GB" is useful, "148.3 GB" is noise.
    val pattern = if (value < 100) "%.1f %s" else "%.0f %s"
    return String.format(locale, pattern, value, units[unit])
}

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")
private val BINARY_UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
