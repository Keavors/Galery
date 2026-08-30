package com.keavors.gallery.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.UserAlbum

/**
 * What a selection of photos can be done to, album-wise.
 *
 * Bundled rather than passed as four separate callbacks: the timeline is used
 * from three places, and each would otherwise have to spell out the same list
 * with only [onRemoveFrom] differing.
 *
 * @param onRemoveFrom present only inside an album someone made. Nothing can be
 *   removed from a folder, because being in one is a fact about the disk.
 */
data class AlbumActions(
    val userAlbums: List<UserAlbum>,
    val onAddTo: (albumId: Long, itemIds: Set<Long>) -> Unit,
    val onCreateWith: (name: String, itemIds: Set<Long>) -> Unit,
    val onRemoveFrom: ((itemIds: Set<Long>) -> Unit)? = null,
)

/** Picks which album a selection is going into, or starts a new one for it. */
@Composable
fun ChooseAlbumDialog(
    albums: List<UserAlbum>,
    onChoose: (Long) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_add_to)) },
        text = {
            Column {
                albums.forEach { album ->
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(album.id) }
                            .padding(vertical = 12.dp),
                    )
                }
                if (albums.isEmpty()) {
                    Text(
                        text = stringResource(R.string.albums_none_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) { Text(stringResource(R.string.albums_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
