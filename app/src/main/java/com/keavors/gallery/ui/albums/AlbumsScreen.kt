package com.keavors.gallery.ui.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.keavors.gallery.R
import com.keavors.gallery.data.AlbumPreferences
import com.keavors.gallery.data.AlbumSource
import com.keavors.gallery.data.FolderAlbum
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaThumb
import com.keavors.gallery.data.key
import com.keavors.gallery.data.pinnedFirst
import com.keavors.gallery.data.isRenamableFolder
import com.keavors.gallery.data.thumbnailCacheKey
import com.keavors.gallery.data.withoutHidden
import com.keavors.gallery.ui.common.ConfirmDialog
import com.keavors.gallery.ui.common.TextPromptDialog

/** Cover art is asked for at this size whatever the screen width. */
private const val COVER_BUCKET = 384
private const val ALBUM_COLUMNS = 2

/** One card on the albums screen, whatever kind of album it stands for. */
private data class AlbumCardModel(
    val source: AlbumSource,
    val title: String,
    val count: Int,
    val cover: MediaItem?,
    val fallbackIcon: Int,
    val renamable: Boolean = false,
    val deletable: Boolean = false,
)

/**
 * Every album on the device.
 *
 * Folders are ordered by how recently each was used rather than alphabetically:
 * the folder shot in this morning belongs above the one last touched in 2019,
 * whatever the two are called. Pinning overrides that; hiding takes an album out
 * of the list without touching a single file.
 *
 * A long press on any card is where the choices live. Nothing is on the cards
 * themselves — a row of buttons under every album would bury the covers, which
 * are the only thing here worth looking at.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    items: List<MediaItem>,
    folders: List<FolderAlbum>,
    trashCount: Int,
    vaultCount: Int,
    prefs: AlbumPreferences,
    onOpenAlbum: (source: AlbumSource, title: String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenVault: () -> Unit,
    onTogglePin: (AlbumSource) -> Unit,
    onSetHidden: (AlbumSource, Boolean) -> Unit,
    onRenameAlbum: (AlbumSource, String) -> Unit,
    onDeleteAlbum: (AlbumSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHidden by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AlbumCardModel?>(null) }
    var deleting by remember { mutableStateOf<AlbumCardModel?>(null) }

    val byId = remember(items) { items.associateBy { it.id } }
    fun coverFor(source: AlbumSource, fallback: MediaItem?): MediaItem? =
        prefs.coverId(source)?.let { byId[it] } ?: fallback

    val favourites = items.filter { it.isFavorite }
    val videos = items.filter { it.isVideo }
    val virtual = listOf(
        AlbumCardModel(
            source = AlbumSource.Favourites,
            title = stringResource(R.string.album_favourites),
            count = favourites.size,
            cover = coverFor(AlbumSource.Favourites, favourites.firstOrNull()),
            fallbackIcon = R.drawable.ic_heart,
        ),
        AlbumCardModel(
            source = AlbumSource.Videos,
            title = stringResource(R.string.album_videos),
            count = videos.size,
            cover = coverFor(AlbumSource.Videos, videos.firstOrNull()),
            fallbackIcon = R.drawable.ic_play,
        ),
    )

    val folderCards = folders
        .withoutHidden(prefs, showHidden)
        .pinnedFirst(prefs)
        .map { folder ->
            val source = AlbumSource.Folder(folder.bucketId)
            AlbumCardModel(
                source = source,
                // Files in the root of the storage belong to no folder at all,
                // and a card with no name under it looks like a bug.
                title = folder.name.ifBlank { stringResource(R.string.album_root) },
                count = folder.count,
                cover = coverFor(source, folder.cover),
                fallbackIcon = R.drawable.ic_tab_albums,
                // A folder is a place on the disk, so both of these really
                // happen: renaming one moves every file in it, and deleting one
                // sends every file in it to the trash. The card says so before
                // either goes ahead.
                renamable = isRenamableFolder(folder.path),
                deletable = true,
            )
        }

    val cards = virtual.filter { showHidden || !prefs.isHidden(it.source) } + folderCards
    val anythingHidden = prefs.hidden.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 6.dp, top = 10.dp),
        ) {
            Text(
                text = cards.size.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (anythingHidden) {
                TextButton(onClick = { showHidden = !showHidden }) {
                    Text(
                        stringResource(
                            if (showHidden) R.string.albums_hide_hidden else R.string.albums_show_hidden
                        )
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(ALBUM_COLUMNS),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(cards, key = { it.source.key }) { card ->
                AlbumCard(
                    card = card,
                    pinned = prefs.isPinned(card.source),
                    hidden = prefs.isHidden(card.source),
                    onOpen = { onOpenAlbum(card.source, card.title) },
                    onTogglePin = { onTogglePin(card.source) },
                    onToggleHidden = { onSetHidden(card.source, !prefs.isHidden(card.source)) },
                    onRename = { renaming = card },
                    // An album is files on the disk, so deleting one is asked
                    // about before anything moves.
                    onDelete = { deleting = card },
                )
            }

            item(key = "vault") {
                AlbumCard(
                    card = AlbumCardModel(
                        source = AlbumSource.Vault,
                        title = stringResource(R.string.vault_title),
                        count = vaultCount,
                        cover = null,
                        fallbackIcon = R.drawable.ic_lock,
                    ),
                    pinned = false,
                    hidden = false,
                    menuEnabled = false,
                    onOpen = onOpenVault,
                    onTogglePin = {},
                    onToggleHidden = {},
                    onRename = {},
                    onDelete = {},
                )
            }

            item(key = "trash") {
                AlbumCard(
                    card = AlbumCardModel(
                        // The trash is not an album and cannot be pinned or
                        // hidden; it is here because this is where people look
                        // for it.
                        source = AlbumSource.Favourites,
                        title = stringResource(R.string.tab_trash),
                        count = trashCount,
                        cover = null,
                        fallbackIcon = R.drawable.ic_tab_trash,
                    ),
                    pinned = false,
                    hidden = false,
                    menuEnabled = false,
                    onOpen = onOpenTrash,
                    onTogglePin = {},
                    onToggleHidden = {},
                    onRename = {},
                    onDelete = {},
                )
            }
        }
    }

    renaming?.let { card ->
        TextPromptDialog(
            title = stringResource(R.string.albums_rename),
            initial = card.title,
            confirm = stringResource(R.string.albums_rename_confirm),
            onConfirm = {
                renaming = null
                onRenameAlbum(card.source, it)
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { card ->
        ConfirmDialog(
            title = stringResource(R.string.folder_delete_title),
            body = stringResource(R.string.folder_delete_body, card.count),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                deleting = null
                onDeleteAlbum(card.source)
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    card: AlbumCardModel,
    pinned: Boolean,
    hidden: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    menuEnabled: Boolean = true,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = { if (menuEnabled) menuOpen = true },
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val cover = card.cover
            if (cover != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(MediaThumb(cover.id, cover.isVideo))
                        .size(COVER_BUCKET)
                        // The same name the grid uses, so a cover costs nothing
                        // once its photo has been on screen.
                        .memoryCacheKey(thumbnailCacheKey(cover.id, COVER_BUCKET))
                        .placeholderMemoryCacheKey(thumbnailCacheKey(cover.id, COVER_BUCKET))
                        .build(),
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // An empty album still needs a face: a blank tile reads as a
                // picture that failed to load rather than an album with nothing
                // in it yet.
                Icon(
                    painter = painterResource(card.fallbackIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }

            if (pinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp),
                )
            }
            if (hidden) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                )
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (pinned) R.string.album_unpin else R.string.album_pin))
                    },
                    onClick = {
                        menuOpen = false
                        onTogglePin()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (hidden) R.string.album_unhide else R.string.album_hide))
                    },
                    onClick = {
                        menuOpen = false
                        onToggleHidden()
                    },
                )
                if (card.renamable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.albums_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                }
                if (card.deletable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.album_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }

        Text(
            text = card.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Text(
            text = card.count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
