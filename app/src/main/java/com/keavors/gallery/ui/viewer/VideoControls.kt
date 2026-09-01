@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.keavors.gallery.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.keavors.gallery.R
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.frameAt
import com.keavors.gallery.data.frameReader
import com.keavors.gallery.ui.common.ChromeIconButton
import com.keavors.gallery.ui.photos.formatDuration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How often the position readout catches up while something is playing. */
private const val TICK_MS = 200L

/** How still a dragging finger has to be before a frame is worth fetching. */
private const val PREVIEW_SETTLE_MS = 90L

/**
 * How big the frame over the bar is.
 *
 * Three times what it was, which on anything wider than it is tall means the
 * width runs out first — so it is bounded both ways and fitted inside, and a
 * portrait video gets the full height while a landscape one gets the full width.
 */
private val PREVIEW_HEIGHT = 264.dp
private const val PREVIEW_WIDTH_FRACTION = 0.92f

/** What the frame is fetched at. Enough pixels for the box above, and no more. */
private const val PREVIEW_HEIGHT_PX = 720

/** The speeds the button walks through, as percentages of ordinary. */
private val SPEEDS = intArrayOf(50, 75, 100, 150, 200)

/** Assumed when the file does not say, which is most of the time for a step. */
private const val FALLBACK_FPS = 30f

/**
 * Two rows: where the video is, and what can be done to it.
 *
 * One row would fit on a wide phone and cramp on any other — seven controls and
 * a scrub bar is too much for a single line. Nothing here runs on a timer of its
 * own: these appear and disappear with the rest of the chrome, so a tap on the
 * video takes the controls away along with the clock and the navigation buttons.
 */
@Composable
fun VideoControls(player: ExoPlayer, item: MediaItem, modifier: Modifier = Modifier) {
    var speed by remember { mutableIntStateOf((player.playbackParameters.speed * 100).toInt()) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // While a finger is on the slider the readout follows the finger, not the
    // player: otherwise the thumb fights the person dragging it.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // The frame under the finger.
    //
    // Read only while a finger is down, and only after it has stopped moving for
    // a moment: pulling a frame out of a video costs tens of milliseconds, and
    // doing it for every pixel of a drag would fetch fifty frames to show one.
    // The reader is opened once for the drag and closed after it, because
    // opening one is the expensive half.
    val context = LocalContext.current
    var preview by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(scrubbing, item.id) {
        if (!scrubbing) {
            preview = null
            return@LaunchedEffect
        }
        val reader = context.frameReader(item) ?: return@LaunchedEffect
        try {
            var shownAt = -1L
            while (true) {
                delay(PREVIEW_SETTLE_MS)
                val wanted = (scrubFraction * durationMs).toLong()
                if (durationMs > 0 && wanted != shownAt) {
                    shownAt = wanted
                    preview = reader.frameAt(wanted, PREVIEW_HEIGHT_PX)?.asImageBitmap()
                }
            }
        } finally {
            withContext(NonCancellable) { runCatching { reader.release() } }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = player.duration.takeIf { it > 0 } ?: 0L
            }

            override fun onVolumeChanged(volume: Float) {
                muted = volume == 0f
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(playing, scrubbing) {
        while (true) {
            if (!scrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0)
                durationMs = player.duration.takeIf { it > 0 } ?: durationMs
            }
            if (!playing) break
            delay(TICK_MS)
        }
    }

    val fraction = when {
        scrubbing -> scrubFraction
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownMs = if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // Over the bar rather than beside it, and only while a finger is down.
        preview?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
                    .fillMaxWidth(PREVIEW_WIDTH_FRACTION)
                    .height(PREVIEW_HEIGHT)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = formatDuration(shownMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )

            Slider(
                value = fraction,
                onValueChange = {
                    scrubbing = true
                    scrubFraction = it
                },
                onValueChangeFinished = {
                    if (durationMs > 0) player.seekTo((scrubFraction * durationMs).toLong())
                    scrubbing = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.weight(1f),
            )

            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // A step is a pause and a seek of one frame. The file says how long
            // a frame is when it knows; thirty a second is the guess when it
            // does not, and being a frame or two out on a step is not a thing
            // anybody can see.
            ChromeIconButton(
                icon = R.drawable.ic_step_back,
                contentDescription = stringResource(R.string.video_step_back),
                iconSize = 20.dp,
                onClick = { player.stepFrame(forward = false) },
            )

            ChromeIconButton(
                icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = stringResource(
                    if (playing) R.string.video_pause else R.string.video_play
                ),
                iconSize = 26.dp,
                onClick = {
                    if (playing) {
                        player.pause()
                    } else {
                        // Starting again from the end should replay rather than
                        // sit on the last frame doing nothing.
                        if (durationMs > 0 && player.currentPosition >= durationMs - 100) {
                            player.seekTo(0)
                        }
                        player.play()
                    }
                },
            )

            ChromeIconButton(
                icon = R.drawable.ic_step_forward,
                contentDescription = stringResource(R.string.video_step_forward),
                iconSize = 20.dp,
                onClick = { player.stepFrame(forward = true) },
            )

            TextButton(
                onClick = {
                    val next = SPEEDS[(SPEEDS.indexOf(speed).coerceAtLeast(0) + 1) % SPEEDS.size]
                    speed = next
                    player.setPlaybackSpeed(next / 100f)
                },
            ) {
                Text(
                    text = stringResource(R.string.video_speed_value, speed / 100f),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }

            ChromeIconButton(
                icon = if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on,
                contentDescription = stringResource(
                    if (muted) R.string.video_sound_on else R.string.video_sound_off
                ),
                iconSize = 20.dp,
                onClick = { player.volume = if (muted) 1f else 0f },
            )
        }
    }
}

/**
 * Moves one frame and stops there.
 *
 * Paused first, deliberately: a step while playing is a step the next frame
 * undoes. The seek is exact rather than to the nearest keyframe, which is the
 * whole point of stepping — the nearest keyframe can be seconds away.
 */
private fun ExoPlayer.stepFrame(forward: Boolean) {
    pause()
    val fps = videoFormat?.frameRate?.takeIf { it > 1f } ?: FALLBACK_FPS
    val frame = (1000f / fps).toLong().coerceAtLeast(1L)
    val target = (currentPosition + if (forward) frame else -frame)
        .coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
    seekTo(target)
}
