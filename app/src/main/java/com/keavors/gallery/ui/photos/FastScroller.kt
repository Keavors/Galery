package com.keavors.gallery.ui.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.TimelineRow
import com.keavors.gallery.data.headerAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** How long the controls linger after the list stops moving. */
private const val LINGER_MS = 3000L

/** Below this the list fits in a couple of flicks and a scrollbar is clutter. */
private const val MIN_ROWS_TO_BOTHER = 12

private const val SHOW_AFTER_ROWS = 3

private val TRACK_WIDTH = 28.dp
private val THUMB_HEIGHT = 44.dp

/**
 * Whether the scroll controls should be on screen: while the list is moving, and
 * for a few seconds afterwards so they can still be grabbed.
 */
@Composable
private fun rememberScrollActivity(listState: LazyListState, extraActive: Boolean): Boolean {
    var visible by remember { mutableStateOf(false) }
    val scrolling = listState.isScrollInProgress

    LaunchedEffect(scrolling, extraActive) {
        if (scrolling || extraActive) {
            visible = true
        } else {
            delay(LINGER_MS)
            visible = false
        }
    }
    return visible
}

/**
 * The draggable scrollbar down the right edge.
 *
 * Five thousand photos is around a thousand rows, and crossing that by flicking
 * is a minute of swiping. Dragging the thumb covers the whole library in one
 * gesture, and the bubble names the month under the finger so it can be aimed
 * rather than guessed.
 *
 * Expects to be given a modifier that fills the whole timeline: the track is
 * pinned to the right edge inside it, while the bubble hangs off to the left of
 * the track, which is only possible if the space to the left belongs to it too.
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    rows: List<TimelineRow>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    if (rows.size < MIN_ROWS_TO_BOTHER) return

    var dragging by remember { mutableStateOf(false) }
    val visible = rememberScrollActivity(listState, extraActive = dragging)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    val thumbHeightPx = with(density) { THUMB_HEIGHT.toPx() }

    val fraction by remember(rows) {
        derivedStateOf {
            val last = (rows.size - 1).coerceAtLeast(1)
            (listState.firstVisibleItemIndex.toFloat() / last).coerceIn(0f, 1f)
        }
    }

    val header by remember(rows) {
        derivedStateOf { rows.headerAt(listState.firstVisibleItemIndex) }
    }

    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffset = (travelPx * fraction).roundToInt()

    fun scrollToPosition(y: Float) {
        val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val target = ((y - thumbHeightPx / 2f) / travel).coerceIn(0f, 1f)
        scope.launch { listState.scrollToItem((target * (rows.size - 1)).roundToInt()) }
    }

    Box(modifier = modifier) {

        // The bubble sits outside the track so nothing can clip it: it is wider
        // than the track by design, and lives in the timeline's own space.
        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(tween(120)) + scaleIn(tween(140), initialScale = 0.85f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffset + (thumbHeightPx / 2f - 18.dp.toPx()).roundToInt()) }
                .padding(end = TRACK_WIDTH + 6.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(9.dp),
                shadowElevation = 3.dp,
            ) {
                Text(
                    text = header?.let { scrollLabel(it.bucket, locale) }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + slideInHorizontally(tween(180)) { it },
            exit = fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(TRACK_WIDTH),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(TRACK_WIDTH)
                    // Measured here rather than inside the gesture handler: that
                    // block runs before the first layout pass, so it reads a
                    // height of zero and the thumb never leaves the top.
                    .onSizeChanged { trackHeightPx = it.height.toFloat() }
                    .pointerInput(rows.size) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                scrollToPosition(offset.y)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, _ -> scrollToPosition(change.position.y) }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbOffset) }
                        .padding(end = 4.dp)
                        .width(if (dragging) 8.dp else 5.dp)
                        .height(THUMB_HEIGHT)
                        .background(
                            color = if (dragging) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            shape = RoundedCornerShape(50),
                        ),
                )
            }
        }
    }
}

/**
 * The button back to the newest photo. Sits at the bottom centre, where a thumb
 * reaches it without crossing the screen.
 */
@Composable
fun ScrollToTop(listState: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val scrolledAway by remember {
        derivedStateOf { listState.firstVisibleItemIndex > SHOW_AFTER_ROWS }
    }
    val visible = rememberScrollActivity(listState, extraActive = false) && scrolledAway

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.8f),
        exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.8f),
        modifier = modifier,
    ) {
        Surface(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = RoundedCornerShape(50),
            shadowElevation = 4.dp,
        ) {
            Box(
                modifier = Modifier.size(width = 54.dp, height = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up),
                    contentDescription = stringResource(R.string.scroll_to_top),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
