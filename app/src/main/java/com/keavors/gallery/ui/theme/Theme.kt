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

/** How the app picks between the light and dark palettes. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Wraps the whole app. The three switches here are the ones the settings screen
 * will drive later; for now they take their defaults from the system.
 *
 * @param amoled turns the dark palette pure black. Ignored in light mode.
 * @param dynamicColor takes the palette from the wallpaper (Material You).
 */
@Composable
fun GalleryTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val colors = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark && amoled -> GalleryAmoledColors
        dark -> GalleryDarkColors
        else -> GalleryLightColors
    }

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
