@file:OptIn(ExperimentalLayoutApi::class)

package com.keavors.gallery.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.copyMediaToClipboard
import com.keavors.gallery.data.shareMedia
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Padding is taken from the insets the bars *would* have if the system bars were
 * showing, not the ones they currently have.
 *
 * With the real insets, hiding the status bar would collapse the top padding to
 * zero and the chrome would jump upwards while fading out — the exact twitch the
 * viewer is built to avoid.
 */
private val stableInsets: WindowInsets
    @Composable get() = WindowInsets.systemBarsIgnoringVisibility

@Composable
fun ViewerTopBar(
    item: MediaItem,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    onSetCover: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
    val canCopy = !item.isPrivate

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // A scrim rather than a solid bar: the photo stays visible under the
            // controls, and white text stays readable over a bright sky.
            .background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))
            )
            .windowInsetsPadding(stableInsets)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.viewer_back),
                tint = Color.White,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = formatShotDate(item.takenAt, locale),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(R.string.viewer_more),
                    tint = Color.White,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_details)) },
                    onClick = {
                        menuOpen = false
                        onDetails()
                    },
                )
                onSetCover?.let { setCover ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.album_set_cover)) },
                        onClick = {
                            menuOpen = false
                            setCover()
                        },
                    )
                }
                if (canCopy) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.viewer_copy)) },
                        onClick = {
                            menuOpen = false
                            context.copyMediaToClipboard(item, item.name)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ViewerBottomBar(
    item: MediaItem,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.viewer_share)

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))
            )
            .windowInsetsPadding(stableInsets)
            .padding(vertical = 6.dp),
    ) {
        // A vaulted file has no MediaStore row behind it, so favouriting,
        // sharing and trashing have nothing to act on. Putting it back is the
        // one thing that makes sense, and it is the way out to everything else.
        if (item.isPrivate) {
            BarAction(
                icon = R.drawable.ic_restore,
                label = stringResource(R.string.vault_restore),
                onClick = onRestore,
            )
            return@Row
        }

        BarAction(
            // Filled once it is a favourite, outlined until then — the state is
            // the icon, so nothing has to be read to know it.
            icon = if (item.isFavorite) R.drawable.ic_heart else R.drawable.ic_heart_outline,
            label = stringResource(
                if (item.isFavorite) R.string.action_unfavorite else R.string.action_favorite
            ),
            onClick = onToggleFavorite,
        )
        BarAction(
            icon = R.drawable.ic_share,
            label = stringResource(R.string.viewer_share),
            onClick = { context.shareMedia(item, shareTitle) },
        )
        BarAction(
            icon = R.drawable.ic_tab_trash,
            label = stringResource(R.string.action_delete),
            onClick = onDelete,
        )
    }
}

@Composable
private fun BarAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/** "30 августа 2026, 14:05" — the date first, because that is what is looked for. */
internal fun formatShotDate(epochMillis: Long, locale: Locale): String {
    if (epochMillis <= 0) return ""
    val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale).format(zoned)
}
