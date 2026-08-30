package com.keavors.gallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.Adjustments
import com.keavors.gallery.data.colorMatrixFor
import com.keavors.gallery.data.times
import kotlin.math.min

/**
 * The filters, which are nothing but the sliders already there set to something
 * worth looking at.
 *
 * That is the whole design. A filter sets the corrections and then gets out of
 * the way: what it did is visible on the sliders, every one of them can be
 * moved afterwards, and there is no second pipeline to keep in step with the
 * first. It also means a filter costs exactly what the corrections cost, which
 * on all but one of these is nothing.
 *
 * None of them touch shadows, highlights or sharpness on purpose. Those three
 * are the ones that need every pixel walked over, and a row of thumbnails that
 * each needed that would take a second to appear — or would quietly not show
 * what they promise. Everything here is a colour matrix and a vignette, so a
 * thumbnail is the truth.
 */
internal enum class FilterPreset(val label: Int, val adjustments: Adjustments) {
    NONE(R.string.filter_none, Adjustments.None),

    VIVID(R.string.filter_vivid, Adjustments(saturation = 0.4f, contrast = 0.2f)),

    SOFT(R.string.filter_soft, Adjustments(contrast = -0.25f, brightness = 0.08f)),

    WARM(R.string.filter_warm, Adjustments(temperature = 0.45f, brightness = 0.05f)),

    COOL(R.string.filter_cool, Adjustments(temperature = -0.4f, tint = -0.12f)),

    FILM(
        R.string.filter_film,
        Adjustments(contrast = 0.22f, saturation = -0.25f, temperature = 0.12f, vignette = 0.35f),
    ),

    MONO(R.string.filter_mono, Adjustments(saturation = -1f, contrast = 0.25f)),

    NOIR(
        R.string.filter_noir,
        Adjustments(saturation = -1f, contrast = 0.5f, brightness = -0.06f, vignette = 0.55f),
    ),

    FADED(
        R.string.filter_faded,
        Adjustments(contrast = -0.3f, saturation = -0.15f, brightness = 0.12f),
    ),
}

/** How big the taste of each filter is. */
private val THUMBNAIL = 62.dp

/**
 * One filter, shown on the photograph itself rather than on somebody's flowers.
 *
 * A square out of the middle of the picture: enough to tell what the filter
 * does, small enough that nine of them fit on a phone.
 */
@Composable
internal fun FilterThumbnail(
    image: ImageBitmap,
    preset: FilterPreset,
    strength: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val values = preset.adjustments * strength
    val filter = if (values.matrixIsNeutral) null else ColorFilter.colorMatrix(colorMatrixFor(values))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(THUMBNAIL)
                .clip(RoundedCornerShape(10.dp))
        ) {
            val side = min(image.width, image.height)
            drawImage(
                image = image,
                srcOffset = IntOffset((image.width - side) / 2, (image.height - side) / 2),
                srcSize = IntSize(side, side),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                colorFilter = filter,
            )
            if (values.vignette != 0f) {
                drawVignette(Rect(Offset.Zero, size), values.vignette)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(preset.label),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
