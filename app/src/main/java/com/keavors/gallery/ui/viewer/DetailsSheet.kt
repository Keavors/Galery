package com.keavors.gallery.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.MediaDetails
import com.keavors.gallery.data.GallerySettings
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.formatBytes
import com.keavors.gallery.data.formatMegapixels
import com.keavors.gallery.data.formatResolution
import com.keavors.gallery.data.readDetails
import com.keavors.gallery.ui.photos.formatDuration
import java.util.Locale

/**
 * Everything the file knows about itself.
 *
 * Rows with nothing to say are left out rather than shown empty: a sheet full of
 * dashes reads as broken, and a screenshot legitimately has no aperture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(item: MediaItem, settings: GallerySettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())

    var details by remember(item.id) { mutableStateOf(MediaDetails()) }
    LaunchedEffect(item.id) { details = context.readDetails(item) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            DetailRow(stringResource(R.string.details_taken), formatShotDate(item.takenAt, locale, settings.dateStyle))
            DetailRow(
                stringResource(R.string.details_resolution),
                buildResolution(item.width, item.height),
            )
            DetailRow(stringResource(R.string.details_size), formatBytes(item.sizeBytes, locale, settings.binarySizes))
            if (item.isVideo && item.durationMs > 0) {
                DetailRow(stringResource(R.string.details_duration), formatDuration(item.durationMs))
            }
            DetailRow(stringResource(R.string.details_type), item.mimeType)
            DetailRow(stringResource(R.string.details_folder), item.relativePath.trimEnd('/'))

            DetailRow(stringResource(R.string.details_camera), details.camera.orEmpty())
            DetailRow(stringResource(R.string.details_lens), details.lens.orEmpty())
            DetailRow(stringResource(R.string.details_iso), details.iso.orEmpty())
            DetailRow(
                stringResource(R.string.details_exposure),
                details.exposure?.let { "$it ${stringResource(R.string.unit_seconds)}" }.orEmpty(),
            )
            DetailRow(stringResource(R.string.details_aperture), details.aperture.orEmpty())
            DetailRow(
                stringResource(R.string.details_focal),
                details.focalLength?.let { "$it ${stringResource(R.string.unit_mm)}" }.orEmpty(),
            )
            details.flash?.let {
                DetailRow(
                    stringResource(R.string.details_flash),
                    stringResource(if (it) R.string.flash_fired else R.string.flash_off),
                )
            }
            if (details.latitude != null && details.longitude != null) {
                DetailRow(
                    stringResource(R.string.details_location),
                    String.format(Locale.US, "%.5f, %.5f", details.latitude, details.longitude),
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun buildResolution(width: Int, height: Int): String {
    val resolution = formatResolution(width, height)
    if (resolution.isEmpty()) return ""
    val megapixels = formatMegapixels(width, height)
    return "$resolution · $megapixels ${stringResource(R.string.unit_megapixels)}"
}
