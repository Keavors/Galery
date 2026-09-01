@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.keavors.gallery.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * The second of video hidden inside a motion photo, played once over the still.
 *
 * A player of its own rather than the viewer's, and that is deliberate: the
 * viewer's player belongs to whatever video is on the page, and a motion photo
 * is a photograph — borrowing the player would mean putting it back afterwards
 * in exactly the state it was found. This one is born when the press happens and
 * dies when the clip ends, which is a second later.
 */
@Composable
fun MotionClip(
    file: File,
    muted: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(file.toURI().toString()))
            volume = if (muted) 0f else 1f
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player, onFinished) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onFinished()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

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
        modifier = modifier,
    )
}
