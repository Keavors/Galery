@file:OptIn(ExperimentalLayoutApi::class)

package com.keavors.gallery.ui.viewer

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.copyMediaToClipboard
import com.keavors.gallery.data.shareMedia
import com.keavors.gallery.ui.common.BarAction
import com.keavors.gallery.ui.common.ChromeIconButton
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

/**
 * What the viewer has asked of the screen.
 *
 * Three states rather than a switch. Holding a landscape photograph steady on a
 * phone that is lying flat is a thing people want on purpose, and so is holding
 * an upright one steady, and so is having the screen behave normally again — a
 * switch can offer two of those three.
 *
 * Both locks follow the sensor within their own shape, so a phone turned end
 * for end still turns the picture the right way up.
 */
enum class ScreenLock(val icon: Int, val label: Int, val request: Int) {
    SYSTEM(
        R.drawable.ic_screen_rotation,
        R.string.viewer_orientation_system,
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
    ),
    LANDSCAPE(
        R.drawable.ic_screen_landscape,
        R.string.viewer_orientation_landscape,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
    ),
    PORTRAIT(
        R.drawable.ic_screen_portrait,
        R.string.viewer_orientation_portrait,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
    ),
    ;

    fun next(): ScreenLock = entries[(ordinal + 1) % entries.size]
}

@Composable
fun ViewerTopBar(
    item: MediaItem,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    onSetCover: (() -> Unit)?,
    screenLock: ScreenLock,
    onCycleScreenLock: () -> Unit,
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
        ChromeIconButton(
            icon = R.drawable.ic_back,
            contentDescription = stringResource(R.string.viewer_back),
            onClick = onBack,
        )

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

        // Before the overflow rather than inside it: turning a photograph is
        // something people do while looking at it, and a menu covers the thing
        // being looked at.
        ChromeIconButton(
            icon = screenLock.icon,
            contentDescription = stringResource(screenLock.label),
            onClick = onCycleScreenLock,
        )

        Box {
            ChromeIconButton(
                icon = R.drawable.ic_more,
                contentDescription = stringResource(R.string.viewer_more),
                onClick = { menuOpen = true },
            )
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
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.viewer_share)

    Row(
        // Every action gets the same share of the width — see BarAction. With
        // the row merely spacing them out, "Убрать из избранного" made its own
        // button twice the size of the one next to it and pushed the rest off
        // centre.
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))
            )
            .windowInsetsPadding(stableInsets)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        // A vaulted file has no MediaStore row behind it, so favouriting,
        // sharing and trashing have nothing to act on. Putting it back is the
        // one thing that makes sense, and it is the way out to everything else.
        if (item.isPrivate) {
            BarAction(
                icon = R.drawable.ic_restore,
                label = stringResource(R.string.vault_restore),
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            )
            return@Row
        }

        BarAction(
            // Filled once it is a favourite, outlined until then — the state is
            // the icon, so nothing has to be read to know it.
            icon = if (item.isFavorite) R.drawable.ic_heart else R.drawable.ic_heart_outline,
            // The label stays put while the icon changes: a word that swaps
            // itself under a thumb is how the wrong button gets pressed. What
            // the tap will actually do is said in full to anyone being read to.
            label = stringResource(R.string.bar_favorite),
            description = stringResource(
                if (item.isFavorite) R.string.action_unfavorite else R.string.action_favorite
            ),
            onClick = onToggleFavorite,
            modifier = Modifier.weight(1f),
        )
        // The same button, a different screen behind it: turning and cropping
        // pixels and cutting the ends off a recording have nothing in common but
        // the word, so the icon and the name say which one this is.
        BarAction(
            icon = if (item.isVideo) R.drawable.ic_cut else R.drawable.ic_edit,
            label = stringResource(if (item.isVideo) R.string.bar_trim else R.string.bar_edit),
            description = stringResource(
                if (item.isVideo) R.string.trim_title else R.string.editor_title
            ),
            onClick = onEdit,
            modifier = Modifier.weight(1f),
        )
        BarAction(
            icon = R.drawable.ic_share,
            label = stringResource(R.string.viewer_share),
            onClick = { context.shareMedia(item, shareTitle) },
            modifier = Modifier.weight(1f),
        )
        BarAction(
            icon = R.drawable.ic_tab_trash,
            label = stringResource(R.string.action_delete),
            onClick = onDelete,
            modifier = Modifier.weight(1f),
        )
    }
}

/** "30 августа 2026, 14:05" — the date first, because that is what is looked for. */
internal fun formatShotDate(epochMillis: Long, locale: Locale): String {
    if (epochMillis <= 0) return ""
    val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale).format(zoned)
}
