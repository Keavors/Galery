package com.keavors.gallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keavors.gallery.R
import com.keavors.gallery.data.LibraryState
import com.keavors.gallery.data.MediaAccess
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaRepository
import com.keavors.gallery.data.canManageMedia
import com.keavors.gallery.data.inFolder
import com.keavors.gallery.data.indexOfId
import com.keavors.gallery.data.mediaAccess
import com.keavors.gallery.data.mediaPermissions
import com.keavors.gallery.ui.album.AlbumScreen
import com.keavors.gallery.ui.common.PlaceholderScreen
import com.keavors.gallery.ui.permission.MediaGate
import com.keavors.gallery.ui.photos.PhotosScreen
import com.keavors.gallery.ui.settings.SettingsScreen
import com.keavors.gallery.ui.viewer.ViewerScreen

/** Duration of the cross-fade between tabs, ms. Kept short: tabs are cheap. */
private const val TAB_FADE_IN = 220
private const val TAB_FADE_OUT = 140

/** Used until a tile has been measured and can say which size it wants. */
private const val DEFAULT_THUMB_BUCKET = 384

@Composable
fun GalleryApp(
    repository: MediaRepository,
    launchMode: LaunchMode,
    pendingOpen: ExternalOpen?,
    onExternalHandled: () -> Unit,
    onPicked: (MediaItem) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current

    var selected by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf<FolderRoute?>(null) }
    var viewer by remember { mutableStateOf<ViewerRoute?>(null) }

    // True from the very first frame when another app started this one, so the
    // tabs are never drawn on the way to the photo.
    var resolvingExternal by remember { mutableStateOf(pendingOpen != null) }

    var access by remember { mutableStateOf(context.mediaAccess()) }
    var manageMedia by remember { mutableStateOf(context.canManageMedia()) }
    val library by repository.state.collectAsStateWithLifecycle()
    val libraryItems = (library as? LibraryState.Ready)?.items.orEmpty()

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

    val unknownFolder = stringResource(R.string.album_unknown)

    // A photo arriving from another app. It no longer waits for the library:
    // the file and its folder are looked up directly, which takes a few tens of
    // milliseconds instead of however long indexing five thousand rows takes.
    LaunchedEffect(pendingOpen) {
        val open = pendingOpen ?: return@LaunchedEffect
        resolvingExternal = true

        val resolved = repository.resolveExternal(open.uri, open.declaredType)
        folder = resolved.bucketId?.let { bucket ->
            FolderRoute(
                bucketId = bucket,
                title = resolved.folderName.ifBlank { unknownFolder },
                fromExternal = true,
            )
        }
        viewer = ViewerRoute(
            itemId = resolved.items.getOrNull(resolved.index)?.id ?: -1,
            bucketId = resolved.bucketId,
            thumbBucketPx = DEFAULT_THUMB_BUCKET,
            items = resolved.items,
        )

        resolvingExternal = false
        onExternalHandled()
    }

    val folderItems = folder?.let { libraryItems.inFolder(it.bucketId) }.orEmpty()

    val viewerItems = viewer?.let { route ->
        when {
            // The folder straight from the library once it has one; the answer
            // found ahead of it only until then.
            route.bucketId != null ->
                libraryItems.inFolder(route.bucketId).ifEmpty { route.items.orEmpty() }
            route.items != null -> route.items
            else -> libraryItems
        }
    }.orEmpty()

    val viewerIndex = viewer?.let { route ->
        if (viewerItems.none { it.id == route.itemId }) -1 else viewerItems.indexOfId(route.itemId)
    } ?: -1

    val openItem: (MediaItem, Int) -> Unit = { item, bucket ->
        if (launchMode == LaunchMode.PICK) {
            onPicked(item)
        } else {
            viewer = ViewerRoute(
                itemId = item.id,
                bucketId = if (folder != null) item.bucketId else null,
                thumbBucketPx = bucket,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            // Composition and layout are kept so the grid holds its scroll
            // position, but there is no point drawing a thousand tiles under
            // something opaque.
            modifier = Modifier.drawWithContent {
                if (!resolvingExternal && folder == null && viewerIndex < 0) drawContent()
            },
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
                targetState = Tab.entries[selected],
                transitionSpec = {
                    // A fade with a whisper of scale: tabs are siblings, so
                    // nothing should slide in from a direction implying hierarchy.
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
                        onOpenSettings = {
                            context.startActivity(appSettingsIntent(context.packageName))
                        },
                    ) {
                        PhotosScreen(state = library, onOpen = openItem)
                    }

                    // Settings never sits behind the gate: it is where a person
                    // goes to understand why the rest of the app is asking for
                    // anything.
                    Tab.SETTINGS -> SettingsScreen(
                        access = access,
                        canManageMedia = manageMedia,
                        onOpenSettings = {
                            context.startActivity(appSettingsIntent(context.packageName))
                        },
                        onRequestManageMedia = {
                            context.startActivity(manageMediaIntent(context.packageName))
                        },
                    )

                    else -> PlaceholderScreen(current)
                }
            }
        }

        folder?.let { route ->
            AlbumScreen(
                title = route.title,
                items = folderItems,
                onBack = {
                    // Arrived here from another app's photo: going back a second
                    // time leaves the gallery rather than dropping into a
                    // timeline nobody asked for.
                    if (route.fromExternal) onFinish() else folder = null
                },
                onOpen = openItem,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .safeDrawingPadding()
                    .drawWithContent { if (viewerIndex < 0) drawContent() },
            )
        }

        // The gap between another app handing over a photo and the photo being
        // ready. Black rather than the timeline: a flash of the gallery on the
        // way to a picture reads as the wrong app opening.
        if (resolvingExternal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        if (viewerIndex >= 0) {
            ViewerScreen(
                items = viewerItems,
                startIndex = viewerIndex,
                thumbBucketPx = viewer?.thumbBucketPx ?: DEFAULT_THUMB_BUCKET,
                onClose = { viewer = null },
            )
        } else if (folder == null && selected != 0) {
            // Back from any tab other than the first returns to the timeline
            // before it leaves the app.
            BackHandler { selected = 0 }
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
