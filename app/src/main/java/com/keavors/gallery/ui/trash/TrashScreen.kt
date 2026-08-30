package com.keavors.gallery.ui.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaWriter
import com.keavors.gallery.data.daysUntilExpiry
import com.keavors.gallery.ui.common.ConfirmDialog
import com.keavors.gallery.ui.photos.Thumbnail

/**
 * The system trash.
 *
 * Files here still sit where they always did — nothing was moved or copied —
 * and Android sweeps them away thirty days after they arrive. That deadline is
 * not ours to change, so the most useful thing to do with it is show it.
 *
 * Tapping selects rather than opens: this is a screen for deciding what comes
 * back and what goes for good, not for looking at pictures.
 */
@Composable
fun TrashScreen(
    items: List<MediaItem>,
    writer: MediaWriter,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmForever by remember { mutableStateOf<List<MediaItem>?>(null) }

    // A selection left behind after the files are gone would show a count of
    // items nobody can point at.
    val present = items.map { it.id }.toSet()
    if (selected.any { it !in present }) selected = selected.intersect(present)

    val chosen = items.filter { it.id in selected }
    if (selected.isNotEmpty()) {
        BackHandler { selected = emptySet() }
    }

    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.trash_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TrashBar(
            total = items.size,
            selectedCount = chosen.size,
            onRestore = {
                writer.setTrashed(chosen.ifEmpty { items }, trashed = false)
                selected = emptySet()
            },
            onDeleteForever = { confirmForever = chosen.ifEmpty { items } },
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(TRASH_COLUMNS),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.id }) { item ->
                TrashTile(
                    item = item,
                    selected = item.id in selected,
                    onToggle = {
                        selected = if (item.id in selected) selected - item.id else selected + item.id
                    },
                )
            }
        }
    }

    confirmForever?.let { doomed ->
        ConfirmDialog(
            title = stringResource(R.string.delete_forever_title),
            body = stringResource(R.string.delete_forever_body),
            confirm = stringResource(R.string.action_delete_forever),
            onConfirm = {
                confirmForever = null
                selected = emptySet()
                writer.deleteForever(doomed)
            },
            onDismiss = { confirmForever = null },
        )
    }
}

private const val TRASH_COLUMNS = 3

@Composable
private fun TrashBar(
    total: Int,
    selectedCount: Int,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Text(
            text = if (selectedCount > 0) {
                stringResource(R.string.trash_selected, selectedCount)
            } else {
                total.toString()
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) {
            Text(
                stringResource(
                    if (selectedCount > 0) R.string.action_restore else R.string.trash_restore_all
                )
            )
        }
        IconButton(onClick = onDeleteForever) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_forever),
                contentDescription = stringResource(
                    if (selectedCount > 0) R.string.action_delete_forever else R.string.trash_empty_all
                ),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TrashTile(
    item: MediaItem,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val days = daysUntilExpiry(item.expiresAt, System.currentTimeMillis())

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onToggle),
    ) {
        Thumbnail(
            item = item,
            tileSize = 120.dp,
            corner = 3.dp,
            onClick = onToggle,
            modifier = Modifier.fillMaxSize(),
        )

        if (days != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = if (days <= 0) {
                        stringResource(R.string.trash_expires_today)
                    } else {
                        stringResource(R.string.trash_days_left, days)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
            )
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp),
            )
        }
    }
}
