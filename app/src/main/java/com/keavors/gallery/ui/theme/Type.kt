package com.keavors.gallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * Material 3 defaults with tighter tracking on the large styles — dates and
 * album names are short strings that look loose at stock letter spacing.
 */
val GalleryTypography = Typography(
    displaySmall = Default.displaySmall.tighten(),
    headlineLarge = Default.headlineLarge.tighten(),
    headlineMedium = Default.headlineMedium.tighten(),
    headlineSmall = Default.headlineSmall.tighten(),
    titleLarge = Default.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.Medium),
)

private fun TextStyle.tighten(): TextStyle = copy(letterSpacing = (-0.5).sp)
