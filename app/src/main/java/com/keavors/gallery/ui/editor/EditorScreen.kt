package com.keavors.gallery.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.CropRect
import com.keavors.gallery.data.EditOps
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.applyOps
import com.keavors.gallery.data.decodeForEditing
import com.keavors.gallery.data.maxEditablePixels
import com.keavors.gallery.data.overwriteWith
import com.keavors.gallery.data.saveEditedCopy
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

/**
 * The editor.
 *
 * Everything on screen is drawn from a small copy of the photograph, so turning
 * and cropping are instant whatever the original weighs. The edits themselves
 * are a short list of intentions, not pixels — when the time comes to save, that
 * same list is applied once to a full-size decode.
 *
 * This first pass is geometry: turns, mirroring, straightening and the crop.
 * Colour and markup follow.
 */
@Composable
fun EditorScreen(
    item: MediaItem,
    jpegQuality: Int,
    onSaved: () -> Unit,
    onFailed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ops by remember(item.id) { mutableStateOf(EditOps.None) }
    var preview by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var working by remember { mutableStateOf(false) }
    var askingHow by remember { mutableStateOf(false) }

    // The preview is deliberately small: it is only ever shown at screen size,
    // and decoding a hundred megapixels to draw four hundred thousand of them
    // would make opening the editor a wait.
    LaunchedEffect(item.id) {
        preview = context.decodeForEditing(item, PREVIEW_PIXELS)
    }

    // Turns and straightening only: the crop is the frame drawn on top of this
    // picture, and applying it here as well would cut away the very part the
    // frame is measured against — every drag would then crop the crop.
    val geometry = ops.copy(crop = CropRect.Whole)
    val shown = remember(preview, geometry) {
        preview?.let { if (geometry.isIdentity) it else applyOps(it, geometry) }
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
            val ok = saveEdit(context, item, ops, mode, jpegQuality)
            working = false
            if (ok) onSaved() else onFailed()
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
            .safeDrawingPadding(),
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
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = shown
            if (bitmap == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                CropCanvas(
                    image = bitmap.asImageBitmap(),
                    crop = ops.crop,
                    onCropChange = { ops = ops.cropped(it) },
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

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // The same cells as the viewer's bottom bar: a quarter of the width
            // each, the whole of it answering a thumb rather than the icon in
            // the middle of it.
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BarAction(
                    icon = R.drawable.ic_rotate,
                    label = stringResource(R.string.editor_rotate),
                    onClick = { ops = ops.turned() },
                    modifier = Modifier.weight(1f),
                )
                BarAction(
                    icon = R.drawable.ic_flip,
                    label = stringResource(R.string.editor_flip),
                    onClick = { ops = ops.flipped() },
                    modifier = Modifier.weight(1f),
                )
                BarAction(
                    icon = R.drawable.ic_crop_reset,
                    label = stringResource(R.string.editor_reset_crop),
                    onClick = { ops = ops.cropped(CropRect.Whole) },
                    modifier = Modifier.weight(1f),
                )
                BarAction(
                    icon = R.drawable.ic_restore,
                    label = stringResource(R.string.editor_reset),
                    onClick = { ops = EditOps.None },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(
                    R.string.editor_straighten,
                    ops.straighten.toInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            Slider(
                value = ops.straighten,
                onValueChange = { ops = ops.straightened(it) },
                valueRange = -EditOps.MAX_STRAIGHTEN..EditOps.MAX_STRAIGHTEN,
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
): Boolean = withContext(Dispatchers.IO) {
    val ceiling = maxEditablePixels(Runtime.getRuntime().maxMemory())
    val full = context.decodeForEditing(item, ceiling) ?: return@withContext false
    val edited = applyOps(full, ops)

    val ok = when (mode) {
        SaveMode.COPY -> context.saveEditedCopy(item, edited, quality) != null
        SaveMode.OVERWRITE -> context.overwriteWith(item, edited, quality)
    }

    if (edited !== full) edited.recycle()
    full.recycle()
    ok
}
