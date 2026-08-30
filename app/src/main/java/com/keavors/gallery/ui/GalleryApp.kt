package com.keavors.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.keavors.gallery.ui.common.PlaceholderScreen

/** Duration of the cross-fade between tabs, ms. Kept short: tabs are cheap. */
private const val TAB_FADE_IN = 220
private const val TAB_FADE_OUT = 140

@Composable
fun GalleryApp() {
    // Saved as an ordinal so the selection survives rotation without a custom saver.
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[selected]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEachIndexed { index, entry ->
                    NavigationBarItem(
                        selected = index == selected,
                        onClick = { selected = index },
                        icon = {
                            Icon(
                                painter = painterResource(entry.icon),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(entry.title)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { insets ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                // A fade with a whisper of scale: tabs are siblings, so nothing
                // should slide in from a direction that implies hierarchy.
                (fadeIn(tween(TAB_FADE_IN)) + scaleIn(tween(TAB_FADE_IN), initialScale = 0.985f))
                    .togetherWith(fadeOut(tween(TAB_FADE_OUT)))
            },
            label = "tab",
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) { current ->
            PlaceholderScreen(current)
        }
    }
}
