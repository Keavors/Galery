package com.keavors.gallery.ui

import com.keavors.gallery.data.MediaItem

/**
 * The video that would carry on in a corner of the screen if the app were left.
 *
 * A plain holder rather than state: nothing draws it. The viewer puts the video
 * it is showing in here and takes it out again when it stops showing one, and
 * the activity reads it at the single moment Android offers — the instant
 * somebody presses home, which is not a moment anything can be composed in.
 */
class PipHolder {
    var video: MediaItem? = null
}
