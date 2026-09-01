package com.keavors.gallery.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keavors.gallery.R

/** The four sections along the bottom of the app. Order is the display order. */
enum class Tab(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
) {
    PHOTOS(R.string.tab_photos, R.drawable.ic_tab_photos),
    ALBUMS(R.string.tab_albums, R.drawable.ic_tab_albums),
    TRASH(R.string.tab_trash, R.drawable.ic_tab_trash),
    SETTINGS(R.string.tab_settings, R.drawable.ic_tab_settings),
}
