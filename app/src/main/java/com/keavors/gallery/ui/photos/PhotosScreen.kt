package com.keavors.gallery.ui.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.LibraryState
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaWriter

/** The Photos tab: the timeline, or an honest reason why there is none yet. */
@Composable
fun PhotosScreen(
    state: LibraryState,
    writer: MediaWriter,
    albumActions: AlbumActions,
    onOpen: (item: MediaItem, thumbBucketPx: Int) -> Unit,
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

        is LibraryState.Ready -> if (state.items.isEmpty()) {
            Centered(modifier) {
                Text(
                    text = stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            TimelineScreen(
                items = state.items,
                writer = writer,
                albumActions = albumActions,
                onOpen = onOpen,
                modifier = modifier,
            )
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
