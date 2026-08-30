package com.keavors.gallery.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.keavors.gallery.R
import com.keavors.gallery.ui.photos.formatDuration
import kotlinx.coroutines.delay

/** How often the position readout catches up while something is playing. */
private const val TICK_MS = 200L

/**
 * Play, scrub, mute. Nothing else, and nothing on a timer of its own — these
 * appear and disappear with the rest of the chrome, so a tap on the video takes
 * the controls away along with the clock and the navigation buttons.
 */
@Composable
fun VideoControls(player: ExoPlayer, modifier: Modifier = Modifier) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var muted by remember { mutableStateOf(player.volume == 0f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // While a finger is on the slider the readout follows the finger, not the
    // player: otherwise the thumb fights the person dragging it.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        IconButton(
            onClick = {
                if (playing) {
                    player.pause()
                } else {
                    // Starting again from the end should replay rather than sit
                    // on the last frame doing nothing.
                    if (durationMs > 0 && player.currentPosition >= durationMs - 100) {
                        player.seekTo(0)
                    }
                    player.play()
                }
            },
        ) {
            Icon(
                painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(
                    if (playing) R.string.video_pause else R.string.video_play
                ),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

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

        IconButton(onClick = { player.volume = if (muted) 1f else 0f }) {
            Icon(
                painter = painterResource(
                    if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on
                ),
                contentDescription = stringResource(
                    if (muted) R.string.video_sound_on else R.string.video_sound_off
                ),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
