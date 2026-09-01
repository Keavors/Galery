package com.keavors.gallery.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.ui.common.TextPromptDialog
import com.keavors.gallery.data.FolderAlbum
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.UserAlbum
import com.keavors.gallery.data.newFolderPath

/**
 * What a selection of photos can be done to, album-wise.
 *
 * Bundled rather than passed as six separate callbacks: the timeline is used
 * from three places, and each would otherwise have to spell out the same list
 * with only [onRemoveFrom] differing.
 *
 * The two halves of this are not the same kind of thing, and the wording in the
 * app keeps them apart. An album someone made is a list of ids and nothing else,
 * so adding to one and removing from one costs nothing and changes no file. A
 * folder is a place on the disk, so moving a photograph into one really moves
 * it, and every other app on the phone will see that it has moved.
 *
 * @param onRemoveFrom present only inside an album someone made. Nothing can be
 *   removed from a folder, because being in one is a fact about the disk.
 */
data class AlbumActions(
    val userAlbums: List<UserAlbum>,
    /** Every folder on the device, as somewhere photographs can be sent. */
    val folders: List<FolderAlbum> = emptyList(),
    val onAddTo: (albumId: Long, itemIds: Set<Long>) -> Unit,
    val onCreateWith: (name: String, itemIds: Set<Long>) -> Unit,
    val onRemoveFrom: ((itemIds: Set<Long>) -> Unit)? = null,
    /** Sends the chosen files to a folder, by its relative path. */
    val onMoveTo: (relativePath: String, items: List<MediaItem>) -> Unit = { _, _ -> },
    val onCopyTo: (relativePath: String, items: List<MediaItem>) -> Unit = { _, _ -> },
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

/**
 * Picks the folder a selection is being moved or copied into.
 *
 * Existing folders and nothing else, plus the one way of making a new one. A
 * free-hand path would let anybody put photographs somewhere Android does not
 * index, where no gallery on the phone — including this one — would ever show
 * them again.
 */
@Composable
fun ChooseFolderDialog(
    title: String,
    folders: List<FolderAlbum>,
    onChoose: (relativePath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }

    if (naming) {
        TextPromptDialog(
            title = stringResource(R.string.folder_new),
            initial = "",
            confirm = stringResource(R.string.albums_create_confirm),
            onConfirm = { name -> newFolderPath(name)?.let(onChoose) ?: onDismiss() },
            onDismiss = onDismiss,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                folders.forEach { folder ->
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(folder.path) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { naming = true }) {
                Text(stringResource(R.string.folder_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
