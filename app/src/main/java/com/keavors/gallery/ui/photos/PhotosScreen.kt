package com.keavors.gallery.ui.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.LibraryState
import com.keavors.gallery.data.LibrarySummary
import com.keavors.gallery.data.formatBytes
import com.keavors.gallery.data.formatCount
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stands in for the timeline until stage two builds it.
 *
 * It is not a placeholder though: every number here comes from the real index,
 * updates by itself when a photo is taken or deleted, and is what proves the
 * whole read path works end to end.
 */
@Composable
fun PhotosScreen(
    state: LibraryState,
    canManageMedia: Boolean,
    onRequestManageMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LibraryState.Locked, LibraryState.Loading -> Centered(modifier) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.library_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp),
            )
        }

        is LibraryState.Ready -> if (state.summary.total == 0) {
            Centered(modifier) {
                Text(
                    text = stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Summary(
                summary = state.summary,
                canManageMedia = canManageMedia,
                onRequestManageMedia = onRequestManageMedia,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun Summary(
    summary: LibrarySummary,
    canManageMedia: Boolean,
    onRequestManageMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.library_total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatCount(summary.total, locale),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val range = dateRange(summary, locale)
        if (range != null) {
            Text(
                text = range,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stat(stringResource(R.string.library_photos), formatCount(summary.photos, locale), Modifier.weight(1f))
            Stat(stringResource(R.string.library_videos), formatCount(summary.videos, locale), Modifier.weight(1f))
            Stat(stringResource(R.string.library_albums), formatCount(summary.albums, locale), Modifier.weight(1f))
        }

        Stat(
            label = stringResource(R.string.library_size),
            value = formatBytes(summary.totalBytes, locale),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        ManageMediaCard(
            granted = canManageMedia,
            onRequest = onRequestManageMedia,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ManageMediaCard(granted: Boolean, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.manage_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    if (granted) R.string.manage_granted else R.string.manage_missing
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!granted) {
                TextButton(
                    onClick = onRequest,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(stringResource(R.string.manage_request))
                }
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/**
 * The locale as the composition sees it, so every formatted number and date is
 * rebuilt when the app language changes instead of keeping the old one until the
 * process restarts.
 */
@Composable
private fun currentLocale(): Locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())

@Composable
private fun dateRange(summary: LibrarySummary, locale: Locale): String? {
    val oldest = summary.oldest ?: return null
    val newest = summary.newest ?: return null
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    val zone = ZoneId.systemDefault()
    val from = formatter.format(Instant.ofEpochMilli(oldest).atZone(zone))
    val to = formatter.format(Instant.ofEpochMilli(newest).atZone(zone))
    return stringResource(R.string.library_range, from, to)
}
