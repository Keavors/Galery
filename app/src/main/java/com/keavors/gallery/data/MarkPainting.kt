package com.keavors.gallery.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
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
 * Draws [marks] over the picture occupying [area].
 *
 * [area] is where the *whole* photograph sits, cropped parts and all, so a mark
 * put near an edge that was later cropped away falls outside and is clipped
 * rather than sliding inwards.
 */
fun DrawScope.drawMarks(marks: List<Mark>, area: Rect, clipTo: Rect = area) {
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
        lines.forEachIndexed { index, line ->
            canvas.nativeCanvas.drawText(line, centre.x, first + index * step, paint)
        }
    }
}

/** How far apart lines of a caption sit, as a multiple of their height. */
private const val LINE_SPACING = 1.2f

private fun MarkFont.typeface(): android.graphics.Typeface = when (this) {
    MarkFont.PLAIN -> android.graphics.Typeface.DEFAULT_BOLD
    MarkFont.SERIF -> android.graphics.Typeface.SERIF
    MarkFont.MONOSPACE -> android.graphics.Typeface.MONOSPACE
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
        drawMarks(marks, uncroppedArea(crop, size.width, size.height), shown)
    }

    if (target !== this) recycle()
    return target
}
