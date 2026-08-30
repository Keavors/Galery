package com.keavors.gallery.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.keavors.gallery.data.MediaWriter

/**
 * A writer wired to the activity result plumbing every media change needs.
 *
 * The result is deliberately ignored: whether the change went through or was
 * refused, MediaStore tells the observer either way, and the library reloads
 * itself. Nothing here has to keep score.
 */
@Composable
fun rememberMediaWriter(managesMedia: Boolean): MediaWriter {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }
    return remember(context, managesMedia) {
        MediaWriter(context, managesMedia) { launcher.launch(it) }
    }
}
