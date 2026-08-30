package com.keavors.gallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.keavors.gallery.data.CropRect
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
 * The picture handed in must be the one *before* cropping — turned and
 * straightened, but whole. The crop is this frame, and a picture that already
 * had it applied would leave the frame measuring a photograph that is no longer
 * underneath it.
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
    var canvas by remember { mutableStateOf(Size.Zero) }

    // Where the photograph sits inside the canvas, worked out from the two
    // sizes rather than measured while drawing. Drawing happens after touches
    // are handled, so a frame written there was always one gesture out of date —
    // the first drag of a session moved the crop against a rectangle of zeroes.
    val frame = letterboxIn(canvas, image.width, image.height)

    // Read through updated state rather than captured: the gesture detector is
    // created once per picture, and a captured frame or crop would keep the
    // value it was born with for as long as the editor is open.
    val current by rememberUpdatedState(crop)
    val report by rememberUpdatedState(onCropChange)
    val box by rememberUpdatedState(frame)

    var grabbed by remember { mutableStateOf(Grab.NONE) }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvas = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(image) {
                val reach = HANDLE_REACH.toPx()
                detectDragGestures(
                    onDragStart = { start -> grabbed = grabFor(start, current, box, reach) },
                    onDragEnd = { grabbed = Grab.NONE },
                    onDragCancel = { grabbed = Grab.NONE },
                ) { change, drag ->
                    change.consume()
                    val over = box
                    if (over.width <= 0f || over.height <= 0f) return@detectDragGestures
                    report(current.moved(grabbed, drag.x / over.width, drag.y / over.height))
                }
            },
    ) {
        drawImage(
            image = image,
            dstOffset = IntOffset(frame.left.toInt(), frame.top.toInt()),
            dstSize = IntSize(frame.width.toInt(), frame.height.toInt()),
        )
        drawCrop(frame, current)
    }
}

/** Which part of the frame a finger took hold of. */
internal enum class Grab { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, WHOLE }

/**
 * Where a picture of this shape ends up inside a canvas of that shape: as large
 * as it goes without being stretched, and centred in what is left over.
 */
internal fun letterboxIn(canvas: Size, imageWidth: Int, imageHeight: Int): Rect {
    if (canvas.width <= 0f || canvas.height <= 0f || imageWidth <= 0 || imageHeight <= 0) {
        return Rect.Zero
    }
    val scale = min(canvas.width / imageWidth, canvas.height / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (canvas.width - width) / 2f
    val top = (canvas.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * What a finger landing here took hold of.
 *
 * The nearest corner wins rather than the first one within reach: on a crop
 * pulled down to a thumbnail every corner is within reach of every touch, and
 * asking them in a fixed order would always answer the top left.
 */
internal fun grabFor(point: Offset, crop: CropRect, frame: Rect, reach: Float): Grab {
    if (frame.width <= 0f || frame.height <= 0f) return Grab.NONE
    val on = crop.on(frame)

    var closest = Grab.NONE
    var distance = reach * reach
    listOf(
        Grab.TOP_LEFT to on.topLeft,
        Grab.TOP_RIGHT to on.topRight,
        Grab.BOTTOM_LEFT to on.bottomLeft,
        Grab.BOTTOM_RIGHT to on.bottomRight,
    ).forEach { (corner, at) ->
        val away = (point - at).getDistanceSquared()
        if (away <= distance) {
            distance = away
            closest = corner
        }
    }
    if (closest != Grab.NONE) return closest

    return if (on.contains(point)) Grab.WHOLE else Grab.NONE
}

/**
 * Where a crop lands on the canvas.
 *
 * A crop is fractions of the picture; this is the one place they are turned
 * into pixels, so the frame that is drawn and the frame that is grabbed cannot
 * be two different rectangles.
 */
internal fun CropRect.on(frame: Rect): Rect = Rect(
    left = frame.left + frame.width * this.left,
    top = frame.top + frame.height * this.top,
    right = frame.left + frame.width * this.right,
    bottom = frame.top + frame.height * this.bottom,
)

/**
 * The frame after a drag, in fractions of the picture.
 *
 * A corner moves alone and stops before it crosses its opposite; the middle
 * moves the whole frame and stops at the edges of the picture rather than
 * shrinking against them.
 */
internal fun CropRect.moved(grab: Grab, dx: Float, dy: Float): CropRect = when (grab) {
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

/** Dims what is being cut away and draws the frame and its corners. */
private fun DrawScope.drawCrop(frame: Rect, crop: CropRect) {
    if (frame.width <= 0f) return

    val on = crop.on(frame)
    val left = on.left
    val right = on.right
    val top = on.top
    val bottom = on.bottom

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

    // The arms never reach further than half the side they sit on, or a frame
    // pulled small would be drawn as a solid white block.
    val arm = min(22.dp.toPx(), min(right - left, bottom - top) / 2f)
    val thick = 4.dp.toPx()
    listOf(
        Offset(left, top) to listOf(Offset(left + arm, top), Offset(left, top + arm)),
        Offset(right, top) to listOf(Offset(right - arm, top), Offset(right, top + arm)),
        Offset(left, bottom) to listOf(Offset(left + arm, bottom), Offset(left, bottom - arm)),
        Offset(right, bottom) to listOf(Offset(right - arm, bottom), Offset(right, bottom - arm)),
    ).forEach { (corner, arms) ->
        arms.forEach { drawLine(Color.White, corner, it, strokeWidth = thick) }
    }
}
