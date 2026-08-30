package com.keavors.gallery.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * A gallery is a frame around someone else's colours, so the palette stays warm
 * and neutral and spends all of its saturation on a single amber accent.
 */

private val AmberLight = Color(0xFF8A5A12)
private val AmberDark = Color(0xFFF2B44A)

val GalleryLightColors: ColorScheme = lightColorScheme(
    primary = AmberLight,
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

val GalleryDarkColors: ColorScheme = darkColorScheme(
    primary = AmberDark,
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

/**
 * Pure black variant for OLED panels: unlit pixels save power and make photos
 * look like they float. Only the surfaces change, the accent stays put.
 */
val GalleryAmoledColors: ColorScheme = GalleryDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF0C0B09),
    surfaceContainerHigh = Color(0xFF16140F),
)
