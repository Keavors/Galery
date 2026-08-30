@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.keavors.gallery.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaThumb
import com.keavors.gallery.data.thumbnailCacheKey

/**
 * One video in the pager.
 *
 * Only the page being looked at gets the player; the ones on either side show
 * their thumbnail. One player moved between pages rather than one per page: an
 * ExoPlayer holds a decoder, and three of them idling to the left and right of
 * the screen is hardware nobody is using.
 *
 * The player view carries no controls of its own — they are drawn in Compose so
 * they can hide and reappear along with the rest of the chrome instead of
 * running to their own timer.
 */
@Composable
fun VideoPage(
    item: MediaItem,
    player: ExoPlayer,
    isCurrent: Boolean,
    thumbBucketPx: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        if (isCurrent) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view -> view.player = player },
                onReset = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val request = remember(item.id, thumbBucketPx) {
                ImageRequest.Builder(context)
                    .data(MediaThumb(item.id, isVideo = true))
                    .memoryCacheKey(thumbnailCacheKey(item.id, thumbBucketPx))
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
