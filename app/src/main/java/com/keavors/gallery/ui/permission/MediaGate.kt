package com.keavors.gallery.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.MediaAccess

/**
 * Stands in front of every screen that reads the library.
 *
 * The partial-access case gets its own wording on purpose: Android 14 lets the
 * user hand over a handful of photos, and a gallery that quietly showed four
 * random pictures would look broken rather than restricted.
 */
@Composable
fun MediaGate(
    access: MediaAccess,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (access) {
        MediaAccess.FULL -> content()
        MediaAccess.NONE -> Explainer(
            title = stringResource(R.string.access_title),
            body = stringResource(R.string.access_body),
            action = stringResource(R.string.access_grant),
            onAction = onRequest,
            modifier = modifier,
        )
        MediaAccess.PARTIAL -> Explainer(
            title = stringResource(R.string.access_partial_title),
            body = stringResource(R.string.access_partial_body),
            action = stringResource(R.string.access_open_settings),
            onAction = onOpenSettings,
            modifier = modifier,
        )
    }
}

@Composable
private fun Explainer(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tab_photos),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = 26.dp),
        ) {
            Text(action)
        }
    }
}
