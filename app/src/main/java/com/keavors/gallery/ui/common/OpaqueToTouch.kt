package com.keavors.gallery.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Makes a screen that covers another one stop touches the way it stops light.
 *
 * The screens stacked in GalleryApp are siblings in a single box, and a finger
 * landing on a part of the top one that has no control under it is offered to
 * the screen underneath instead. That is how a tap beside a button in the editor
 * reached the pager behind it and came back on a different photograph, and how a
 * swipe across a lock screen worked the gallery it was supposed to be hiding.
 *
 * Being here is the whole of the fix: the hit test stops at the first sibling it
 * lands on, so nothing below is ever offered the touch. The events themselves
 * are left alone — this is the root of the overlay and is asked before its own
 * buttons are, so anything taken here would be taken from them.
 */
fun Modifier.opaqueToTouch(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial)
    }
}
