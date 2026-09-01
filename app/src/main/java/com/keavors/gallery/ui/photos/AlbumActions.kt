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
import com.keavors.gallery.data.newFolderPath

/**
 * What a selection of photographs can be done to, album-wise.
 *
 * An album is a folder on the disk, so both of these really move files and every
 * other app on the phone sees that they have moved. That is the whole reason
 * there is no third thing here: an album this app kept to itself would be
 * invisible to everything else and would go when the app did.
 *
 * Bundled rather than passed as separate callbacks because the timeline is used
 * from three places and each would otherwise spell out the same list.
 */
data class AlbumActions(
    /** Every folder on the device, as somewhere photographs can be sent. */
    val folders: List<FolderAlbum> = emptyList(),
    /** Sends the chosen files to an album, by its relative path. */
    val onMoveTo: (relativePath: String, items: List<MediaItem>) -> Unit = { _, _ -> },
    val onCopyTo: (relativePath: String, items: List<MediaItem>) -> Unit = { _, _ -> },
)

/**
 * Picks the album a selection is being moved or copied into.
 *
 * Albums that exist and nothing else, plus the one way of making a new one. A
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
