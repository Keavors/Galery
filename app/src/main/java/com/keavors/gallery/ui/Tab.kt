package com.keavors.gallery.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keavors.gallery.R

/** The four sections along the bottom of the app. Order is the display order. */
enum class Tab(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    @param:StringRes val description: Int,
) {
    PHOTOS(R.string.tab_photos, R.drawable.ic_tab_photos, R.string.photos_placeholder),
    ALBUMS(R.string.tab_albums, R.drawable.ic_tab_albums, R.string.albums_placeholder),
    TRASH(R.string.tab_trash, R.drawable.ic_tab_trash, R.string.trash_placeholder),
    SETTINGS(R.string.tab_settings, R.drawable.ic_tab_settings, R.string.settings_placeholder),
}
