package com.keavors.gallery.ui.photos

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R

/**
 * What appears over the grid once something is selected.
 *
 * Deliberately the same three actions as the viewer's bottom bar: whether one
 * photo or forty are in hand, the things that can be done with them are the
 * same, and they should not be named differently in the two places.
 */
@Composable
fun SelectionBar(
    count: Int,
    allFavorite: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onAddToAlbum: () -> Unit,
    onRemoveFromAlbum: (() -> Unit)?,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.trash_selected, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    painter = painterResource(
                        if (allFavorite) R.drawable.ic_heart else R.drawable.ic_heart_outline
                    ),
                    contentDescription = stringResource(
                        if (allFavorite) R.string.action_unfavorite else R.string.action_favorite
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onAddToAlbum) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.album_add_to),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            // Only inside an album someone made: nothing can be removed from a
            // folder, because being in one is a fact about the disk.
            onRemoveFromAlbum?.let { remove ->
                IconButton(onClick = remove) {
                    Icon(
                        painter = painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(R.string.album_remove_from),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = stringResource(R.string.viewer_share),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onHide) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = stringResource(R.string.vault_hide),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_tab_trash),
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
