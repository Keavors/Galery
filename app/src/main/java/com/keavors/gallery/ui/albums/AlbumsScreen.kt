package com.keavors.gallery.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.keavors.gallery.data.AlbumSource
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaThumb
import com.keavors.gallery.data.folderAlbums
import com.keavors.gallery.data.thumbnailCacheKey

/** Cover art is asked for at this size whatever the screen width. */
private const val COVER_BUCKET = 384
private const val ALBUM_COLUMNS = 2

/**
 * Every album on the device.
 *
 * Folders first by how recently each was used, with the questions that reach
 * across all of them — favourites, videos, the trash — pinned above. A folder
 * touched this morning belongs at the top whatever it is called, which is why
 * the list is not alphabetical.
 */
@Composable
fun AlbumsScreen(
    items: List<MediaItem>,
    trashCount: Int,
    onOpenAlbum: (source: AlbumSource, title: String) -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folders = items.folderAlbums()
    val favourites = items.filter { it.isFavorite }
    val videos = items.filter { it.isVideo }

    val favouritesTitle = stringResource(R.string.album_favourites)
    val videosTitle = stringResource(R.string.album_videos)

    LazyVerticalGrid(
        columns = GridCells.Fixed(ALBUM_COLUMNS),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "favourites") {
            AlbumCard(
                title = favouritesTitle,
                count = favourites.size,
                cover = favourites.firstOrNull(),
                fallbackIcon = R.drawable.ic_heart,
                onClick = { onOpenAlbum(AlbumSource.Favourites, favouritesTitle) },
            )
        }
        item(key = "videos") {
            AlbumCard(
                title = videosTitle,
                count = videos.size,
                cover = videos.firstOrNull(),
                fallbackIcon = R.drawable.ic_play,
                onClick = { onOpenAlbum(AlbumSource.Videos, videosTitle) },
            )
        }
        item(key = "trash") {
            AlbumCard(
                title = stringResource(R.string.tab_trash),
                count = trashCount,
                cover = null,
                fallbackIcon = R.drawable.ic_tab_trash,
                onClick = onOpenTrash,
            )
        }

        items(folders, key = { it.bucketId }) { folder ->
            AlbumCard(
                title = folder.name,
                count = folder.count,
                cover = folder.cover,
                fallbackIcon = R.drawable.ic_tab_albums,
                onClick = { onOpenAlbum(AlbumSource.Folder(folder.bucketId), folder.name) },
            )
        }
    }
}

@Composable
private fun AlbumCard(
    title: String,
    count: Int,
    cover: MediaItem?,
    fallbackIcon: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(MediaThumb(cover.id, cover.isVideo))
                        .size(COVER_BUCKET)
                        // The same name the grid uses, so an album cover costs
                        // nothing once its photo has been on screen.
                        .memoryCacheKey(thumbnailCacheKey(cover.id, COVER_BUCKET))
                        .placeholderMemoryCacheKey(thumbnailCacheKey(cover.id, COVER_BUCKET))
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // An empty album still needs a face, and a blank tile reads as a
                // picture that failed to load rather than an album with nothing
                // in it yet.
                Icon(
                    painter = painterResource(fallbackIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }

            if (cover != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.06f)),
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
