@file:OptIn(ExperimentalLayoutApi::class)

package com.keavors.gallery.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.Adjustments
import com.keavors.gallery.data.CropRect
import com.keavors.gallery.data.EditHistory
import com.keavors.gallery.data.EditOps
import com.keavors.gallery.data.EditStep
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.SaveOutcome
import com.keavors.gallery.data.applyOps
import com.keavors.gallery.data.carriedExif
import com.keavors.gallery.data.times
import com.keavors.gallery.data.colorMatrixFor
import com.keavors.gallery.data.decodeForEditing
import com.keavors.gallery.data.maxEditablePixels
import com.keavors.gallery.data.overwriteWith
import com.keavors.gallery.data.saveEditedCopy
import com.keavors.gallery.data.tonedCopy
import com.keavors.gallery.data.writeRequestFor
import com.keavors.gallery.ui.common.BarAction
import com.keavors.gallery.ui.common.ChromeIconButton
import com.keavors.gallery.ui.common.TOUCH_TARGET
import com.keavors.gallery.ui.common.opaqueToTouch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How the edited picture is put back.
 *
 * Asked every time by default, because the two are not interchangeable: one
 * keeps the photograph as it was and one does not.
 */
enum class SaveMode { COPY, OVERWRITE }

/** Which set of tools is open. */
private enum class EditorTab { GEOMETRY, COLOUR, FILTERS }

/**
 * The editor.
 *
 * Everything on screen is drawn from a small copy of the photograph, so turning
 * and cropping are instant whatever the original weighs. The edits themselves
 * are a short list of intentions, not pixels — when the time comes to save, that
 * same list is applied once to a full-size decode.
 *
 * Geometry and colour are separate sets of tools rather than one long strip:
 * they are used at different moments, and a crop frame has no business being on
 * screen while a brightness slider is being moved.
 */
@Composable
fun EditorScreen(
    item: MediaItem,
    jpegQuality: Int,
    onSaved: (keptMetadata: Boolean) -> Unit,
    onFailed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Everywhere the picture has been, not just where it is. The edits are a
    // small value, so keeping all of them costs nothing worth counting.
    var history by remember(item.id) { mutableStateOf(EditHistory()) }
    val ops = history.present
    var comparing by remember(item.id) { mutableStateOf(false) }
    var preview by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var working by remember { mutableStateOf(false) }
    var askingHow by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(EditorTab.GEOMETRY) }
    var correction by remember { mutableStateOf(Correction.entries.first()) }
    var shape by remember(item.id) { mutableStateOf(CropShape.FREE) }
    // Which filter is on and how far. Kept beside the corrections rather than
    // inside them because a filter is only a way of setting them: once it has,
    // it is the corrections that are the truth, and these two are just what the
    // strength slider needs to be able to set them again.
    var filter by remember(item.id) { mutableStateOf(FilterPreset.NONE) }
    var strength by remember(item.id) { mutableFloatStateOf(1f) }

    // One way in for every change, so that nothing can alter the picture without
    // saying what kind of change it was — which is what keeps a slider dragged
    // across the screen from becoming two hundred steps to undo.
    fun change(next: EditOps, step: EditStep) {
        history = history.with(next, step)
    }

    // The preview is deliberately small: it is only ever shown at screen size,
    // and decoding a hundred megapixels to draw four hundred thousand of them
    // would make opening the editor a wait.
    LaunchedEffect(item.id) {
        preview = context.decodeForEditing(item, PREVIEW_PIXELS)
    }

    // Turns and straightening only. The crop is the frame drawn over this
    // picture and the colours are a filter it is drawn through, so applying
    // either here as well would be doing the work twice — and in the crop's
    // case doing it to the very part the frame is measured against.
    val geometry = ops.geometryOnly
    val shown = remember(preview, geometry) {
        preview?.let { if (geometry.isIdentity) it else applyOps(it, geometry) }
    }

    // Shadows, highlights and sharpness are the three that cannot be a matrix:
    // they are arithmetic on every pixel, so unlike the other six they cost real
    // work. It is done off the main thread, and the last finished picture stays
    // on screen until the next one is ready — which is why these three sliders
    // are followed a beat behind rather than stuttering.
    var toned by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(
        shown,
        ops.adjustments.shadows,
        ops.adjustments.highlights,
        ops.adjustments.sharpness,
    ) {
        val base = shown
        toned = when {
            base == null -> null
            ops.adjustments.toneIsNeutral -> base
            else -> withContext(Dispatchers.Default) { base.tonedCopy(ops.adjustments) }
        }
    }

    // Wrapped once per picture. asImageBitmap builds a new object every time it
    // is called, so wrapping it where it is used would hand the canvas a
    // different picture on every frame of a drag.
    val image = remember(toned) { toned?.asImageBitmap() }

    // The same photograph with none of the corrections on it, for the comparison
    // held under a finger. Same geometry, though: the crop and the turns are
    // decisions about what the picture is, and swapping those out as well would
    // be showing a different photograph rather than the same one untouched.
    val plain = remember(shown) { shown?.asImageBitmap() }

    // The picture's own width against its height. A crop is fractions of the
    // photograph, so this is what turns "square" into a rectangle of fractions.
    val pictureShape = image?.let { it.width.toFloat() / it.height.toFloat() }

    // A matrix, not a redrawn bitmap: this is what makes the colour sliders cost
    // nothing on a photograph of any size.
    val colours = remember(ops.adjustments) {
        if (ops.adjustments.matrixIsNeutral) {
            null
        } else {
            ColorFilter.colorMatrix(colorMatrixFor(ops.adjustments))
        }
    }

    BackHandler { onClose() }

    // Both ways of saving go through here, because the two differ by one
    // argument and what has to be said afterwards does not differ at all. A
    // write that fails — no room, a card pulled out, a file gone — has to say
    // so; the editor staying open with a button that did nothing is not an
    // answer.
    fun save(mode: SaveMode) {
        scope.launch {
            working = true
            val outcome = saveEdit(context, item, ops, mode, jpegQuality)
            working = false
            when (outcome) {
                SaveOutcome.SAVED -> onSaved(true)
                SaveOutcome.SAVED_WITHOUT_METADATA -> onSaved(false)
                SaveOutcome.FAILED -> onFailed()
            }
        }
    }

    val overwriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // A refusal is an answer, not a failure, and needs nothing said about it.
        if (result.resultCode == android.app.Activity.RESULT_OK) save(SaveMode.OVERWRITE)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Before the padding, so the strips behind the system bars are
            // covered too: the editor is over a photograph, and a touch that
            // misses everything here must not turn the page underneath.
            .opaqueToTouch()
            .background(Color.Black)
            // The insets the system bars would have, not the ones they have.
            // The editor opens over the viewer, which hides those bars on a
            // timer of its own; with the real insets the toolbar would slide up
            // under the clock the moment that timer went off. The cutout is
            // added because in landscape the back button sits exactly where the
            // camera is.
            .windowInsetsPadding(
                WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChromeIconButton(
                icon = R.drawable.ic_back,
                contentDescription = stringResource(R.string.viewer_back),
                onClick = onClose,
            )
            Text(
                text = stringResource(R.string.editor_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            ChromeIconButton(
                icon = R.drawable.ic_undo,
                contentDescription = stringResource(R.string.editor_undo),
                enabled = history.canUndo,
                onClick = {
                    history = history.undone()
                    // The panel stops claiming a filter it may no longer be
                    // showing. What the corrections are is the truth; the name
                    // over them was only ever a convenience.
                    filter = FilterPreset.NONE
                },
            )
            ChromeIconButton(
                icon = R.drawable.ic_redo,
                contentDescription = stringResource(R.string.editor_redo),
                enabled = history.canRedo,
                onClick = {
                    history = history.redone()
                    filter = FilterPreset.NONE
                },
            )
            TextButton(
                onClick = { askingHow = true },
                enabled = !ops.isIdentity && !working && preview != null,
                // The one button here that saves over a photograph, and it was
                // the smallest thing on the bar.
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = TOUCH_TARGET),
            ) {
                Text(stringResource(R.string.editor_save), color = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .compareWhileHeld { comparing = it },
            contentAlignment = Alignment.Center,
        ) {
            if (image == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                EditorCanvas(
                    image = if (comparing) plain ?: image else image,
                    crop = ops.crop,
                    onCropChange = { change(ops.cropped(it), EditStep(EditStep.Kind.CROP)) },
                    ratio = pictureShape?.let { shape.of(it) },
                    colorFilter = if (comparing) null else colours,
                    vignette = if (comparing) 0f else ops.adjustments.vignette,
                    cropVisible = tab == EditorTab.GEOMETRY && !comparing,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (working) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Nothing moves while the picture is being written: the
                        // edits being saved were settled when saving started,
                        // and a frame dragged now would be a lie about them.
                        .opaqueToTouch()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        when (tab) {
            EditorTab.GEOMETRY -> GeometryTools(
                ops = ops,
                shape = shape,
                onShape = { chosen ->
                    shape = chosen
                    // Applied there and then rather than waiting for the frame
                    // to be touched: a shape that does nothing until the next
                    // drag looks like a button that did nothing.
                    if (pictureShape != null) {
                        chosen.of(pictureShape)?.let { wanted ->
                            change(
                                ops.cropped(ops.crop.keeping(wanted, pictureShape, Grab.NONE)),
                                EditStep(EditStep.Kind.CROP, "shape"),
                            )
                        }
                    }
                },
                onChange = ::change,
            )

            EditorTab.COLOUR -> ColourTools(
                adjustments = ops.adjustments,
                chosen = correction,
                onChoose = { correction = it },
                onChange = {
                    // Moving a slider by hand is no longer whatever the filter
                    // said, so the filter stops claiming the credit.
                    filter = FilterPreset.NONE
                    // Named after the correction, so that moving on to a
                    // different one starts a step of its own.
                    change(ops.adjusted(it), EditStep(EditStep.Kind.ADJUST, correction.name))
                },
            )

            EditorTab.FILTERS -> FilterTools(
                image = image,
                chosen = filter,
                strength = strength,
                onChoose = { preset ->
                    filter = preset
                    strength = 1f
                    change(
                        ops.adjusted(preset.adjustments),
                        EditStep(EditStep.Kind.FILTER, preset.name),
                    )
                },
                onStrength = {
                    strength = it
                    change(
                        ops.adjusted(filter.adjustments * it),
                        EditStep(EditStep.Kind.FILTER, filter.name + " strength"),
                    )
                },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            BarAction(
                icon = R.drawable.ic_crop_rotate,
                label = stringResource(R.string.editor_tab_geometry),
                selected = tab == EditorTab.GEOMETRY,
                onClick = { tab = EditorTab.GEOMETRY },
                modifier = Modifier.weight(1f),
            )
            BarAction(
                icon = R.drawable.ic_tune,
                label = stringResource(R.string.editor_tab_colour),
                selected = tab == EditorTab.COLOUR,
                onClick = { tab = EditorTab.COLOUR },
                modifier = Modifier.weight(1f),
            )
            BarAction(
                icon = R.drawable.ic_filters,
                label = stringResource(R.string.editor_tab_filters),
                selected = tab == EditorTab.FILTERS,
                onClick = { tab = EditorTab.FILTERS },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (askingHow) {
        SaveChoiceDialog(
            onCopy = {
                askingHow = false
                save(SaveMode.COPY)
            },
            onOverwrite = {
                askingHow = false
                overwriteLauncher.launch(writeRequestFor(context, item))
            },
            onDismiss = { askingHow = false },
        )
    }
}

/** Turns, mirroring, the crop and the horizon. */
@Composable
private fun GeometryTools(
    ops: EditOps,
    shape: CropShape,
    onShape: (CropShape) -> Unit,
    onChange: (EditOps, EditStep) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        // The same cells as the viewer's bottom bar: a quarter of the width
        // each, the whole of it answering a thumb rather than the icon in the
        // middle of it.
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BarAction(
                icon = R.drawable.ic_rotate,
                label = stringResource(R.string.editor_rotate),
                onClick = { onChange(ops.turned(), EditStep(EditStep.Kind.TURN)) },
                modifier = Modifier.weight(1f),
            )
            BarAction(
                icon = R.drawable.ic_flip,
                label = stringResource(R.string.editor_flip),
                onClick = { onChange(ops.flipped(), EditStep(EditStep.Kind.FLIP)) },
                modifier = Modifier.weight(1f),
            )
            BarAction(
                icon = R.drawable.ic_crop_reset,
                label = stringResource(R.string.editor_reset_crop),
                onClick = {
                    onChange(ops.cropped(CropRect.Whole), EditStep(EditStep.Kind.CROP, "whole"))
                },
                modifier = Modifier.weight(1f),
            )
            BarAction(
                icon = R.drawable.ic_restore,
                label = stringResource(R.string.editor_reset),
                onClick = { onChange(EditOps.None, EditStep(EditStep.Kind.RESET)) },
                modifier = Modifier.weight(1f),
            )
        }

        // The shapes a crop can be held to. Scrolled rather than shared out
        // evenly: there are seven of them, and no reason for the commonest to be
        // as narrow as the rarest.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            CropShape.entries.forEach { entry ->
                CorrectionChip(
                    label = stringResource(entry.label),
                    selected = entry == shape,
                    touched = false,
                    onClick = { onShape(entry) },
                )
            }
        }

        Text(
            text = stringResource(R.string.editor_straighten, ops.straighten.toInt()),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
        WhiteSlider(
            value = ops.straighten,
            onValueChange = {
                onChange(ops.straightened(it), EditStep(EditStep.Kind.STRAIGHTEN))
            },
            range = -EditOps.MAX_STRAIGHTEN..EditOps.MAX_STRAIGHTEN,
        )
    }
}

/**
 * One slider and a row of names, rather than a slider each.
 *
 * Six sliders stacked up would leave no photograph to look at, and the
 * photograph is the one thing the person moving them needs to see.
 */
@Composable
private fun ColourTools(
    adjustments: Adjustments,
    chosen: Correction,
    onChoose: (Correction) -> Unit,
    onChange: (Adjustments) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        val value = chosen.read(adjustments)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(chosen.label),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                // The sign is kept and zero is dimmed: on a slider whose middle
                // means "leave this alone", which side of the middle a value is
                // on matters more than the number itself.
                text = if (value == 0f) "0" else "%+d".format((value * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = if (value == 0f) Color.White.copy(alpha = 0.5f) else Color.White,
            )
        }

        WhiteSlider(
            value = value,
            onValueChange = { onChange(chosen.write(adjustments, it)) },
            range = -1f..1f,
        )

        // Scrolled rather than shared out evenly: "Насыщенность" is not a word
        // that fits in a sixth of a phone, and there will be ten of these before
        // this screen is finished.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Correction.entries.forEach { entry ->
                CorrectionChip(
                    label = stringResource(entry.label),
                    selected = entry == chosen,
                    touched = entry.read(adjustments) != 0f,
                    onClick = { onChoose(entry) },
                )
            }
        }
    }
}

/**
 * The filters, and how far the chosen one goes.
 *
 * The strength slider only appears once something has been chosen: a slider
 * that governs "no filter" governs nothing, and there is no reason for it to be
 * on screen looking as though it might.
 */
@Composable
private fun FilterTools(
    image: androidx.compose.ui.graphics.ImageBitmap?,
    chosen: FilterPreset,
    strength: Float,
    onChoose: (FilterPreset) -> Unit,
    onStrength: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (chosen != FilterPreset.NONE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.filter_strength),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%d%%".format((strength * 100).toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
            WhiteSlider(value = strength, onValueChange = onStrength, range = 0f..1f)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            FilterPreset.entries.forEach { preset ->
                if (image != null) {
                    FilterThumbnail(
                        image = image,
                        preset = preset,
                        strength = if (preset == chosen) strength else 1f,
                        selected = preset == chosen,
                        onClick = { onChoose(preset) },
                    )
                }
            }
        }
    }
}

/**
 * The name of one correction, and the way to choose it.
 *
 * The dot marks anything moved away from neutral: with a single slider serving
 * every correction, nothing else on screen would say which of them have been
 * touched — and a photograph that looks wrong for a reason nobody can find is
 * worse than one that looks wrong.
 */
@Composable
private fun CorrectionChip(
    label: String,
    selected: Boolean,
    touched: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .heightIn(min = TOUCH_TARGET)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        if (touched) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
        )
    }
}

/**
 * The slider used throughout the editor.
 *
 * White, because everything here sits on black over a photograph and the
 * default takes its colours from a theme that knows nothing about that.
 */
@Composable
private fun WhiteSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        ),
    )
}

/**
 * The shapes a crop can be held to.
 *
 * Each one answers with the width-to-height it wants, given the shape of the
 * photograph, or null for no answer at all — which is what "free" is. "As it
 * is" needs the picture to know what it means, which is why these are questions
 * rather than numbers.
 */
private enum class CropShape(val label: Int, val of: (Float) -> Float?) {
    FREE(R.string.crop_free, { null }),
    ORIGINAL(R.string.crop_original, { it }),
    SQUARE(R.string.crop_square, { 1f }),
    WIDE_4_3(R.string.crop_4_3, { 4f / 3f }),
    TALL_3_4(R.string.crop_3_4, { 3f / 4f }),
    WIDE_16_9(R.string.crop_16_9, { 16f / 9f }),
    TALL_9_16(R.string.crop_9_16, { 9f / 16f }),
}

/**
 * The light and colour corrections, in the order they are shown.
 *
 * Each one knows how to read itself out of the settings and how to put itself
 * back, which is what keeps the panel above from being six copies of the same
 * slider with different names on them.
 */
private enum class Correction(
    val label: Int,
    val read: (Adjustments) -> Float,
    val write: (Adjustments, Float) -> Adjustments,
) {
    BRIGHTNESS(R.string.adjust_brightness, { it.brightness }, { a, v -> a.copy(brightness = v) }),
    CONTRAST(R.string.adjust_contrast, { it.contrast }, { a, v -> a.copy(contrast = v) }),
    EXPOSURE(R.string.adjust_exposure, { it.exposure }, { a, v -> a.copy(exposure = v) }),
    SHADOWS(R.string.adjust_shadows, { it.shadows }, { a, v -> a.copy(shadows = v) }),
    HIGHLIGHTS(R.string.adjust_highlights, { it.highlights }, { a, v -> a.copy(highlights = v) }),
    SATURATION(R.string.adjust_saturation, { it.saturation }, { a, v -> a.copy(saturation = v) }),
    TEMPERATURE(
        R.string.adjust_temperature,
        { it.temperature },
        { a, v -> a.copy(temperature = v) },
    ),
    TINT(R.string.adjust_tint, { it.tint }, { a, v -> a.copy(tint = v) }),
    SHARPNESS(R.string.adjust_sharpness, { it.sharpness }, { a, v -> a.copy(sharpness = v) }),
    VIGNETTE(R.string.adjust_vignette, { it.vignette }, { a, v -> a.copy(vignette = v) }),
}

/** Two megapixels is more than any phone screen shows and decodes in a blink. */
private const val PREVIEW_PIXELS = 2_000_000

/**
 * Decodes the original at the largest size this device can hold, applies the
 * edits once, and writes the result.
 *
 * The full decode happens here and nowhere else: the editor never holds it, so
 * a photograph too large to edit comfortably is still edited comfortably.
 */
private suspend fun saveEdit(
    context: android.content.Context,
    item: MediaItem,
    ops: EditOps,
    mode: SaveMode,
    quality: Int,
): SaveOutcome = withContext(Dispatchers.IO) {
    // Read before anything is written: overwriting destroys the original, and
    // by then there is nothing left to read the date and the place off.
    val exif = context.carriedExif(item)

    val ceiling = maxEditablePixels(Runtime.getRuntime().maxMemory())
    val full = context.decodeForEditing(item, ceiling) ?: return@withContext SaveOutcome.FAILED
    val edited = applyOps(full, ops)

    val outcome = when (mode) {
        SaveMode.COPY -> context.saveEditedCopy(item, edited, quality, exif)
        SaveMode.OVERWRITE -> context.overwriteWith(item, edited, quality, exif)
    }

    if (edited !== full) edited.recycle()
    full.recycle()
    outcome
}
