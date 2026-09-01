package com.keavors.gallery.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.keavors.gallery.data.Accent
import com.keavors.gallery.data.Palette
import com.keavors.gallery.data.ThemeMode

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
    accent: Accent = Accent.DEFAULT,
    /** Percent of the phone's own text size. */
    fontScale: Int = 100,
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
    val accented = base.withAccent(accent, dark)
    val colors = if (dark && pureBlack) accented.pureBlack() else accented

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

    // Text size is changed by changing what a scaled pixel means rather than by
      // rewriting every text style: one number moves the whole app, including the
      // parts that never asked to be resized, and it stacks with the phone's own
      // accessibility setting instead of fighting it.
    val density = LocalDensity.current
    val scaled = remember(density, fontScale) {
        Density(density.density, density.fontScale * fontScale / 100f)
    }

    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = colors,
            typography = GalleryTypography,
            content = content,
        )
    }
}
