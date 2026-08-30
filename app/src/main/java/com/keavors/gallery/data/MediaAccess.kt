package com.keavors.gallery.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/** How much of the library the app is currently allowed to see. */
enum class MediaAccess {
    /** Nothing granted yet. */
    NONE,

    /** Android 14+: the user picked individual photos instead of the library. */
    PARTIAL,

    /** The whole library. */
    FULL,
}

/**
 * The permissions the gallery asks for on first run.
 *
 * READ_MEDIA_VISUAL_USER_SELECTED is only requested on Android 14 and later,
 * where the system offers a "select photos" answer to the dialog. Asking for it
 * is what makes that answer detectable rather than silently looking like an
 * almost-empty library.
 */
val mediaPermissions: Array<String> = buildList {
    add(Manifest.permission.READ_MEDIA_IMAGES)
    add(Manifest.permission.READ_MEDIA_VIDEO)
    add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }
}.toTypedArray()

fun Context.mediaAccess(): MediaAccess {
    val images = isGranted(Manifest.permission.READ_MEDIA_IMAGES)
    val video = isGranted(Manifest.permission.READ_MEDIA_VIDEO)
    if (images && video) return MediaAccess.FULL

    val partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    return if (partial) MediaAccess.PARTIAL else MediaAccess.NONE
}

/**
 * Whether the special "media management" access has been granted in system
 * settings. With it, deleting, trashing and favouriting happen without a
 * confirmation dialog every single time.
 */
fun Context.canManageMedia(): Boolean = MediaStore.canManageMedia(this)

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
