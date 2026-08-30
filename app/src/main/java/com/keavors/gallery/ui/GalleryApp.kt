package com.keavors.gallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keavors.gallery.data.LibraryState
import com.keavors.gallery.data.MediaAccess
import com.keavors.gallery.data.MediaRepository
import com.keavors.gallery.data.canManageMedia
import com.keavors.gallery.data.mediaAccess
import com.keavors.gallery.data.mediaPermissions
import com.keavors.gallery.ui.common.PlaceholderScreen
import com.keavors.gallery.ui.permission.MediaGate
import com.keavors.gallery.ui.photos.PhotosScreen
import com.keavors.gallery.ui.settings.SettingsScreen
import com.keavors.gallery.ui.viewer.ViewerScreen

/** Duration of the cross-fade between tabs, ms. Kept short: tabs are cheap. */
private const val TAB_FADE_IN = 220
private const val TAB_FADE_OUT = 140

@Composable
fun GalleryApp(repository: MediaRepository) {
    val context = LocalContext.current

    // Saved as an ordinal so the selection survives rotation without a custom saver.
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[selected]

    // Which photo the viewer is showing, if any. Held by id rather than by
    // index so a library reload underneath cannot slide it onto another photo.
    var viewingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewingBucket by rememberSaveable { mutableIntStateOf(384) }

    var access by remember { mutableStateOf(context.mediaAccess()) }
    var manageMedia by remember { mutableStateOf(context.canManageMedia()) }
    val library by repository.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // The result map is not consulted on purpose: which permissions add up to
        // full access is decided in one place, and this is not it.
        access = context.mediaAccess()
        repository.refresh()
    }

    // Access can change while the app sits in the background — the user may have
    // opened system settings to widen or revoke it — so it is re-read on every
    // resume rather than only at startup.
    LifecycleResumeEffect(Unit) {
        access = context.mediaAccess()
        manageMedia = context.canManageMedia()
        repository.refresh()
        onPauseOrDispose { }
    }

    val items = (library as? LibraryState.Ready)?.items.orEmpty()
    val viewingIndex = viewingId?.let { id -> items.indexOfFirst { it.id == id } } ?: -1

    Box(modifier = Modifier.fillMaxSize()) {

    Scaffold(
        // Composition and layout are kept so the grid holds its scroll position,
        // but there is no point drawing a thousand tiles under an opaque viewer.
        modifier = Modifier.drawWithContent { if (viewingIndex < 0) drawContent() },
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
            when (current) {
                Tab.PHOTOS -> MediaGate(
                    access = access,
                    onRequest = { permissionLauncher.launch(mediaPermissions) },
                    onOpenSettings = { context.startActivity(appSettingsIntent(context.packageName)) },
                ) {
                    PhotosScreen(
                        state = library,
                        onOpen = { item, bucket ->
                            viewingBucket = bucket
                            viewingId = item.id
                        },
                    )
                }

                // Settings never sits behind the gate: it is where a person goes
                // to understand why the rest of the app is asking for anything.
                Tab.SETTINGS -> SettingsScreen(
                    access = access,
                    canManageMedia = manageMedia,
                    onOpenSettings = { context.startActivity(appSettingsIntent(context.packageName)) },
                    onRequestManageMedia = {
                        context.startActivity(manageMediaIntent(context.packageName))
                    },
                )

                else -> PlaceholderScreen(current)
            }
        }
    }

        if (viewingIndex >= 0) {
            ViewerScreen(
                items = items,
                startIndex = viewingIndex,
                thumbBucketPx = viewingBucket,
                onClose = { viewingId = null },
            )
        }
    }
}

private fun appSettingsIntent(packageName: String) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null),
)

private fun manageMediaIntent(packageName: String) = Intent(
    Settings.ACTION_REQUEST_MANAGE_MEDIA,
    Uri.fromParts("package", packageName, null),
)
