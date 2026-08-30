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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.keavors.gallery.data.CropRect
import com.keavors.gallery.data.PixelRect
import com.keavors.gallery.data.Vignette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** How close a finger has to be to a corner to be taken as grabbing it. */
private val HANDLE_REACH = 36.dp

/**
 * The picture, as the editor is about to change it.
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
 * The light and colour corrections are a filter the picture is drawn through
 * rather than pixels rewritten, which is what makes those sliders free: moving
 * one changes a matrix handed to the graphics chip and nothing else.
 *
 * The crop frame is only here while the crop is being worked on. Its corners,
 * its sides and its middle are all grabbed: a corner moves two edges at once, a
 * side moves one, the middle moves all four. Nothing snaps to anything: a crop
 * is a judgement, and a frame that jumps to a ratio nobody asked for is
 * fighting it.
 */
@Composable
fun EditorCanvas(
    image: ImageBitmap,
    crop: CropRect,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    vignette: Float = 0f,
    cropVisible: Boolean = true,
) {
    var canvas by remember { mutableStateOf(Size.Zero) }

    // With the frame gone there is nothing to say which part is being kept, so
    // the picture is shown already cut down to it: away from the crop tool,
    // what is on screen is what will be saved.
    val cut = remember(crop, image, cropVisible) {
        if (cropVisible) {
            PixelRect(0, 0, image.width, image.height)
        } else {
            crop.pixelsIn(image.width, image.height)
        }
    }

    // Where the photograph sits inside the canvas, worked out from the two
    // sizes rather than measured while drawing. Drawing happens after touches
    // are handled, so a frame written there was always one gesture out of date —
    // the first drag of a session moved the crop against a rectangle of zeroes.
    val frame = letterboxIn(canvas, cut.width, cut.height)

    // Everything the gesture detector needs is read through updated state
    // rather than captured, because the detector below is started once and
    // never again: a captured crop, frame or callback would keep the value it
    // was born with for as long as the editor is open.
    val current by rememberUpdatedState(crop)
    val report by rememberUpdatedState(onCropChange)
    val box by rememberUpdatedState(frame)
    val reach by rememberUpdatedState(with(LocalDensity.current) { HANDLE_REACH.toPx() })

    var grabbed by remember { mutableStateOf(Grab.NONE) }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvas = Size(it.width.toFloat(), it.height.toFloat()) }
            // Keyed on whether there is a frame to drag and on nothing else,
            // and the nothing else is deliberate. A key that changes cancels the
            // coroutine underneath and starts it again, which mid-drag means the
            // drag simply stops; the key here was once the picture, and the
            // picture is wrapped afresh on every recomposition, so every crop
            // reported caused the very recomposition that killed the gesture
            // reporting it. A frame could be nudged a few pixels and then went
            // dead until the finger was lifted and put down again.
            .pointerInput(cropVisible) {
                // Away from the crop there is nothing here to take hold of, and
                // a stray drag quietly re-cropping the photograph is the sort of
                // thing found much later, if at all.
                if (!cropVisible) return@pointerInput
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
            srcOffset = IntOffset(cut.x, cut.y),
            srcSize = IntSize(cut.width, cut.height),
            dstOffset = IntOffset(frame.left.toInt(), frame.top.toInt()),
            dstSize = IntSize(frame.width.toInt(), frame.height.toInt()),
            colorFilter = colorFilter,
        )
        if (vignette != 0f) drawVignette(frame, vignette)
        if (cropVisible) drawCrop(frame, current)
    }
}

/**
 * Which part of the frame a finger took hold of, and which of its edges that
 * moves.
 *
 * A corner is not a thing of its own: it is two edges moving together, and
 * saying so here is what keeps eight of them from being eight rules.
 */
internal enum class Grab(
    val movesLeft: Boolean = false,
    val movesTop: Boolean = false,
    val movesRight: Boolean = false,
    val movesBottom: Boolean = false,
) {
    NONE,
    LEFT(movesLeft = true),
    TOP(movesTop = true),
    RIGHT(movesRight = true),
    BOTTOM(movesBottom = true),
    TOP_LEFT(movesLeft = true, movesTop = true),
    TOP_RIGHT(movesTop = true, movesRight = true),
    BOTTOM_LEFT(movesLeft = true, movesBottom = true),
    BOTTOM_RIGHT(movesRight = true, movesBottom = true),
    WHOLE,
}

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
 * Corners are asked first and sides after, because a corner is where two sides
 * meet and a finger there means the corner every time. Within each, the nearest
 * wins rather than the first within reach: on a crop pulled down to a thumbnail
 * everything is within reach of everything, and asking in a fixed order would
 * always answer the top left.
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

    // A side is only grabbed alongside it, never off the end of it: level with
    // the frame for an upright edge, beside it for a flat one. Without that the
    // reach around a short side would answer for touches far past where the
    // frame actually is.
    val alongside = point.y >= on.top && point.y <= on.bottom
    val beside = point.x >= on.left && point.x <= on.right
    var edgeAway = reach
    listOf(
        Grab.LEFT to if (alongside) abs(point.x - on.left) else Float.MAX_VALUE,
        Grab.RIGHT to if (alongside) abs(point.x - on.right) else Float.MAX_VALUE,
        Grab.TOP to if (beside) abs(point.y - on.top) else Float.MAX_VALUE,
        Grab.BOTTOM to if (beside) abs(point.y - on.bottom) else Float.MAX_VALUE,
    ).forEach { (edge, away) ->
        if (away <= edgeAway) {
            edgeAway = away
            closest = edge
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
 * An edge being moved stops at the picture on one side and before it crosses
 * its opposite on the other; an edge not being moved does not move. That one
 * rule covers the four sides and the four corners, a corner being two sides.
 * The middle is the exception worth writing out: it moves the whole frame and
 * stops at the edges of the picture rather than shrinking against them.
 */
internal fun CropRect.moved(grab: Grab, dx: Float, dy: Float): CropRect = when (grab) {
    Grab.NONE -> this

    Grab.WHOLE -> {
        val shiftX = dx.coerceIn(-left, 1f - right)
        val shiftY = dy.coerceIn(-top, 1f - bottom)
        CropRect(left + shiftX, top + shiftY, right + shiftX, bottom + shiftY)
    }

    else -> CropRect(
        left = if (grab.movesLeft) (left + dx).coerceIn(0f, right - CropRect.MIN_SIDE) else left,
        top = if (grab.movesTop) (top + dy).coerceIn(0f, bottom - CropRect.MIN_SIDE) else top,
        right = if (grab.movesRight) (right + dx).coerceIn(left + CropRect.MIN_SIDE, 1f) else right,
        bottom = if (grab.movesBottom) {
            (bottom + dy).coerceIn(top + CropRect.MIN_SIDE, 1f)
        } else {
            bottom
        },
    )
}

/**
 * Darkens the corners, or lightens them the other side of neutral.
 *
 * Over the picture rather than over the canvas, and over the part being kept
 * rather than the whole photograph: a vignette belongs to the picture that comes
 * out, so it is centred on what the crop has left.
 */
internal fun DrawScope.drawVignette(frame: Rect, strength: Float) {
    if (frame.width <= 0f || frame.height <= 0f) return
    val corner = if (Vignette.darkens(strength)) Color.Black else Color.White
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                Vignette.CLEAR_TO to corner.copy(alpha = 0f),
                1f to corner.copy(alpha = Vignette.opacity(strength)),
            ),
            center = frame.center,
            radius = hypot(frame.width, frame.height) / 2f,
        ),
        topLeft = frame.topLeft,
        size = frame.size,
    )
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

    // A short bar in the middle of each side. The sides can be dragged now, and
    // nothing about a plain white line says so — the corners have had their
    // brackets saying it since the first version.
    val middleX = (left + right) / 2f
    val middleY = (top + bottom) / 2f
    val bar = min(26.dp.toPx(), min(right - left, bottom - top) / 3f) / 2f
    listOf(
        Offset(middleX - bar, top) to Offset(middleX + bar, top),
        Offset(middleX - bar, bottom) to Offset(middleX + bar, bottom),
        Offset(left, middleY - bar) to Offset(left, middleY + bar),
        Offset(right, middleY - bar) to Offset(right, middleY + bar),
    ).forEach { (from, to) ->
        drawLine(Color.White, from, to, strokeWidth = thick)
    }
}
