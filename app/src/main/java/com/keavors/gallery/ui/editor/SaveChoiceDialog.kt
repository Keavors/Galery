package com.keavors.gallery.ui.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.keavors.gallery.R

/**
 * Copy or overwrite.
 *
 * Asked every time rather than remembered, because the two are not variations of
 * one action: one keeps the photograph as it was and one does not, and the right
 * answer is different for a snapshot and for something irreplaceable.
 */
@Composable
fun SaveChoiceDialog(
    onCopy: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_save)) },
        text = { Text(stringResource(R.string.editor_save_body)) },
        confirmButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.editor_save_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onOverwrite) {
                Text(stringResource(R.string.editor_save_overwrite))
            }
        },
    )
}
