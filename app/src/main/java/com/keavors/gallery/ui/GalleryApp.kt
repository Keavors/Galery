package com.keavors.gallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.keavors.gallery.BuildConfig
import coil3.SingletonImageLoader
import com.keavors.gallery.data.AlbumPreferences
import com.keavors.gallery.data.GallerySettings
import com.keavors.gallery.data.SettingsStore
import android.widget.Toast
import com.keavors.gallery.data.filteredFor
import com.keavors.gallery.data.formatBytes
import com.keavors.gallery.data.sortedFor
import com.keavors.gallery.data.AlbumSource
import com.keavors.gallery.data.AlbumStore
import android.app.Activity
import com.keavors.gallery.data.VaultStore
import com.keavors.gallery.data.canAuthenticate
import com.keavors.gallery.data.deleteRequestFor
import com.keavors.gallery.data.canManageMedia
import com.keavors.gallery.data.inAlbum
import com.keavors.gallery.data.indexOfId
import com.keavors.gallery.data.mediaAccess
import com.keavors.gallery.data.mediaPermissions
import com.keavors.gallery.data.provisionalItem
import com.keavors.gallery.data.awaitPreview
import com.keavors.gallery.data.DEFAULT_THUMB_BUCKET
import com.keavors.gallery.data.UNKNOWN_ID
import com.keavors.gallery.ui.album.AlbumScreen
import com.keavors.gallery.ui.albums.AlbumsScreen
import com.keavors.gallery.ui.common.PlaceholderScreen
import com.keavors.gallery.ui.editor.EditorScreen
import com.keavors.gallery.ui.editor.VideoTrimScreen
import com.keavors.gallery.ui.lock.LockScreen
import com.keavors.gallery.ui.permission.MediaGate
import com.keavors.gallery.ui.photos.AlbumActions
import com.keavors.gallery.ui.photos.PhotosScreen
import com.keavors.gallery.ui.common.rememberMediaWriter
import com.keavors.gallery.ui.settings.SettingsScreen
import com.keavors.gallery.ui.trash.TrashScreen
import com.keavors.gallery.ui.viewer.ViewerScreen
import kotlinx.coroutines.launch

/** Duration of the cross-fade between tabs, ms. Kept short: tabs are cheap. */
private const val TAB_FADE_IN = 220
private const val TAB_FADE_OUT = 140

@Composable
fun GalleryApp(
    settings: GallerySettings,
    settingsStore: SettingsStore,
    repository: MediaRepository,
    albumStore: AlbumStore,
    vaultStore: VaultStore,
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
    // Bumped when the viewer must be rebuilt on its anchor — after an editor
    // closes — because a pager holds on to its page number while the list
    // underneath it shifts.
    var viewerEpoch by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MediaItem?>(null) }


    val activity = LocalActivity.current
    val canLock = remember(activity) { activity?.canAuthenticate() == true }
    // Locked until proven otherwise, and only while the setting says so. Held
    // outside the lock screen so that leaving the app and coming back locks it
    // again — a lock that only asks once is a lock on a door left open.
    var unlocked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(settings.appLock) {
        onPauseOrDispose { if (settings.appLock) unlocked = false }
    }

    var access by remember { mutableStateOf(context.mediaAccess()) }
    var manageMedia by remember { mutableStateOf(context.canManageMedia()) }
    val library by repository.state.collectAsStateWithLifecycle()
    val trash by repository.trash.collectAsStateWithLifecycle()
    val writer = rememberMediaWriter(managesMedia = manageMedia)
    val albumPrefs by albumStore.preferences.collectAsStateWithLifecycle(AlbumPreferences())
    val vaultEntries by vaultStore.entries.collectAsStateWithLifecycle(emptyList())
    val vaultItems = remember(vaultEntries) { vaultEntries.map { vaultStore.asMediaItem(it) } }
    var vaultUnlocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Sorting and filtering happen here, once, so every screen downstream —
    // timeline, albums, viewer — is looking at the same library in the same
    // order, and paging never disagrees with the grid it was opened from.
    val rawItems = (library as? LibraryState.Ready)?.items.orEmpty()
    val libraryItems = remember(rawItems, settings.sortBy, settings.sortOrder, settings.showVideos) {
        rawItems.filteredFor(settings).sortedFor(settings.sortBy, settings.sortOrder)
    }

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
    //
    // Re-reading the library is held back while a photograph from another app is
    // on its way. Five thousand rows and one photograph want the same disk at
    // the same moment, and the photograph is the one somebody is waiting for.
    // The key is what starts it afterwards: it flips the instant the photo has
    // been placed, and the effect runs again.
    val opening = pendingOpen != null
    LifecycleResumeEffect(opening) {
        access = context.mediaAccess()
        manageMedia = context.canManageMedia()
        if (!opening) repository.refresh()
        onPauseOrDispose { }
    }

    val unknownFolder = stringResource(R.string.album_unknown)
    val vaultTitle = stringResource(R.string.vault_title)
    var cacheSummary by remember { mutableStateOf("") }

    val importFailed = stringResource(R.string.settings_import_failed)
    val restoredNote = stringResource(R.string.vault_restored)
    val editorSaved = stringResource(R.string.editor_saved)
    val editorFailed = stringResource(R.string.editor_save_failed)
    val editorSavedBare = stringResource(R.string.editor_saved_without_metadata)
    val trimFailed = stringResource(R.string.trim_failed)
    val restoreFailedNote = stringResource(R.string.vault_restore_failed)
    val savedNote = stringResource(R.string.settings_saved)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = readSettingsFrom(context, settingsStore, uri)
            Toast.makeText(context, if (ok) savedNote else importFailed, Toast.LENGTH_SHORT).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            writeSettingsTo(context, settingsStore, uri)
            Toast.makeText(context, savedNote, Toast.LENGTH_SHORT).show()
        }
    }

    // The cache is only measured while the settings tab is being looked at;
    // asking on every launch would read the disk for a line nobody has opened.
    LaunchedEffect(selected, cacheSummary) {
        if (selected == Tab.SETTINGS.ordinal && cacheSummary.isEmpty()) {
            val bytes = SingletonImageLoader.get(context).diskCache?.size ?: 0L
            cacheSummary = formatBytes(bytes)
        }
    }

    // A photo arriving from another app, on screen before anything has been
    // asked of anybody.
    //
    // Worked out while composing rather than in an effect, and that is the whole
    // point: an effect runs after a frame has been drawn, and that frame is
    // whatever was on screen when the app was last used — which is how an album
    // someone had open flashed past on the way to a photograph. There was
    // nothing to wait for in the first place. The uri that arrived is the
    // photograph.
    val arriving = remember(pendingOpen) {
        pendingOpen?.let { open ->
            val item = provisionalItem(open.uri, open.declaredType)
            ViewerRoute(
                itemId = item.id,
                source = null,
                thumbBucketPx = DEFAULT_THUMB_BUCKET,
                items = listOf(item),
                resolved = false,
            )
        }
    }

    // And the considered answer, which arrives a moment later and brings the
    // neighbours with it. Finding the file and reading its folder is two narrow
    // queries rather than an index of five thousand rows, but two queries is
    // still not nothing, and nothing is what opening a photograph has to cost.
    LaunchedEffect(pendingOpen) {
        val open = pendingOpen ?: return@LaunchedEffect

        val resolved = repository.resolveExternal(open.uri, open.declaredType)
        val found = resolved.items.getOrNull(resolved.index)

        // Placing the photograph rebuilds the viewer on its folder, and a rebuilt
        // page reads its placeholder out of memory once and never asks again. So
        // the thumbnail has to be in memory before that happens, not after —
        // otherwise a uri that named no row goes black at the very moment the
        // neighbours arrive. It is free whenever the picture is already up.
        found?.let { context.awaitPreview(it, DEFAULT_THUMB_BUCKET) }

        val source = resolved.bucketId?.let { AlbumSource.Folder(it) }
        folder = source?.let {
            FolderRoute(
                source = it,
                title = resolved.folderName.ifBlank { unknownFolder },
                // The stack an intent builds is two steps and no more: the
                // photograph, the folder it lives in, and then out.
                leavesTo = null,
            )
        }
        viewer = ViewerRoute(
            itemId = found?.id ?: UNKNOWN_ID,
            source = source,
            thumbBucketPx = DEFAULT_THUMB_BUCKET,
            items = resolved.items,
        )

        onExternalHandled()
    }

    // What is on screen: the picture straight off the intent until the library
    // has been asked about it, and the library's answer from then on.
    val route = arriving ?: viewer

    val folderItems = when (folder?.source) {
        null -> emptyList()
        AlbumSource.Vault -> vaultItems
        else -> libraryItems.inAlbum(folder!!.source, albumPrefs.userAlbums)
    }

    val viewerItems = route?.let { shown ->
        when {
            // The folder straight from the library once it has one; the answer
            // found ahead of it only until then.
            shown.source == AlbumSource.Vault -> vaultItems
            shown.source != null ->
                libraryItems.inAlbum(shown.source, albumPrefs.userAlbums)
                    .ifEmpty { shown.items.orEmpty() }
            shown.items != null -> shown.items
            else -> libraryItems
        }
    }.orEmpty()

    val viewerIndex = route?.let { shown ->
        if (viewerItems.none { it.id == shown.itemId }) -1 else viewerItems.indexOfId(shown.itemId)
    } ?: -1

    // Album actions for the timeline. The remove action is filled in only when
    // the grid on screen is an album someone made — a photo cannot be removed
    // from a folder, only moved out of it, which is a different thing entirely.
    fun albumActions(removableFrom: AlbumSource?) = AlbumActions(
        userAlbums = albumPrefs.userAlbums,
        onAddTo = { albumId, ids -> scope.launch { albumStore.addToUserAlbum(albumId, ids) } },
        onCreateWith = { name, ids -> scope.launch { albumStore.createUserAlbum(name, ids) } },
        onRemoveFrom = (removableFrom as? AlbumSource.User)?.let { album ->
            { ids: Set<Long> -> scope.launch { albumStore.removeFromUserAlbum(album.albumId, ids) } }
        },
    )

    // Copies waiting on the system to confirm that the originals may go. If it
    // refuses, they are thrown away again: a copy kept alongside an original
    // that never left is a duplicate nobody asked for.
    var pendingVault by remember { mutableStateOf<List<com.keavors.gallery.data.VaultEntry>>(emptyList()) }
    val vaultDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val awaiting = pendingVault
        pendingVault = emptyList()
        if (result.resultCode != Activity.RESULT_OK) {
            scope.launch { awaiting.forEach { vaultStore.discard(it) } }
        }
    }

    val hideItems: (List<MediaItem>) -> Unit = { chosen ->
        scope.launch {
            // Copy first, record second, delete last. Any other order risks the
            // one outcome that cannot be undone.
            val taken = chosen.mapNotNull { item -> vaultStore.take(item)?.let { item to it } }
            if (taken.isNotEmpty()) {
                pendingVault = taken.map { it.second }
                vaultDeleteLauncher.launch(deleteRequestFor(context, taken.map { it.first }))
            }
        }
    }

    // Back undoes exactly one thing, whatever that thing was: the photograph,
    // then the folder, then the tab, then the app. So leaving a folder returns
    // to the tab it was opened from — the albums tab, for every folder anybody
    // navigated to — and lands on the timeline only for a folder that was
    // reached from the timeline. A folder with no way in at all was built by an
    // intent from another app, and there back leaves the gallery.
    fun leaveFolder() {
        val leavesTo = folder?.leavesTo
        folder = null
        if (leavesTo == null) onFinish() else selected = leavesTo.ordinal
    }

    // The one way out of either editor, for every kind of save on every kind
    // of file. The library is re-read first, so the list under the viewer is
    // already in its final order; then the viewer is rebuilt anchored on the
    // very item that was being edited. Without this the outcome depended on
    // timing — a copy appearing above shifts every index under an open pager,
    // and a pager keeps its page number, not its photograph — so it sometimes
    // came back to the same picture and sometimes to the grid.
    fun closeEditorOnto(subject: MediaItem) {
        scope.launch {
            repository.reloadNow()
            viewer = viewer?.copy(itemId = subject.id)
            viewerEpoch++
            editing = null
        }
    }

    val openItem: (MediaItem, Int) -> Unit = { item, bucket ->
        if (launchMode == LaunchMode.PICK) {
            onPicked(item)
        } else {
            viewer = ViewerRoute(
                itemId = item.id,
                source = folder?.source,
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
                if (folder == null && viewerIndex < 0) drawContent()
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
                    if (!settings.animations) {
                        // Not a faster fade — none at all. Somebody who turns
                        // animations off wants the next screen, not a quicker
                        // way of being shown it arriving.
                        fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
                    } else {
                        // A fade with a whisper of scale: tabs are siblings, so
                        // nothing should slide in from a direction implying
                        // hierarchy.
                        (fadeIn(tween(TAB_FADE_IN)) +
                            scaleIn(tween(TAB_FADE_IN), initialScale = 0.985f))
                            .togetherWith(fadeOut(tween(TAB_FADE_OUT)))
                    }
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
                        PhotosScreen(
                            items = libraryItems,
                            loading = library !is LibraryState.Ready,
                            settings = settings,
                            writer = writer,
                            albumActions = albumActions(removableFrom = null),
                            onHide = hideItems,
                            onOpen = openItem,
                        )
                    }

                    Tab.TRASH -> MediaGate(
                        access = access,
                        onRequest = { permissionLauncher.launch(mediaPermissions) },
                        onOpenSettings = {
                            context.startActivity(appSettingsIntent(context.packageName))
                        },
                    ) {
                        TrashScreen(items = trash, writer = writer)
                    }

                    // Settings never sits behind the gate: it is where a person
                    // goes to understand why the rest of the app is asking for
                    // anything.
                    Tab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onChange = { updated -> scope.launch { settingsStore.update { updated } } },
                        access = access,
                        canManageMedia = manageMedia,
                        versionName = BuildConfig.VERSION_NAME,
                        cacheSummary = cacheSummary,
                        canLock = canLock,
                        onOpenSystemSettings = {
                            context.startActivity(appSettingsIntent(context.packageName))
                        },
                        onRequestManageMedia = {
                            context.startActivity(manageMediaIntent(context.packageName))
                        },
                        onClearCache = {
                            SingletonImageLoader.get(context).memoryCache?.clear()
                            SingletonImageLoader.get(context).diskCache?.clear()
                            cacheSummary = ""
                        },
                        onExport = { exportLauncher.launch("gallery-settings.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        onReset = { scope.launch { settingsStore.reset() } },
                    )

                    Tab.ALBUMS -> MediaGate(
                        access = access,
                        onRequest = { permissionLauncher.launch(mediaPermissions) },
                        onOpenSettings = {
                            context.startActivity(appSettingsIntent(context.packageName))
                        },
                    ) {
                        AlbumsScreen(
                            items = libraryItems,
                            vaultCount = vaultItems.size,
                            prefs = albumPrefs,
                            onOpenAlbum = { source, title ->
                                folder = FolderRoute(source, title, leavesTo = Tab.ALBUMS)
                            },
                            trashCount = trash.size,
                            onOpenTrash = { selected = Tab.TRASH.ordinal },
                            onOpenVault = {
                                // The vault asks every time it is opened, not
                                // once per run: it is the only thing here worth
                                // locking, so it is the one thing not trusted to
                                // stay unlocked.
                                vaultUnlocked = false
                                folder = FolderRoute(
                                    source = AlbumSource.Vault,
                                    title = vaultTitle,
                                    leavesTo = Tab.ALBUMS,
                                )
                            },
                            onTogglePin = { scope.launch { albumStore.togglePinned(it) } },
                            onSetHidden = { source, hidden ->
                                scope.launch { albumStore.setHidden(source, hidden) }
                            },
                            onCreateAlbum = { name ->
                                scope.launch { albumStore.createUserAlbum(name) }
                            },
                            onRenameAlbum = { id, name ->
                                scope.launch { albumStore.renameUserAlbum(id, name) }
                            },
                            onDeleteAlbum = { id -> scope.launch { albumStore.deleteUserAlbum(id) } },
                        )
                    }

                    else -> PlaceholderScreen(current)
                }
            }
        }

        // Nothing of the vault is drawn until the phone says who is holding it.
        if (folder?.source == AlbumSource.Vault && !vaultUnlocked) {
            LockScreen(
                title = stringResource(R.string.vault_title),
                subtitle = stringResource(R.string.vault_subtitle),
                onUnlocked = { vaultUnlocked = true },
                onGiveUp = ::leaveFolder,
                modifier = Modifier.fillMaxSize(),
            )
        } else folder?.let { route ->
            AlbumScreen(
                title = route.title,
                items = folderItems,
                settings = settings,
                writer = writer,
                albumActions = albumActions(removableFrom = route.source),
                onHide = hideItems,
                onBack = ::leaveFolder,
                onOpen = openItem,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .safeDrawingPadding()
                    .drawWithContent { if (viewerIndex < 0) drawContent() },
            )
        }

        if (viewerIndex >= 0) {
            // Rebuilt once, when the photograph from another app stops being
            // a single picture and becomes a page in its folder. A pager
            // keeps the page it is on, and page zero of one photo is not
            // page forty of a folder — without this the picture would change
            // under the finger a moment after opening.
            key(route?.resolved, viewerEpoch) {
                ViewerScreen(
                    items = viewerItems,
                    startIndex = viewerIndex,
                    thumbBucketPx = route?.thumbBucketPx ?: DEFAULT_THUMB_BUCKET,
                    settings = settings,
                    writer = writer,
                    onEdit = { editing = it },
                    onRestoreFromVault = { item ->
                        scope.launch {
                            // Saying nothing was the real bug here: an operation
                            // that can fail has to admit it, or it looks like a
                            // button that does nothing at all.
                            val entry = vaultEntries.firstOrNull { -it.id == item.id }
                            val ok = entry != null && vaultStore.restore(entry)
                            Toast.makeText(
                                context,
                                if (ok) restoredNote else restoreFailedNote,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    // Only offered where there is an album to be the cover of.
                    onSetCover = route?.source?.let { source ->
                        { itemId: Long -> scope.launch { albumStore.setCover(source, itemId) } }
                    },
                    onClose = { viewer = null },
                    // Left in composition while the editor is open so that closing
                    // the editor puts the same photo back on the same page, but not
                    // drawn: the editor covers it completely.
                    modifier = Modifier.drawWithContent { if (editing == null) drawContent() },
                )
            }
        } else if (folder == null && selected != 0) {
            // Back from any tab other than the first returns to the timeline
            // before it leaves the app.
            BackHandler { selected = 0 }
        }

        // After the viewer rather than before it. The editor is opened from a
        // photo and has to cover it; composed later it is also the one that
        // hears the back press, so leaving the editor returns to the photo
        // instead of closing both at once.
        editing?.let { subject ->
            if (subject.isVideo) {
                VideoTrimScreen(
                    item = subject,
                    onSaved = {
                        Toast.makeText(context, editorSaved, Toast.LENGTH_SHORT).show()
                        closeEditorOnto(subject)
                    },
                    onFailed = { reason ->
                        // The encoder's own words. An export can fail for a
                        // dozen unrelated reasons and only the one it hit is any
                        // use to whoever has to decide what to do next.
                        Toast.makeText(
                            context,
                            trimFailed.format(reason),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onClose = { closeEditorOnto(subject) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EditorScreen(
                    item = subject,
                    jpegQuality = settings.jpegQuality,
                    onSaved = { keptMetadata ->
                        // A photograph that came back without its date and place
                        // is still saved, but it is not what was asked for
                        // either, and finding out months later is too late.
                        Toast.makeText(
                            context,
                            if (keptMetadata) editorSaved else editorSavedBare,
                            if (keptMetadata) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                        ).show()
                        closeEditorOnto(subject)
                    },
                    // Left open on purpose: the edits are still there and the
                    // second attempt costs a tap.
                    onFailed = { reason ->
                        Toast.makeText(
                            context,
                            editorFailed.format(reason),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onClose = { closeEditorOnto(subject) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // In front of everything else, including a photo another app sent over:
        // arriving from outside must not be a way past the lock. Which is why it
        // is composed last — anything after it would be drawn over it.
        if (settings.appLock && canLock && !unlocked) {
            LockScreen(
                title = stringResource(R.string.lock_title),
                subtitle = stringResource(R.string.lock_subtitle),
                onUnlocked = { unlocked = true },
                onGiveUp = onFinish,
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
