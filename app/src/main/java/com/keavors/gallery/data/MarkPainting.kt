package com.keavors.gallery.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * Drawing the marks.
 *
 * One implementation, used twice: the editor draws it onto the screen while a
 * finger is still moving, and saving draws it onto the full-size photograph.
 * Two implementations of this would be two different pictures — the one that
 * was approved and the one that was written — so both go through here, and the
 * only difference between them is how large the rectangle is.
 *
 * Nothing in here may use dp. Everything is measured against the picture, and
 * the picture is not measured in anybody's fingers.
 */

/**
 * The picture the marks are going over, and where it sits on the canvas.
 *
 * Needed only by the marks that hide part of it: blurring a face means reading
 * the face first, and the marks are drawn in canvas coordinates while the pixels
 * live in the picture's own. On screen those are two very different rectangles;
 * while saving they happen to be the same one, which is why this is one
 * parameter rather than two code paths.
 */
data class Underneath(val image: ImageBitmap, val occupies: Rect)

/**
 * Draws [marks] over the picture occupying [area].
 *
 * [area] is where the *whole* photograph sits, cropped parts and all, so a mark
 * put near an edge that was later cropped away falls outside and is clipped
 * rather than sliding inwards.
 */
fun DrawScope.drawMarks(
    marks: List<Mark>,
    area: Rect,
    clipTo: Rect = area,
    under: Underneath? = null,
) {
    if (marks.isEmpty() || area.width <= 0f || area.height <= 0f) return

    clipRect(clipTo.left, clipTo.top, clipTo.right, clipTo.bottom) {
        drawIntoCanvas { canvas ->
            // Every mark goes onto a sheet of its own, which is what lets the
            // eraser be an eraser. Rubbing out straight onto the photograph
            // would take the photograph with it — a hole rather than a
            // correction.
            canvas.saveLayer(clipTo, Paint())
            marks.forEach { mark ->
                when (mark) {
                    is Mark.Stroke -> drawStroke(mark, area)
                    is Mark.Text -> drawWriting(mark, area)
                    is Mark.Obscured -> under?.let { drawObscured(mark, area, it) }
                }
            }
            canvas.restore()
        }
    }
}

private fun DrawScope.drawStroke(stroke: Mark.Stroke, area: Rect) {
    val points = stroke.points
    if (points.isEmpty()) return

    val width = stroke.widthOn(area).coerceAtLeast(1f)
    val colour = if (stroke.erases) Color.Black else Color(stroke.colour)
    val blend = if (stroke.erases) BlendMode.Clear else BlendMode.SrcOver

    val first = points.first().on(area)
    if (points.size == 1) {
        // A tap is a dot, and a dot is the same brush held still. Without this
        // it would be nothing at all, which is not what a tap looks like.
        drawCircle(color = colour, radius = width / 2f, center = first, blendMode = blend)
        return
    }

    val path = Path().apply {
        moveTo(first.x, first.y)
        // Through the midpoints, with the recorded points as the corners to bend
        // around. A finger reports a coarse trail of positions and joining them
        // with straight lines looks like one.
        for (index in 1 until points.size) {
            val previous = points[index - 1].on(area)
            val current = points[index].on(area)
            val middle = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)
            quadraticTo(previous.x, previous.y, middle.x, middle.y)
        }
        val last = points.last().on(area)
        lineTo(last.x, last.y)
    }

    drawPath(
        path = path,
        color = colour,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = blend,
    )
}

/**
 * Words on the picture.
 *
 * Drawn through the platform's own text painter rather than through Compose's,
 * for one reason: this same function runs on a background thread while saving,
 * where there is no composition to ask for a font resolver. The platform painter
 * needs nothing but a typeface and a size, and it draws emoji as readily as
 * letters — which is what makes a sticker nothing more than a very large full
 * stop.
 */
private fun DrawScope.drawWriting(writing: Mark.Text, area: Rect) {
    if (writing.text.isBlank()) return

    val height = writing.sizeOn(area).coerceAtLeast(1f)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = writing.colour
        textSize = height
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = writing.font.typeface()
    }

    val lines = writing.text.split('\n')
    val step = height * LINE_SPACING
    val centre = Offset(
        x = area.left + writing.at.x * area.width,
        y = area.top + writing.at.y * area.height,
    )
    // Put down from the middle outwards, so a caption grows evenly around the
    // spot that was tapped rather than hanging off it.
    val first = centre.y - (lines.size - 1) * step / 2f + height / 3f

    drawIntoCanvas { canvas ->
        // Turned about the spot it was put at rather than about the corner of
        // the picture, which is the only place a turn makes sense to whoever
        // turned it.
        canvas.save()
        if (writing.angle != 0f) canvas.rotateAround(writing.angle, centre)
        lines.forEachIndexed { index, line ->
            canvas.nativeCanvas.drawText(line, centre.x, first + index * step, paint)
        }
        canvas.restore()
    }
}

/** Turns the canvas about a point rather than about its own corner. */
private fun androidx.compose.ui.graphics.Canvas.rotateAround(degrees: Float, at: Offset) {
    translate(at.x, at.y)
    rotate(degrees)
    translate(-at.x, -at.y)
}

/** How far apart lines of a caption sit, as a multiple of their height. */
private const val LINE_SPACING = 1.2f

private fun MarkFont.typeface(): android.graphics.Typeface = when (this) {
    MarkFont.PLAIN -> android.graphics.Typeface.DEFAULT_BOLD
    MarkFont.SERIF -> android.graphics.Typeface.SERIF
    MarkFont.MONOSPACE -> android.graphics.Typeface.MONOSPACE
}

/**
 * A part of the picture, made unreadable.
 *
 * The region is read, averaged down to a few dozen blocks and drawn back over
 * itself. Stretched smoothly that is a blur; stretched in squares it is a
 * mosaic. Doing it this way rather than with a real convolution means the work
 * is the size of the answer instead of the size of the question — hiding a
 * whole sky costs what hiding a face costs.
 */
private fun DrawScope.drawObscured(mark: Mark.Obscured, area: Rect, under: Underneath) {
    val dst = mark.bounds.on(area)
    if (dst.width < 1f || dst.height < 1f) return

    // The upright rectangle around the region, which for a turned one takes in
    // a little more than will be covered. It is being blurred: a little more of
    // it in the average changes nothing anybody can see.
    val cut = sourcePixels(dst, under.occupies, under.image.width, under.image.height)
    if (cut.width < 1 || cut.height < 1) return

    val pixels = IntArray(cut.width * cut.height)
    runCatching {
        under.image.readPixels(
            buffer = pixels,
            startX = cut.x,
            startY = cut.y,
            width = cut.width,
            height = cut.height,
        )
    }.onFailure { return }

    val across = OBSCURE_BLOCKS.coerceAtMost(cut.width)
    val down = (across * cut.height / cut.width.coerceAtLeast(1)).coerceIn(1, cut.height)
    val blocks = coarsen(pixels, cut.width, cut.height, across, down)

    val coarse = android.graphics.Bitmap
        .createBitmap(blocks, across, down, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()

    drawIntoCanvas { canvas ->
        canvas.save()
        if (mark.angle != 0f) canvas.rotateAround(mark.angle, dst.center)
        drawImage(
            image = coarse,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(across, down),
            dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
            dstSize = IntSize(
                dst.width.toInt().coerceAtLeast(1),
                dst.height.toInt().coerceAtLeast(1),
            ),
            // The one difference between the two: squares or no squares.
            filterQuality = if (mark.pixelated) FilterQuality.None else FilterQuality.High,
        )
        canvas.restore()
    }
}

/** Where a point of a mark falls on the picture. */
private fun MarkPoint.on(area: Rect): Offset =
    Offset(area.left + x * area.width, area.top + y * area.height)

/**
 * The same marks, drawn onto a bitmap.
 *
 * How the full-size save gets exactly what the screen showed: the bitmap is
 * wrapped rather than copied, so this draws into the photograph itself, through
 * the same code the editor draws through.
 */
fun Bitmap.withMarks(marks: List<Mark>, crop: CropRect): Bitmap {
    if (marks.isEmpty()) return this

    // A bitmap cut out of another one comes back immutable, and an immutable
    // bitmap cannot be drawn on.
    val target = if (isMutable) this else copy(Bitmap.Config.ARGB_8888, true)
    val size = Size(target.width.toFloat(), target.height.toFloat())
    val shown = Rect(0f, 0f, size.width, size.height)

    CanvasDrawScope().draw(
        // One pixel to the unit: everything here is measured against the
        // picture, never against a screen.
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(target.asImageBitmap()),
        size = size,
    ) {
        drawMarks(
            marks = marks,
            area = uncroppedArea(crop, size.width, size.height),
            clipTo = shown,
            // The picture being drawn on is also the picture being read from:
            // by this point it is the finished photograph, and a blur is meant
            // to hide what is finally there rather than what was there first.
            under = Underneath(target.asImageBitmap(), shown),
        )
    }

    if (target !== this) recycle()
    return target
}
