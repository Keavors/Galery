package com.keavors.gallery.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Press and hold to see the photograph as it arrived.
 *
 * Every editor has this and none of them explain it, because it explains
 * itself the first time it happens: a picture that has been worked on for a
 * while stops looking edited, and the only way to tell whether any of it helped
 * is to see the other one for a second.
 *
 * A finger that moves is doing something else — dragging a crop frame, most
 * likely — so the hold is abandoned the moment it travels, and nothing here is
 * consumed either way. This sits over the tools rather than instead of them.
 */
@Composable
internal fun Modifier.compareWhileHeld(onCompare: (Boolean) -> Unit): Modifier {
    // The gesture detector below is started once and never again, so what it
    // reports through has to be read live rather than captured.
    val report by rememberUpdatedState(onCompare)

    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

            report(true)
            try {
                // Held for exactly as long as the finger is down. Waiting for
                // the whole gesture to end rather than for an up event means a
                // call, a notification or a palm on the screen puts the edited
                // picture back rather than leaving the original stuck there.
                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.id == down.id && it.pressed })
            } finally {
                report(false)
            }
        }
    }
}
