@file:OptIn(ExperimentalLayoutApi::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.keavors.gallery.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.keavors.gallery.R
import com.keavors.gallery.data.GallerySettings
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.SaveChoice
import com.keavors.gallery.data.TrimRange
import com.keavors.gallery.data.TrimResult
import com.keavors.gallery.data.contentUri
import com.keavors.gallery.data.endAt
import com.keavors.gallery.data.frameTimesMs
import com.keavors.gallery.data.startAt
import com.keavors.gallery.data.trimVideo
import com.keavors.gallery.data.videoFrames
import com.keavors.gallery.data.writeRequestFor
import com.keavors.gallery.ui.common.ChromeIconButton
import com.keavors.gallery.ui.common.TOUCH_TARGET
import com.keavors.gallery.ui.common.opaqueToTouch
import com.keavors.gallery.ui.photos.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How many frames the strip is made of. Enough to navigate by, few enough to decode. */
private const val STRIP_FRAMES = 8

/** How often the playhead catches up with the player, in milliseconds. */
private const val TICK_MS = 60L

/**
 * Trimming a video down to the part worth keeping.
 *
 * A separate screen from the photo editor rather than a tab inside it, because
 * they have nothing in common but the word "edit": one is arithmetic on pixels
 * that can be undone at any point, the other is a re-encode that takes as long
 * as it takes and produces a new file at the end.
 *
 * The encoder reads the source for as long as it runs, so the one file it can
 * never write into directly is the one it is reading. Every encode therefore
 * lands in a scratch file first — which is exactly what makes the choice
 * between a copy and a replacement honest: by the time the original is
 * touched, the encoder is finished with it.
 */
@Composable
fun VideoTrimScreen(
    item: MediaItem,
    settings: GallerySettings,
    onSaved: () -> Unit,
    onFailed: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val duration = remember(item.id) { item.durationMs.coerceAtLeast(TrimRange.MIN_LENGTH_MS) }
    var range by remember(item.id) { mutableStateOf(TrimRange.whole(duration)) }
    var position by remember(item.id) { mutableLongStateOf(0L) }
    var playing by remember(item.id) { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var askingHow by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(item.id) {
        player.setMediaItem(Media3Item.fromUri(item.contentUri()))
        player.prepare()
        // Muted, like the viewer: an editor that starts making noise the moment
        // it opens is an editor nobody opens twice.
        player.volume = 0f
    }

    // The playhead, and the end of the kept piece. Playback that runs past the
    // right-hand handle comes back to the left one, so what is heard and seen
    // while trimming is what the file will contain.
    LaunchedEffect(playing, range) {
        while (playing) {
            position = player.currentPosition.coerceIn(0L, duration)
            if (position >= range.endMs) {
                player.seekTo(range.startMs)
                player.pause()
                playing = false
            }
            delay(TICK_MS)
        }
    }

    val frames = rememberFrames(item, duration)

    // Both ways out go through here: the encode is identical, only where the
    // finished clip lands differs.
    fun save(overwrite: Boolean) {
        scope.launch {
            // The preview lets go of the video first. It holds a hardware
            // decoder, the export is about to ask for one of the same family,
            // and on a lean device the second request is the one that loses.
            player.pause()
            playing = false
            working = true
            progress = 0
            val outcome = context.trimVideo(item, range, overwrite) { progress = it }
            working = false
            when (outcome) {
                is TrimResult.Saved -> onSaved()
                is TrimResult.Failed -> onFailed(outcome.reason)
            }
        }
    }

    val overwriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // A refusal is an answer, not a failure, and needs nothing said about it.
        if (result.resultCode == android.app.Activity.RESULT_OK) save(overwrite = true)
    }

    BackHandler { onClose() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .opaqueToTouch()
            .background(Color.Black)
            .windowInsetsPadding(
                WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChromeIconButton(
                icon = R.drawable.ic_back,
                contentDescription = stringResource(R.string.viewer_back),
                onClick = onClose,
            )
            Text(
                text = stringResource(R.string.trim_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    when (settings.saveChoice) {
                        SaveChoice.ASK -> askingHow = true
                        SaveChoice.COPY -> save(overwrite = false)
                        SaveChoice.OVERWRITE ->
                            overwriteLauncher.launch(writeRequestFor(context, item))
                    }
                },
                enabled = !working && !range.isWhole(duration),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = TOUCH_TARGET),
            ) {
                Text(stringResource(R.string.editor_save), color = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
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

            if (working) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .opaqueToTouch()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = stringResource(R.string.trim_working, progress),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(range.startMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    text = stringResource(R.string.trim_kept, formatDuration(range.lengthMs)),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDuration(range.endMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }

            TrimTimeline(
                frames = frames,
                durationMs = duration,
                range = range,
                positionMs = position,
                onStartChange = {
                    range = range.startAt(it)
                    player.seekTo(it)
                    position = it
                },
                onEndChange = {
                    range = range.endAt(it, duration)
                    player.seekTo(it)
                    position = it
                },
                onSeek = {
                    player.seekTo(it)
                    position = it
                },
                modifier = Modifier.padding(vertical = 10.dp),
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ChromeIconButton(
                    icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    contentDescription = stringResource(
                        if (playing) R.string.video_pause else R.string.video_play
                    ),
                    iconSize = 26.dp,
                    onClick = {
                        if (playing) {
                            player.pause()
                            playing = false
                        } else {
                            // Always from inside the kept piece: pressing play
                            // and watching the part that is being cut away is
                            // not what the button looks like it does.
                            if (position < range.startMs || position >= range.endMs) {
                                player.seekTo(range.startMs)
                                position = range.startMs
                            }
                            player.play()
                            playing = true
                        }
                    },
                )
            }
        }
    }

    if (askingHow) {
        SaveChoiceDialog(
            body = stringResource(R.string.trim_save_body),
            onCopy = {
                askingHow = false
                save(overwrite = false)
            },
            onOverwrite = {
                askingHow = false
                overwriteLauncher.launch(writeRequestFor(context, item))
            },
            onDismiss = { askingHow = false },
        )
    }
}

/**
 * The strip of frames under the handles.
 *
 * Decoded once per video and held for as long as the screen is open. They are
 * taken across the whole video rather than across the trim, because the strip is
 * what the handles are dragged along: it has to stay still while they move.
 */
@Composable
private fun rememberFrames(item: MediaItem, durationMs: Long): List<ImageBitmap?> {
    val context = LocalContext.current
    var frames by remember(item.id) { mutableStateOf<List<ImageBitmap?>>(emptyList()) }

    LaunchedEffect(item.id, durationMs) {
        val times = frameTimesMs(durationMs, STRIP_FRAMES)
        frames = context.videoFrames(item, times).map { it?.asImageBitmap() }
    }
    return frames
}
