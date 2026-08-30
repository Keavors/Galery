package com.keavors.gallery.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Catches two-finger pinches without taking scrolling away from one finger.
 *
 * The obvious tool, detectTransformGestures, swallows single-pointer gestures
 * too, which would leave the timeline unable to scroll. This watches the Initial
 * pass instead, so it sees every event before the list does, and only consumes
 * the ones that have a second finger down. Everything else falls through to the
 * list untouched.
 *
 * @param onStart fires once, when the second finger arrives.
 * @param onZoom cumulative scale since the gesture began; 1 means no change.
 * @param onEnd fires when the last finger lifts, with the final cumulative scale.
 */
fun Modifier.pinchZoom(
    onStart: () -> Unit,
    onZoom: (Float) -> Unit,
    onEnd: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulated = 1f
        var pinching = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break

            if (event.changes.size >= 2) {
                if (!pinching) {
                    pinching = true
                    onStart()
                }
                val step = event.calculateZoom()
                if (step != 0f && step.isFinite()) {
                    accumulated *= step
                    onZoom(accumulated)
                }
                // Consumed only while two fingers are down, so a pinch never
                // leaves the list mid-fling afterwards.
                event.changes.forEach { it.consume() }
            }
        }

        if (pinching) onEnd(accumulated)
    }
}
