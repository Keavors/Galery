package com.keavors.gallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.keavors.gallery.data.CropRect
import kotlin.math.abs
import kotlin.math.min

/** How close a finger has to be to a corner to be taken as grabbing it. */
private val HANDLE_REACH = 36.dp

/**
 * The picture with a crop frame over it.
 *
 * Drawn rather than laid out: the frame has to sit exactly on the photograph
 * whatever shape it is, and the photograph is letterboxed inside whatever space
 * the screen has left. Two boxes agreeing on that by layout would drift apart
 * the moment either changed.
 *
 * The corners are grabbed; the middle drags the whole frame. Nothing snaps to
 * anything: a crop is a judgement, and a frame that jumps to a ratio nobody
 * asked for is fighting it.
 */
@Composable
fun CropCanvas(
    image: ImageBitmap,
    crop: CropRect,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(crop)
    val report by rememberUpdatedState(onCropChange)

    // Where the photograph ended up inside the canvas, in pixels. Written while
    // drawing and read while handling touches, which is the only way the two can
    // agree without one of them guessing.
    var frame by remember { mutableStateOf(Rect.Zero) }
    var grabbed by remember { mutableStateOf(Grab.NONE) }

    Box(
        modifier = modifier.pointerInput(image) {
            val reach = HANDLE_REACH.toPx()
            detectDragGestures(
                onDragStart = { start ->
                    grabbed = grabFor(start, current, frame, reach)
                },
                onDragEnd = { grabbed = Grab.NONE },
                onDragCancel = { grabbed = Grab.NONE },
            ) { change, drag ->
                change.consume()
                if (frame.width <= 0f || frame.height <= 0f) return@detectDragGestures
                val dx = drag.x / frame.width
                val dy = drag.y / frame.height
                report(current.moved(grabbed, dx, dy))
            }
        },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frame = drawLetterboxed(image)
            drawCrop(frame, current)
        }
    }
}

/** Which part of the frame a finger took hold of. */
private enum class Grab { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, WHOLE }

private fun grabFor(point: Offset, crop: CropRect, frame: Rect, reach: Float): Grab {
    if (frame.width <= 0f) return Grab.NONE
    val left = frame.left + frame.width * crop.left
    val right = frame.left + frame.width * crop.right
    val top = frame.top + frame.height * crop.top
    val bottom = frame.top + frame.height * crop.bottom

    fun near(x: Float, y: Float) = abs(point.x - x) < reach && abs(point.y - y) < reach

    return when {
        near(left, top) -> Grab.TOP_LEFT
        near(right, top) -> Grab.TOP_RIGHT
        near(left, bottom) -> Grab.BOTTOM_LEFT
        near(right, bottom) -> Grab.BOTTOM_RIGHT
        point.x in left..right && point.y in top..bottom -> Grab.WHOLE
        else -> Grab.NONE
    }
}

/**
 * The frame after a drag.
 *
 * A corner moves alone and stops before it crosses its opposite; the middle
 * moves the whole frame and stops at the edges of the picture rather than
 * shrinking against them.
 */
private fun CropRect.moved(grab: Grab, dx: Float, dy: Float): CropRect = when (grab) {
    Grab.NONE -> this

    Grab.TOP_LEFT -> copy(
        left = (left + dx).coerceIn(0f, right - CropRect.MIN_SIDE),
        top = (top + dy).coerceIn(0f, bottom - CropRect.MIN_SIDE),
    )

    Grab.TOP_RIGHT -> copy(
        right = (right + dx).coerceIn(left + CropRect.MIN_SIDE, 1f),
        top = (top + dy).coerceIn(0f, bottom - CropRect.MIN_SIDE),
    )

    Grab.BOTTOM_LEFT -> copy(
        left = (left + dx).coerceIn(0f, right - CropRect.MIN_SIDE),
        bottom = (bottom + dy).coerceIn(top + CropRect.MIN_SIDE, 1f),
    )

    Grab.BOTTOM_RIGHT -> copy(
        right = (right + dx).coerceIn(left + CropRect.MIN_SIDE, 1f),
        bottom = (bottom + dy).coerceIn(top + CropRect.MIN_SIDE, 1f),
    )

    Grab.WHOLE -> {
        val shiftX = dx.coerceIn(-left, 1f - right)
        val shiftY = dy.coerceIn(-top, 1f - bottom)
        CropRect(left + shiftX, top + shiftY, right + shiftX, bottom + shiftY)
    }
}

/** Draws the picture as large as it goes without changing its shape. */
private fun DrawScope.drawLetterboxed(image: ImageBitmap): Rect {
    val scale = min(size.width / image.width, size.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    val left = (size.width - width) / 2f
    val top = (size.height - height) / 2f

    drawImage(
        image = image,
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(width.toInt(), height.toInt()),
    )
    return Rect(left, top, left + width, top + height)
}

/** Dims what is being cut away and draws the frame and its corners. */
private fun DrawScope.drawCrop(frame: Rect, crop: CropRect) {
    if (frame.width <= 0f) return

    val left = frame.left + frame.width * crop.left
    val right = frame.left + frame.width * crop.right
    val top = frame.top + frame.height * crop.top
    val bottom = frame.top + frame.height * crop.bottom

    val shade = Color.Black.copy(alpha = 0.55f)
    // Four bands rather than one rectangle with a hole: a hole needs a layer,
    // and this runs on every frame of a drag.
    drawRect(shade, topLeft = frame.topLeft, size = Size(frame.width, top - frame.top))
    drawRect(shade, topLeft = Offset(frame.left, bottom), size = Size(frame.width, frame.bottom - bottom))
    drawRect(shade, topLeft = Offset(frame.left, top), size = Size(left - frame.left, bottom - top))
    drawRect(shade, topLeft = Offset(right, top), size = Size(frame.right - right, bottom - top))

    drawRect(
        color = Color.White,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = 2.dp.toPx()),
    )

    // Thirds, because that is what people compose against.
    val thirdX = (right - left) / 3f
    val thirdY = (bottom - top) / 3f
    val hairline = Color.White.copy(alpha = 0.35f)
    for (i in 1..2) {
        drawLine(hairline, Offset(left + thirdX * i, top), Offset(left + thirdX * i, bottom))
        drawLine(hairline, Offset(left, top + thirdY * i), Offset(right, top + thirdY * i))
    }

    val arm = 22.dp.toPx()
    val thick = Stroke(width = 4.dp.toPx())
    listOf(
        Offset(left, top) to listOf(Offset(left + arm, top), Offset(left, top + arm)),
        Offset(right, top) to listOf(Offset(right - arm, top), Offset(right, top + arm)),
        Offset(left, bottom) to listOf(Offset(left + arm, bottom), Offset(left, bottom - arm)),
        Offset(right, bottom) to listOf(Offset(right - arm, bottom), Offset(right, bottom - arm)),
    ).forEach { (corner, arms) ->
        arms.forEach { drawLine(Color.White, corner, it, strokeWidth = thick.width) }
    }
}
