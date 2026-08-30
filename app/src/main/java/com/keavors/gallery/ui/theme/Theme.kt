package com.keavors.gallery.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** How the app picks between the light and dark sides of a palette. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Wraps the whole app.
 *
 * The three knobs are deliberately independent, which is what makes "pure white"
 * and "pure black" reachable without a separate theme for each combination:
 * [Palette.MONO] is already white in light mode, and [pureBlack] takes any dark
 * palette down to #000000.
 *
 * The settings screen will drive all three; until it exists they hold defaults.
 */
@Composable
fun GalleryTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: Palette = Palette.COFFEE,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val base = when {
        palette != Palette.DYNAMIC -> staticColorScheme(palette, dark)
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    val colors = if (dark && pureBlack) base.pureBlack() else base

    // Status and navigation bar icons have to flip with the palette, otherwise
    // they vanish into the background one theme at a time.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = GalleryTypography,
        content = content,
    )
}
