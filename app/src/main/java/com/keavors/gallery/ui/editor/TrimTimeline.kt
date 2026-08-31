package com.keavors.gallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.keavors.gallery.data.TrimRange
import kotlin.math.abs
import kotlin.math.roundToLong

/** How tall the strip is. Tall enough to recognise a shot in, short enough to spare. */
private val STRIP_HEIGHT = 66.dp

/** How wide the grab area of each handle is. */
private val HANDLE_WIDTH = 18.dp

/** How close a finger has to be to a handle to be taken as holding it. */
private val HANDLE_REACH = 40.dp

/** Which handle a finger took hold of. */
private enum class Handle { NONE, START, END }

/**
 * The frames of the video with the two handles over them.
 *
 * Drawn rather than laid out, for the same reason the crop frame is: the handles
 * have to sit exactly on the times they stand for, and a row of boxes agreeing
 * with a set of milliseconds by layout would drift apart the moment either
 * changed.
 *
 * A tap anywhere on the strip moves the playhead there. That is worth having on
 * its own — trimming is mostly looking for the moment something happens — and it
 * costs nothing, because the arithmetic for it is the same arithmetic the
 * handles use.
 */
@Composable
internal fun TrimTimeline(
    frames: List<ImageBitmap?>,
    durationMs: Long,
    range: TrimRange,
    positionMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val reach = remember(density) { with(density) { HANDLE_REACH.toPx() } }
    val handleWidth = remember(density) { with(density) { HANDLE_WIDTH.toPx() } }

    // Everything the gesture detectors read, read live: they are started once
    // and a captured range would be the range the screen opened with.
    val current by rememberUpdatedState(range)
    val length by rememberUpdatedState(durationMs)
    val moveStart by rememberUpdatedState(onStartChange)
    val moveEnd by rememberUpdatedState(onEndChange)
    val seek by rememberUpdatedState(onSeek)

    var held by remember { mutableStateOf(Handle.NONE) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { at ->
                        seek(timeAt(at.x, size.width.toFloat(), length))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { at ->
                            held = handleAt(at.x, size.width.toFloat(), current, length, reach)
                        },
                        onDragEnd = { held = Handle.NONE },
                        onDragCancel = { held = Handle.NONE },
                    ) { change, _ ->
                        change.consume()
                        val at = timeAt(change.position.x, size.width.toFloat(), length)
                        when (held) {
                            Handle.START -> moveStart(at)
                            Handle.END -> moveEnd(at)
                            Handle.NONE -> Unit
                        }
                    }
                },
        ) {
            drawFrames(frames)
            drawTrim(range, durationMs, handleWidth)
            drawPlayhead(positionMs, durationMs)
        }
    }
}

/** The time a point along the strip stands for. */
private fun timeAt(x: Float, width: Float, durationMs: Long): Long {
    if (width <= 0f) return 0L
    return (x / width * durationMs).roundToLong().coerceIn(0L, durationMs)
}

/** Where along the strip a time falls. */
private fun offsetOf(timeMs: Long, width: Float, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (timeMs.toFloat() / durationMs) * width
}

/**
 * Which handle a finger landed on.
 *
 * The nearer of the two wins, and only within reach of one — a finger in the
 * middle of a long clip is doing something else, and moving the nearest handle
 * halfway across the video because of it would be a disaster nobody could undo.
 */
private fun handleAt(
    x: Float,
    width: Float,
    range: TrimRange,
    durationMs: Long,
    reach: Float,
): Handle {
    if (width <= 0f) return Handle.NONE
    val toStart = abs(x - offsetOf(range.startMs, width, durationMs))
    val toEnd = abs(x - offsetOf(range.endMs, width, durationMs))
    return when {
        toStart > reach && toEnd > reach -> Handle.NONE
        toStart <= toEnd -> Handle.START
        else -> Handle.END
    }
}

/** The frames, side by side, filling the strip. */
private fun DrawScope.drawFrames(frames: List<ImageBitmap?>) {
    drawRect(Color.White.copy(alpha = 0.08f))
    if (frames.isEmpty()) return

    val slice = size.width / frames.size
    frames.forEachIndexed { index, frame ->
        frame ?: return@forEachIndexed
        drawImage(
            image = frame,
            dstOffset = IntOffset((index * slice).toInt(), 0),
            dstSize = IntSize(slice.toInt() + 1, size.height.toInt()),
        )
    }
}

/** What is being cut away, dimmed, and the two handles that decide it. */
private fun DrawScope.drawTrim(range: TrimRange, durationMs: Long, handleWidth: Float) {
    val left = offsetOf(range.startMs, size.width, durationMs)
    val right = offsetOf(range.endMs, size.width, durationMs)
    val shade = Color.Black.copy(alpha = 0.6f)

    drawRect(shade, topLeft = Offset.Zero, size = Size(left, size.height))
    drawRect(shade, topLeft = Offset(right, 0f), size = Size(size.width - right, size.height))

    // The kept piece, outlined top and bottom so the strip reads as one piece
    // rather than as two dark ends with something between them.
    val edge = 3.dp.toPx()
    drawRect(Color.White, topLeft = Offset(left, 0f), size = Size(right - left, edge))
    drawRect(
        Color.White,
        topLeft = Offset(left, size.height - edge),
        size = Size(right - left, edge),
    )

    drawRect(
        Color.White,
        topLeft = Offset(left - handleWidth / 2f, 0f),
        size = Size(handleWidth, size.height),
    )
    drawRect(
        Color.White,
        topLeft = Offset(right - handleWidth / 2f, 0f),
        size = Size(handleWidth, size.height),
    )

    // A grip line down the middle of each, so a handle looks like something to
    // take hold of rather than a white bar.
    val grip = 2.dp.toPx()
    val inset = size.height * 0.28f
    listOf(left, right).forEach { at ->
        drawRect(
            Color.Black.copy(alpha = 0.45f),
            topLeft = Offset(at - grip / 2f, inset),
            size = Size(grip, size.height - inset * 2f),
        )
    }
}

/** Where the video is at, if it is anywhere. */
private fun DrawScope.drawPlayhead(positionMs: Long, durationMs: Long) {
    if (positionMs <= 0L) return
    val at = offsetOf(positionMs, size.width, durationMs)
    drawRect(
        Color.White,
        topLeft = Offset(at - 1.dp.toPx(), 0f),
        size = Size(2.dp.toPx(), size.height),
    )
}
