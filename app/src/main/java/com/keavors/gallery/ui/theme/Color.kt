package com.keavors.gallery.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Which set of colours the app is painted with. Independent of light/dark: every
 * palette has both.
 */
enum class Palette {
    /** Warm neutrals with a single amber accent. The default. */
    COFFEE,

    /** Pure white and pure black with grey accents; photos supply all the colour. */
    MONO,

    /** Taken from the wallpaper (Material You). */
    DYNAMIC,
}

// ---------------------------------------------------------------- coffee ----

private val CoffeeLight = lightColorScheme(
    primary = Color(0xFF8A5A12),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDA8),
    onPrimaryContainer = Color(0xFF2C1800),
    secondary = Color(0xFF6D5C43),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7E0BF),
    onSecondaryContainer = Color(0xFF261906),
    tertiary = Color(0xFF4C6553),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F5F1),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFF7F5F1),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFEBE2D3),
    onSurfaceVariant = Color(0xFF4C463A),
    surfaceContainer = Color(0xFFEEEAE3),
    surfaceContainerHigh = Color(0xFFE8E3DA),
    outline = Color(0xFF7E7768),
    outlineVariant = Color(0xFFCFC6B6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val CoffeeDark = darkColorScheme(
    primary = Color(0xFFF2B44A),
    onPrimary = Color(0xFF442B00),
    primaryContainer = Color(0xFF614000),
    onPrimaryContainer = Color(0xFFFFDDA8),
    secondary = Color(0xFFDAC4A4),
    onSecondary = Color(0xFF3C2E18),
    secondaryContainer = Color(0xFF54442D),
    onSecondaryContainer = Color(0xFFF7E0BF),
    tertiary = Color(0xFFB2CDB8),
    onTertiary = Color(0xFF1E3527),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFE8E2D8),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFE8E2D8),
    surfaceVariant = Color(0xFF4C463A),
    onSurfaceVariant = Color(0xFFCFC6B6),
    surfaceContainer = Color(0xFF211E19),
    surfaceContainerHigh = Color(0xFF2C2823),
    outline = Color(0xFF989082),
    outlineVariant = Color(0xFF4C463A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

// ------------------------------------------------------------------ mono ----

private val MonoLight = lightColorScheme(
    primary = Color(0xFF1F1F1F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E4E4),
    onPrimaryContainer = Color(0xFF101010),
    secondary = Color(0xFF4A4A4A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEDED),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF3D3D3D),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF464646),
    surfaceContainer = Color(0xFFF4F4F4),
    surfaceContainerHigh = Color(0xFFEBEBEB),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFFD6D6D6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val MonoDark = darkColorScheme(
    primary = Color(0xFFE6E6E6),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF303030),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF262626),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFCFCFCF),
    onTertiary = Color(0xFF242424),
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF0B0B0B),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFC6C6C6),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    outline = Color(0xFF8F8F8F),
    outlineVariant = Color(0xFF3A3A3A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

// ----------------------------------------------------------------- pick -----

/** The static palettes. [Palette.DYNAMIC] is resolved from the wallpaper instead. */
internal fun staticColorScheme(palette: Palette, dark: Boolean): ColorScheme = when {
    palette == Palette.MONO && dark -> MonoDark
    palette == Palette.MONO -> MonoLight
    dark -> CoffeeDark
    else -> CoffeeLight
}

/**
 * Drops every surface to true black for OLED panels: unlit pixels save power and
 * make photos look like they float. Only surfaces move, the accent stays put.
 */
internal fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1E1E1E),
)
