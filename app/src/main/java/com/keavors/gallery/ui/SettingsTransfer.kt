package com.keavors.gallery.ui

import android.content.Context
import android.net.Uri
import com.keavors.gallery.data.SettingsStore

/**
 * Settings in and out of a file.
 *
 * Both directions go through the system document picker rather than a folder
 * this app chooses: the file lands where the person keeps their own things, and
 * nothing here needs permission to write anywhere.
 */
suspend fun writeSettingsTo(context: Context, store: SettingsStore, uri: Uri): Boolean =
    runCatching {
        val json = store.export()
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(json.toByteArray())
        } ?: return false
        true
    }.getOrElse { false }

/** Reads a settings document back. Returns false if the file was not one. */
suspend fun readSettingsFrom(context: Context, store: SettingsStore, uri: Uri): Boolean =
    runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().decodeToString()
        } ?: return false
        store.import(json)
    }.getOrElse { false }
