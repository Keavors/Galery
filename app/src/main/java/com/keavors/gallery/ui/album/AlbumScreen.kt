package com.keavors.gallery.ui.album

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.GallerySettings
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaWriter
import com.keavors.gallery.ui.photos.AlbumActions
import com.keavors.gallery.ui.photos.TimelineScreen

/**
 * One folder on the device.
 *
 * The same timeline as the Photos tab, narrowed to a single folder — dates still
 * group inside it, and the zoom levels work the same. Reached two ways: opened
 * from inside the app, or arrived at from a photo another app sent over, which
 * is what "back" from that photo has to land on.
 */
@Composable
fun AlbumScreen(
    title: String,
    items: List<MediaItem>,
    settings: GallerySettings,
    writer: MediaWriter,
    albumActions: AlbumActions,
    onHide: (List<MediaItem>) -> Unit,
    onExportOut: (List<MediaItem>) -> Unit,
    onUndoableDelete: (List<MediaItem>) -> Unit,
    onBack: () -> Unit,
    onOpen: (item: MediaItem, thumbBucketPx: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.viewer_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = items.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TimelineScreen(
            items = items,
            settings = settings,
            searchable = true,
            writer = writer,
            albumActions = albumActions,
            onHide = onHide,
            onExportOut = onExportOut,
            onUndoableDelete = onUndoableDelete,
            onOpen = onOpen,
        )
    }
}
