package com.keavors.gallery.ui.photos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R

/**
 * What appears over the grid once something is selected.
 *
 * The three that are always wanted — favourite, share, delete — are buttons, and
 * deliberately the same three as the viewer's bottom bar: whether one photo or
 * forty are in hand, the things that can be done with them are the same, and
 * they should not be named differently in the two places.
 *
 * The rest live behind the one menu at the end. Nine buttons do not fit across a
 * phone, and the ones that would have had to shrink to fit are the ones nobody
 * reaches for twice a day.
 */
@Composable
fun SelectionBar(
    count: Int,
    allFavorite: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onHide: (() -> Unit)?,
    onExportOut: (() -> Unit)?,
    onMoveToFolder: (() -> Unit)?,
    onCopyToFolder: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

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
            IconButton(onClick = onShare) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = stringResource(R.string.viewer_share),
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more),
                        contentDescription = stringResource(R.string.viewer_more),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Absent for files in the vault, which are not in the
                    // library and so are in no folder to be moved between.
                    onMoveToFolder?.let { move ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.folder_move_to)) },
                            onClick = { menuOpen = false; move() },
                        )
                    }
                    onCopyToFolder?.let { copy ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.folder_copy_to)) },
                            onClick = { menuOpen = false; copy() },
                        )
                    }
                    // Absent inside the vault: there is nowhere further to hide,
                    // and the original that would have to be deleted is this
                    // file.
                    onHide?.let { hide ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vault_hide)) },
                            onClick = { menuOpen = false; hide() },
                        )
                    }
                    onExportOut?.let { export ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vault_export)) },
                            onClick = { menuOpen = false; export() },
                        )
                    }
                }
            }
        }
    }
}
