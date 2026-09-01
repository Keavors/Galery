package com.keavors.gallery.ui.viewer

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Rect
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import android.view.WindowManager
import com.keavors.gallery.data.GallerySettings
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaWriter
import com.keavors.gallery.data.contentUri
import com.keavors.gallery.data.motionVideoOf
import com.keavors.gallery.data.previewCacheKey
import com.keavors.gallery.data.previewRequest
import com.keavors.gallery.ui.PipHolder
import com.keavors.gallery.ui.common.ConfirmDialog
import com.keavors.gallery.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.DynamicZoomSpec
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far the photo has to travel before letting go closes the viewer. */
private val DISMISS_DISTANCE = 130.dp

/** How long each photograph is left up during a slideshow. */
private const val SLIDE_MS = 4_000L

/** Where a double tap goes and comes back from. Twice, as it has always been. */
private const val DOUBLE_TAP_ZOOM = 2f

/** Below this the picture is sitting where it opened, however the float landed. */
private const val AT_REST = 0.01f

/**
 * What a double tap does.
 *
 * Twice what is on the screen, and back again. Telephoto's own cycler zooms to
 * a multiple of the picture's own pixels, which is a different thing entirely
 * and the reason this is written out: a photograph from a messenger or a
 * download arrives smaller than the screen and is therefore already shown
 * enlarged, so "twice the original size" can be less than it is being shown at
 * already — a double tap that barely moves, or does nothing whatsoever. What is
 * multiplied here is what is there.
 */
private val DOUBLE_TAP_TO_ZOOM = DoubleClickToZoomListener { state, centroid ->
    if ((state.zoomFraction ?: 0f) > AT_REST) {
        state.resetZoom()
    } else {
        state.zoomBy(DOUBLE_TAP_ZOOM, centroid)
    }
}

/** A double tap left to mean nothing, for whoever turned it off. */
private val DOUBLE_TAP_IGNORED = DoubleClickToZoomListener { _, _ -> }

/**
 * The zoom limit, worked out from the sizes rather than fixed.
 *
 * Telephoto counts zoom from the picture's own pixels, and the picture's own
 * pixels are whatever the decoder felt like handing over — a screen-sized copy
 * of a forty-megapixel photograph on one phone, the whole thing on the next. A
 * fixed number would therefore mean something different for every photo. This
 * asks how large the picture is being shown and multiplies from there, so that
 * "eight times" always means eight times what is on the screen.
 */
private fun viewerZoom(maxZoom: Int) = DynamicZoomSpec { inputs ->
    val limit = maxZoom.toFloat()
    val shownAt = inputs.scaledContentBounds.size.maxDimension /
        inputs.unscaledContentSize.maxDimension
    ZoomSpec(
        maxZoomFactor = if (shownAt.isFinite() && shownAt > 0f) limit * shownAt else limit,
    )
}

/**
 * Pages handed to a looping pager.
 *
 * Large enough that nobody swipes off either end of it, and the middle is where
 * it starts, so there is as much room behind as ahead.
 */
private const val LOOP_PAGES = 1_000_000
private const val LOOP_MIDDLE = LOOP_PAGES / 2

/**
 * Full-screen viewer.
 *
 * The one rule everything else here bends around: the picture must not move when
 * the chrome comes and goes. That is why the pager fills the window from the
 * first frame and the bars are drawn on top of it — showing and hiding them
 * changes their own opacity and nothing else, so there is no layout pass in
 * which the photo could shift.
 */
@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    thumbBucketPx: Int,
    settings: GallerySettings,
    writer: MediaWriter,
    onRestoreFromVault: (MediaItem) -> Unit,
    /** Where this video was left off last time, or null to start at the beginning. */
    resumeAt: (Long) -> Long?,
    /** Told where a video got to, so the next opening can pick it up there. */
    onWatched: (id: Long, positionMs: Long, durationMs: Long) -> Unit,
    /** Deletes without asking and offers the way back. See the setting. */
    onUndoableDelete: (List<MediaItem>) -> Unit,
    onEdit: (MediaItem) -> Unit,
    pip: PipHolder,
    inPip: Boolean,
    onSetCover: ((itemId: Long) -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val view = LocalView.current

    // Looping is done by handing the pager far more pages than there are
    // photos and folding the index back with a modulo. The alternative — moving
    // the pager back to the start when it reaches the end — is visible as a jump.
    val loop = settings.loopPaging && items.size > 1
    val safeStart = startIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = if (loop) LOOP_MIDDLE - LOOP_MIDDLE % items.size + safeStart else safeStart,
        pageCount = { if (loop) LOOP_PAGES else items.size },
    )
    fun itemAt(page: Int) = items[if (loop) page.mod(items.size) else page.coerceIn(0, items.lastIndex)]

    // What the viewer is holding the screen at. The system's own setting is
    // where it starts and where it is put back on the way out — the rest of the
    // app has no business being locked to what one photograph needed.
    var screenLock by remember { mutableStateOf(ScreenLock.SYSTEM) }
    LaunchedEffect(screenLock) {
        // Only when it differs. Setting it is a call into the window manager, and
        // the viewer opens on the system's own setting every single time.
        val activity = view.context as Activity
        if (activity.requestedOrientation != screenLock.request) {
            activity.requestedOrientation = screenLock.request
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            (view.context as Activity).requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var chromeVisible by remember { mutableStateOf(settings.chromeOnOpen) }
    var slideshow by remember { mutableStateOf(false) }
    // The video hiding inside the photograph on screen, while it is playing.
    var motion by remember { mutableStateOf<java.io.File?>(null) }
    var detailsVisible by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // How far the photo has been dragged away from the middle of the screen.
    // An Animatable rather than a plain float because the gesture callbacks are
    // created once and would otherwise keep reading the value they were born with.
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { DISMISS_DISTANCE.toPx() }
    val close by rememberUpdatedState(onClose)

    val current = itemAt(pagerState.currentPage)

    // One player for the whole viewer, moved from page to page. Each ExoPlayer
    // holds a hardware decoder, and keeping three alive so the pages either side
    // are "ready" would tie up a scarce resource for something nobody is watching.
    //
    // Built when the first video is looked at rather than when the viewer opens.
    // Building one is tens of milliseconds of work on the main thread — it asks
    // the system about codecs and starts a thread of its own — and those are the
    // milliseconds a photograph opened from another app spends waiting to be
    // drawn. Most viewings never show a video at all.
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    LaunchedEffect(current.id, current.isVideo) {
        if (!current.isVideo) {
            player?.pause()
            player?.clearMediaItems()
            return@LaunchedEffect
        }
        val playing = player ?: ExoPlayer.Builder(context).build().also { player = it }
        playing.setMediaItem(Media3Item.fromUri(current.contentUri()))
        playing.repeatMode =
            if (settings.videoRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        // Both off unless asked for: opening a video should not start making
        // noise in a quiet room.
        playing.volume = if (settings.videoSound) 1f else 0f
        playing.setPlaybackSpeed(settings.videoSpeed / 100f)
        playing.playWhenReady = settings.videoAutoplay
        playing.prepare()

        // Back to where it was left, if it was left anywhere worth returning to.
        // After prepare rather than before: a seek on a player that has not been
        // given a file yet is a seek into nothing.
        if (settings.videoResume) {
            resumeAt(current.id)?.let { playing.seekTo(it) }
        }
    }

    // Where the video being left behind got to. Written when the page changes,
    // when the viewer closes and when the app goes away — the three ways a video
    // stops being watched, and none of them is a moment the player announces.
    val watching by rememberUpdatedState(current)
    DisposableEffect(watching.id) {
        onDispose {
            val playing = player ?: return@onDispose
            if (watching.isVideo && playing.duration > 0) {
                onWatched(watching.id, playing.currentPosition, playing.duration)
            }
        }
    }

    // What the app would keep playing if somebody walked out of it now.
    DisposableEffect(current.id, current.isVideo) {
        pip.video = current.takeIf { it.isVideo }
        onDispose { pip.video = null }
    }

    // Told to the system in advance rather than at the moment of leaving.
    //
    // A swipe home is not a moment an app gets asked anything: with the gesture
    // navigation everybody uses, the only way into the corner is to have said
    // beforehand that this screen may go there, and to have said what shape it
    // is and where on the screen it currently sits so the shrink has something
    // to shrink from.
    LaunchedEffect(current.id, current.isVideo, settings.pictureInPicture, inPip) {
        val activity = view.context as Activity
        val wanted = settings.pictureInPicture && current.isVideo && !inPip
        val ratio = Rational(
            current.width.coerceAtLeast(1),
            current.height.coerceAtLeast(1),
        ).takeIf { current.width > 0 && current.height > 0 && it.toFloat() in 0.42f..2.39f }
            ?: Rational(16, 9)

        runCatching {
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(wanted)
                    .setAspectRatio(ratio)
                    .setSourceRectHint(
                        Rect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                    )
                    .build()
            )
        }
    }

    // A video left running while the phone is locked or the app is switched away
    // keeps decoding for no one.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player?.pause()
                player?.let { playing ->
                    if (current.isVideo && playing.duration > 0) {
                        onWatched(current.id, playing.currentPosition, playing.duration)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // System bars follow the chrome. Behaviour is set to the transient mode so a
    // swipe from the edge brings them back temporarily without the app deciding
    // that the chrome should return for good.
    LaunchedEffect(chromeVisible) {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Chrome that takes itself away after a while. Off by default, because a
    // photo being looked at is not an idle screen and the controls disappearing
    // mid-thought is its own kind of annoyance.
    // Nothing but the picture fits in the corner.
    LaunchedEffect(inPip) { if (inPip) chromeVisible = false }

    LaunchedEffect(chromeVisible, settings.autoHideSeconds) {
        if (chromeVisible && settings.autoHideSeconds > 0) {
            delay(settings.autoHideSeconds * 1000L)
            chromeVisible = false
        }
    }

    // Brightness and the screen timeout are window-wide, so both are put back
    // exactly as they were found on the way out.
    DisposableEffect(settings.maxBrightness, settings.keepScreenOn) {
        val window = (view.context as Activity).window
        val previousBrightness = window.attributes.screenBrightness
        if (settings.maxBrightness) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (settings.maxBrightness) {
                window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            }
            if (settings.keepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // The viewer is black whatever the app theme is, so its bar icons are light.
    // Both the icons and the bars themselves are put back on the way out.
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLightStatusBars = controller.isAppearanceLightStatusBars
        val wasLightNavBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = wasLightStatusBars
            controller.isAppearanceLightNavigationBars = wasLightNavBars
        }
    }

    // A slideshow is the pager turning its own pages, and nothing else: the same
    // photographs, the same zoom, the same everything. It stops itself at the
    // last picture unless paging loops, because a slideshow that silently
    // restarts has no end and nobody watching knows whether they have seen it.
    LaunchedEffect(slideshow, pagerState.currentPage) {
        if (!slideshow) return@LaunchedEffect
        delay(SLIDE_MS)
        val page = pagerState.currentPage
        when {
            loop -> pagerState.animateScrollToPage(page + 1)
            page < pagerState.pageCount - 1 -> pagerState.animateScrollToPage(page + 1)
            else -> slideshow = false
        }
    }

    BackHandler {
        // The first thing back undoes is the slideshow, not the viewer.
        if (slideshow) slideshow = false else onClose()
    }

    // Zero while the photo sits still, one when it has been dragged far enough
    // to let go. Everything about the dismiss animation is driven from it.
    val dismissProgress = (abs(dragY.value) / dismissThresholdPx).coerceIn(0f, 1f)

    LaunchedEffect(pagerState.currentPage) {
        dragY.snapTo(0f)
        motion = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The backdrop thins out as the photo is dragged away, so the grid
            // underneath shows through and the gesture reads as "put this back".
            .background(Color.Black.copy(alpha = 1f - dismissProgress * 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragY.value
                    val shrink = 1f - dismissProgress * 0.12f
                    scaleX = shrink
                    scaleY = shrink
                }
                // Runs on the main pass, so a zoomed-in photo that is being
                // panned has already claimed the drag and this never starts.
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val travelled = dragY.value
                            when {
                                travelled > dismissThresholdPx && settings.swipeDownCloses -> close()
                                travelled < -dismissThresholdPx && settings.swipeUpDetails -> {
                                    detailsVisible = true
                                    scope.launch { dragY.animateTo(0f) }
                                }
                                else -> scope.launch { dragY.animateTo(0f) }
                            }
                        },
                        onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                    ) { change, delta ->
                        // Upward has nowhere to go but the details sheet, so it
                        // is damped: the photo hints at the gesture, it does not
                        // fly off the top of the screen.
                        val next = dragY.value + if (dragY.value < 0) delta * 0.4f else delta
                        scope.launch { dragY.snapTo(next) }
                        change.consume()
                    }
                },
        ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = itemAt(page)

            if (item.isVideo) {
                VideoPage(
                    item = item,
                    player = player,
                    isCurrent = page == pagerState.currentPage,
                    thumbBucketPx = thumbBucketPx,
                    onClick = { chromeVisible = !chromeVisible },
                )
                return@HorizontalPager
            }

            val zoomSpec = remember(settings.maxZoom) { viewerZoom(settings.maxZoom) }
            val zoomableState = rememberZoomableState(zoomSpec = zoomSpec)
            val imageState = rememberZoomableImageState(zoomableState)

            // The uri as well as the id: a photograph that arrived from another
            // app and has not been found in the library yet has no id to tell it
            // apart from the last one that arrived the same way.
            val request = remember(item.id, item.uri, thumbBucketPx) {
                ImageRequest.Builder(context)
                    .data(item.contentUri())
                    // Whatever is already in memory goes up while the file is
                    // read: the tile the grid drew, or the preview started the
                    // moment another app's intent arrived.
                    .placeholderMemoryCacheKey(previewCacheKey(item, thumbBucketPx))
                    .build()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Under the photograph, the same preview as a picture in its own
                // right.
                //
                // The placeholder above is read out of memory once, at the
                // instant the request starts, so it comes up empty whenever the
                // preview is a fraction of a second behind — which is exactly
                // the case this whole arrangement exists for: a photograph from
                // another app, out of a folder no grid has ever drawn. This one
                // waits for the preview instead of asking once for it, and goes
                // when the photograph itself is up.
                if (!imageState.isImageDisplayed) {
                    val preview = remember(item.id, item.uri, thumbBucketPx) {
                        previewRequest(context, item, thumbBucketPx)
                    }
                    AsyncImage(
                        model = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                ZoomableAsyncImage(
                    model = request,
                    contentDescription = item.name,
                    state = imageState,
                    onClick = {
                        slideshow = false
                        motion = null
                        chromeVisible = !chromeVisible
                    },
                    // A motion photo is an ordinary photograph with a second of
                    // video hidden in the same file. Nothing announces it, so it
                    // is looked for on the press rather than on every photograph
                    // that goes past, and an ordinary photograph simply has
                    // nothing to find.
                    onLongClick = {
                        scope.launch {
                            val found = context.motionVideoOf(item)
                            if (found != null) {
                                chromeVisible = false
                                motion = found
                            }
                        }
                    },
                    // Told where to stop rather than left to the limit above: a
                    // double tap is meant to land on a face, not to throw the
                    // picture eight times across the screen.
                    onDoubleClick = if (settings.doubleTapZoom) {
                        DOUBLE_TAP_TO_ZOOM
                    } else {
                        DOUBLE_TAP_IGNORED
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Over the photograph, exactly where it is, and gone the moment
                // it ends or anything is touched.
                if (motion != null && page == pagerState.currentPage) {
                    MotionClip(
                        file = motion!!,
                        muted = !settings.videoSound,
                        onFinished = { motion = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // A page left zoomed in would come back zoomed when swiped past and
            // returned to, which is never what someone means by going back.
            LaunchedEffect(pagerState.settledPage) {
                if (pagerState.settledPage != page) {
                    zoomableState.resetZoom(animationSpec = snap())
                }
            }
        }
        }

        AnimatedVisibility(
            visible = chromeVisible && !inPip,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = 1f - dismissProgress },
        ) {
            ViewerTopBar(
                item = current,
                settings = settings,
                slideshow = slideshow,
                onToggleSlideshow = {
                    slideshow = !slideshow
                    // The pictures are the point of a slideshow; the buttons
                    // over them are not.
                    if (slideshow) chromeVisible = false
                },
                onBack = onClose,
                onDetails = { detailsVisible = true },
                onSetCover = onSetCover?.let { set -> { set(current.id) } },
                screenLock = screenLock,
                onCycleScreenLock = { screenLock = screenLock.next() },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && !inPip,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = 1f - dismissProgress },
        ) {
            Column {
                player?.let { if (current.isVideo) VideoControls(player = it, item = current) }
                ViewerBottomBar(
                    item = current,
                    onToggleFavorite = { writer.setFavorite(listOf(current), !current.isFavorite) },
                    onEdit = { onEdit(current) },
                    onRestore = {
                        onRestoreFromVault(current)
                        if (items.size <= 1) onClose()
                    },
                    onDelete = {
                        when {
                            settings.undoDelete -> {
                                onUndoableDelete(listOf(current))
                                if (items.size <= 1) onClose()
                            }

                            writer.needsOwnConfirmation -> confirmDelete = true

                            else -> {
                                writer.setTrashed(listOf(current), trashed = true)
                                if (items.size <= 1) onClose()
                            }
                        }
                    },
                )
            }
        }
    }

    if (detailsVisible) {
        DetailsSheet(
            item = current,
            settings = settings,
            onDismiss = { detailsVisible = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            body = stringResource(R.string.delete_body),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                writer.setTrashed(listOf(current), trashed = true)
                // The pager drops to the next photo on its own once the library
                // reloads without this one; if it was the last, there is nothing
                // left to show and the viewer closes.
                if (items.size <= 1) onClose()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
