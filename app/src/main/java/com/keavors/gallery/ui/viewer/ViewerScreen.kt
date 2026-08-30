package com.keavors.gallery.ui.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.request.ImageRequest
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.contentUri
import com.keavors.gallery.data.thumbnailCacheKey
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far the photo has to travel before letting go closes the viewer. */
private val DISMISS_DISTANCE = 130.dp

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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val view = LocalView.current

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    var chromeVisible by remember { mutableStateOf(true) }
    var detailsVisible by remember { mutableStateOf(false) }

    // How far the photo has been dragged away from the middle of the screen.
    // An Animatable rather than a plain float because the gesture callbacks are
    // created once and would otherwise keep reading the value they were born with.
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { DISMISS_DISTANCE.toPx() }
    val close by rememberUpdatedState(onClose)

    val current = items.getOrNull(pagerState.currentPage) ?: items.first()

    // One player for the whole viewer, moved from page to page. Each ExoPlayer
    // holds a hardware decoder, and keeping three alive so the pages either side
    // are "ready" would tie up a scarce resource for something nobody is watching.
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(current.id, current.isVideo) {
        if (current.isVideo) {
            player.setMediaItem(Media3Item.fromUri(current.contentUri()))
            player.repeatMode = Player.REPEAT_MODE_OFF
            // Both off by default, as agreed: opening a video should not start
            // making noise in a quiet room.
            player.volume = 0f
            player.playWhenReady = false
            player.prepare()
        } else {
            player.pause()
            player.clearMediaItems()
        }
    }

    // A video left running while the phone is locked or the app is switched away
    // keeps decoding for no one.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
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

    BackHandler { onClose() }

    // Zero while the photo sits still, one when it has been dragged far enough
    // to let go. Everything about the dismiss animation is driven from it.
    val dismissProgress = (abs(dragY.value) / dismissThresholdPx).coerceIn(0f, 1f)

    LaunchedEffect(pagerState.currentPage) { dragY.snapTo(0f) }

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
                                travelled > dismissThresholdPx -> close()
                                travelled < -dismissThresholdPx -> {
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
            val item = items[page]

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

            val zoomableState = rememberZoomableState()
            val imageState = rememberZoomableImageState(zoomableState)

            val request = remember(item.id, thumbBucketPx) {
                ImageRequest.Builder(context)
                    .data(item.contentUri())
                    // The grid tile is already in memory, so the full photo can
                    // fade in over it instead of over a black rectangle.
                    .placeholderMemoryCacheKey(thumbnailCacheKey(item.id, thumbBucketPx))
                    .build()
            }

            ZoomableAsyncImage(
                model = request,
                contentDescription = item.name,
                state = imageState,
                onClick = { chromeVisible = !chromeVisible },
                modifier = Modifier.fillMaxSize(),
            )

            // A page left zoomed in would come back zoomed when swiped past and
            // returned to, which is never what someone means by going back.
            LaunchedEffect(pagerState.settledPage) {
                if (pagerState.settledPage != page) {
                    zoomableState.resetZoom(withAnimation = false)
                }
            }
        }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = 1f - dismissProgress },
        ) {
            ViewerTopBar(
                item = current,
                onBack = onClose,
                onDetails = { detailsVisible = true },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = 1f - dismissProgress },
        ) {
            Column {
                if (current.isVideo) {
                    VideoControls(player = player)
                }
                ViewerBottomBar(item = current)
            }
        }
    }

    if (detailsVisible) {
        DetailsSheet(item = current, onDismiss = { detailsVisible = false })
    }
}
