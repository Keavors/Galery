package com.keavors.gallery.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.MediaAccess

/**
 * The settings tab, holding only what already exists.
 *
 * Media access lives here rather than on the timeline: it is a property of the
 * app, not of the photos, and this is where someone goes looking when the
 * gallery says it cannot see something.
 */
@Composable
fun SettingsScreen(
    access: MediaAccess,
    canManageMedia: Boolean,
    onOpenSettings: () -> Unit,
    onRequestManageMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_access),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )

        Card(
            title = stringResource(
                when (access) {
                    MediaAccess.FULL -> R.string.access_state_full
                    MediaAccess.PARTIAL -> R.string.access_state_partial
                    MediaAccess.NONE -> R.string.access_state_none
                }
            ),
            action = if (access == MediaAccess.FULL) null else stringResource(R.string.access_open_settings),
            onAction = onOpenSettings,
        )

        Card(
            label = stringResource(R.string.manage_title),
            title = stringResource(
                if (canManageMedia) R.string.manage_granted else R.string.manage_missing
            ),
            action = if (canManageMedia) null else stringResource(R.string.manage_request),
            onAction = onRequestManageMedia,
            modifier = Modifier.padding(top = 10.dp),
        )

        Text(
            text = stringResource(R.string.stage_notice),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 24.dp),
        )
    }
}

@Composable
private fun Card(
    title: String,
    action: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (action != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(action)
                }
            }
        }
    }
}
