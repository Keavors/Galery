package com.keavors.gallery.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.Accent
import com.keavors.gallery.data.DateStyle
import com.keavors.gallery.data.SaveChoice
import com.keavors.gallery.data.TileShape
import com.keavors.gallery.data.ViewerTitle
import com.keavors.gallery.data.GallerySettings
import com.keavors.gallery.data.MediaAccess
import com.keavors.gallery.data.Palette
import com.keavors.gallery.data.SortBy
import com.keavors.gallery.data.SortOrder
import com.keavors.gallery.data.ThemeMode
import com.keavors.gallery.data.ZoomLevel
import kotlin.math.roundToInt

/** One line on the settings screen, with the text the search box matches on. */
private class SettingRow(val title: String, val content: @Composable () -> Unit)

private class SettingSection(val title: String, val rows: List<SettingRow>)

/**
 * Everything the app can be told to do differently.
 *
 * There are enough of these that a list alone would be unusable, so there is a
 * search box: typing narrows to the matching lines and drops the sections that
 * no longer have any. Nothing here is a switch that does nothing — a setting
 * appears when the thing it governs exists.
 */
@Composable
fun SettingsScreen(
    settings: GallerySettings,
    onChange: (GallerySettings) -> Unit,
    access: MediaAccess,
    canManageMedia: Boolean,
    versionName: String,
    cacheSummary: String,
    canLock: Boolean,
    onOpenSystemSettings: () -> Unit,
    onRequestManageMedia: () -> Unit,
    onClearCache: () -> Unit,
    onExport: () -> Unit,
    onExportVault: () -> Unit,
    vaultCount: Int,
    onImport: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val sections = listOf(
        SettingSection(
            stringResource(R.string.settings_access),
            listOf(
                SettingRow(stringResource(R.string.settings_access)) {
                    ActionRow(
                        title = stringResource(
                            when (access) {
                                MediaAccess.FULL -> R.string.access_state_full
                                MediaAccess.PARTIAL -> R.string.access_state_partial
                                MediaAccess.NONE -> R.string.access_state_none
                            }
                        ),
                        summary = null,
                        action = if (access == MediaAccess.FULL) null
                        else stringResource(R.string.access_open_settings),
                        onAction = onOpenSystemSettings,
                    )
                },
                SettingRow(stringResource(R.string.manage_title)) {
                    ActionRow(
                        title = stringResource(R.string.manage_title),
                        summary = stringResource(
                            if (canManageMedia) R.string.manage_granted else R.string.manage_missing
                        ),
                        action = if (canManageMedia) null else stringResource(R.string.manage_request),
                        onAction = onRequestManageMedia,
                    )
                },
            ),
        ),

        SettingSection(
            stringResource(R.string.settings_appearance),
            listOf(
                choiceRow(
                    title = stringResource(R.string.set_theme),
                    current = settings.themeMode,
                    options = ThemeMode.entries,
                    label = { stringResource(themeModeLabel(it)) },
                    onPick = { onChange(settings.copy(themeMode = it)) },
                ),
                choiceRow(
                    title = stringResource(R.string.set_palette),
                    current = settings.palette,
                    options = Palette.entries,
                    label = { stringResource(paletteLabel(it)) },
                    onPick = { onChange(settings.copy(palette = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_pure_black),
                    summary = stringResource(R.string.set_pure_black_summary),
                    checked = settings.pureBlack,
                    onChange = { onChange(settings.copy(pureBlack = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_tile_gap),
                    value = settings.tileGapDp,
                    range = 0..8,
                    onChange = { onChange(settings.copy(tileGapDp = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_tile_corner),
                    value = settings.tileCornerDp,
                    range = 0..16,
                    onChange = { onChange(settings.copy(tileCornerDp = it)) },
                ),
                choiceRow(
                    title = stringResource(R.string.set_accent),
                    current = settings.accent,
                    options = Accent.entries,
                    label = { stringResource(accentLabel(it)) },
                    onPick = { onChange(settings.copy(accent = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_animations),
                    summary = stringResource(R.string.set_animations_summary),
                    checked = settings.animations,
                    onChange = { onChange(settings.copy(animations = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_animation_speed),
                    value = settings.animationSpeed,
                    range = 25..300,
                    step = 25,
                    onChange = { onChange(settings.copy(animationSpeed = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_font_scale),
                    value = settings.fontScale,
                    range = 80..140,
                    step = 5,
                    onChange = { onChange(settings.copy(fontScale = it)) },
                ),
            ),
        ),

        SettingSection(
            stringResource(R.string.tab_photos),
            listOf(
                choiceRow(
                    title = stringResource(R.string.set_default_zoom),
                    current = settings.defaultZoom,
                    options = ZoomLevel.entries,
                    label = { it.columns.toString() },
                    onPick = { onChange(settings.copy(defaultZoomColumns = it.columns)) },
                ),
                choiceRow(
                    title = stringResource(R.string.set_sort_by),
                    current = settings.sortBy,
                    options = SortBy.entries,
                    label = { stringResource(sortByLabel(it)) },
                    onPick = { onChange(settings.copy(sortBy = it)) },
                ),
                choiceRow(
                    title = stringResource(R.string.set_sort_order),
                    current = settings.sortOrder,
                    options = SortOrder.entries,
                    label = { stringResource(sortOrderLabel(it)) },
                    onPick = { onChange(settings.copy(sortOrder = it)) },
                ),
                choiceRow(
                    title = stringResource(R.string.set_tile_shape),
                    current = settings.tileShape,
                    options = TileShape.entries,
                    label = { stringResource(tileShapeLabel(it)) },
                    onPick = { onChange(settings.copy(tileShape = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_show_videos),
                    summary = null,
                    checked = settings.showVideos,
                    onChange = { onChange(settings.copy(showVideos = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_show_screenshots),
                    summary = null,
                    checked = settings.showScreenshots,
                    onChange = { onChange(settings.copy(showScreenshots = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_show_downloads),
                    summary = null,
                    checked = settings.showDownloads,
                    onChange = { onChange(settings.copy(showDownloads = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_show_hidden_folders),
                    summary = stringResource(R.string.set_show_hidden_folders_summary),
                    checked = settings.showHiddenFolders,
                    onChange = { onChange(settings.copy(showHiddenFolders = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_tile_badges),
                    summary = stringResource(R.string.set_tile_badges_summary),
                    checked = settings.tileBadges,
                    onChange = { onChange(settings.copy(tileBadges = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_relative_dates),
                    summary = stringResource(R.string.set_relative_dates_summary),
                    checked = settings.relativeDates,
                    onChange = { onChange(settings.copy(relativeDates = it)) },
                ),
            ),
        ),

        SettingSection(
            stringResource(R.string.settings_viewer),
            listOf(
                switchRow(
                    title = stringResource(R.string.set_chrome_on_open),
                    summary = null,
                    checked = settings.chromeOnOpen,
                    onChange = { onChange(settings.copy(chromeOnOpen = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_auto_hide),
                    value = settings.autoHideSeconds,
                    range = 0..20,
                    zeroLabel = stringResource(R.string.set_off),
                    onChange = { onChange(settings.copy(autoHideSeconds = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_swipe_down),
                    summary = null,
                    checked = settings.swipeDownCloses,
                    onChange = { onChange(settings.copy(swipeDownCloses = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_swipe_up),
                    summary = null,
                    checked = settings.swipeUpDetails,
                    onChange = { onChange(settings.copy(swipeUpDetails = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_double_tap),
                    summary = null,
                    checked = settings.doubleTapZoom,
                    onChange = { onChange(settings.copy(doubleTapZoom = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_max_zoom),
                    value = settings.maxZoom,
                    range = 2..32,
                    step = 2,
                    onChange = { onChange(settings.copy(maxZoom = it)) },
                ),
                choiceRow(
                    title = stringResource(R.string.set_viewer_title),
                    current = settings.viewerTitle,
                    options = ViewerTitle.entries,
                    label = { stringResource(viewerTitleLabel(it)) },
                    onPick = { onChange(settings.copy(viewerTitle = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_loop),
                    summary = stringResource(R.string.set_loop_summary),
                    checked = settings.loopPaging,
                    onChange = { onChange(settings.copy(loopPaging = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_brightness),
                    summary = null,
                    checked = settings.maxBrightness,
                    onChange = { onChange(settings.copy(maxBrightness = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_keep_screen_on),
                    summary = null,
                    checked = settings.keepScreenOn,
                    onChange = { onChange(settings.copy(keepScreenOn = it)) },
                ),
            ),
        ),

        SettingSection(
            stringResource(R.string.album_videos),
            listOf(
                switchRow(
                    title = stringResource(R.string.set_video_autoplay),
                    summary = null,
                    checked = settings.videoAutoplay,
                    onChange = { onChange(settings.copy(videoAutoplay = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_video_sound),
                    summary = null,
                    checked = settings.videoSound,
                    onChange = { onChange(settings.copy(videoSound = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_video_repeat),
                    summary = null,
                    checked = settings.videoRepeat,
                    onChange = { onChange(settings.copy(videoRepeat = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_video_speed),
                    value = settings.videoSpeed,
                    range = 25..200,
                    step = 25,
                    onChange = { onChange(settings.copy(videoSpeed = it)) },
                ),
            ),
        ),

        SettingSection(
            stringResource(R.string.tab_trash),
            listOf(
                switchRow(
                    title = stringResource(R.string.set_undo_delete),
                    summary = stringResource(R.string.set_undo_delete_summary),
                    checked = settings.undoDelete,
                    onChange = { onChange(settings.copy(undoDelete = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_confirm_forever),
                    summary = stringResource(R.string.set_confirm_forever_summary),
                    checked = settings.confirmForever,
                    onChange = { onChange(settings.copy(confirmForever = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_remaining_days),
                    summary = null,
                    checked = settings.showRemainingDays,
                    onChange = { onChange(settings.copy(showRemainingDays = it)) },
                ),
            ),
        ),

        SettingSection(
            stringResource(R.string.editor_title),
            listOf(
                choiceRow(
                    title = stringResource(R.string.set_save_choice),
                    current = settings.saveChoice,
                    options = SaveChoice.entries,
                    label = { stringResource(saveChoiceLabel(it)) },
                    onPick = { onChange(settings.copy(saveChoice = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_jpeg_quality),
                    value = settings.jpegQuality,
                    range = 60..100,
                    onChange = { onChange(settings.copy(jpegQuality = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_save_beside),
                    summary = stringResource(R.string.set_save_beside_summary),
                    checked = settings.saveBeside,
                    onChange = { onChange(settings.copy(saveBeside = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_keep_exif),
                    summary = stringResource(R.string.set_keep_exif_summary),
                    checked = settings.keepExif,
                    onChange = { onChange(settings.copy(keepExif = it)) },
                ),
            ),
        ),

        SettingSection(
            stringResource(R.string.settings_security),
            listOfNotNull(
                if (canLock) {
                    switchRow(
                        title = stringResource(R.string.set_app_lock),
                        summary = stringResource(R.string.set_app_lock_summary),
                        checked = settings.appLock,
                        onChange = { onChange(settings.copy(appLock = it)) },
                    )
                } else {
                    // Offering a lock on a phone with no screen lock would be a
                    // promise the phone cannot keep.
                    SettingRow(stringResource(R.string.set_app_lock)) {
                        ActionRow(
                            title = stringResource(R.string.set_app_lock),
                            summary = stringResource(R.string.set_no_device_lock),
                            action = null,
                            onAction = {},
                        )
                    }
                },
                // The way out of the bargain the vault strikes: files that no
                // other app can see are also files that go when this one is
                // uninstalled, and this is the button that makes that payable.
                SettingRow(stringResource(R.string.vault_export)) {
                    ActionRow(
                        title = stringResource(R.string.vault_export),
                        summary = stringResource(R.string.vault_export_summary),
                        action = if (vaultCount > 0) vaultCount.toString() else null,
                        onAction = onExportVault,
                    )
                },
                switchRow(
                    title = stringResource(R.string.set_hide_recents),
                    summary = stringResource(R.string.set_hide_recents_summary),
                    checked = settings.hideInRecents,
                    onChange = { onChange(settings.copy(hideInRecents = it)) },
                ),
            ),
        ),

        SettingSection(
            stringResource(R.string.settings_general),
            listOf(
                choiceRow(
                    title = stringResource(R.string.set_date_style),
                    current = settings.dateStyle,
                    options = DateStyle.entries,
                    label = { stringResource(dateStyleLabel(it)) },
                    onPick = { onChange(settings.copy(dateStyle = it)) },
                ),
                switchRow(
                    title = stringResource(R.string.set_binary_sizes),
                    summary = stringResource(R.string.set_binary_sizes_summary),
                    checked = settings.binarySizes,
                    onChange = { onChange(settings.copy(binarySizes = it)) },
                ),
                sliderRow(
                    title = stringResource(R.string.set_cache_limit),
                    value = settings.cacheLimitMb,
                    range = 32..1024,
                    step = 32,
                    onChange = { onChange(settings.copy(cacheLimitMb = it)) },
                ),
                choiceRow(
                    title = stringResource(R.string.set_language),
                    current = settings.language,
                    options = listOf("", "ru", "en"),
                    label = { stringResource(languageLabel(it)) },
                    onPick = { onChange(settings.copy(language = it)) },
                ),
                SettingRow(stringResource(R.string.set_cache)) {
                    ActionRow(
                        title = stringResource(R.string.set_cache),
                        summary = cacheSummary,
                        action = stringResource(R.string.set_cache_clear),
                        onAction = onClearCache,
                    )
                },
                SettingRow(stringResource(R.string.set_export)) {
                    ActionRow(
                        title = stringResource(R.string.set_export),
                        summary = stringResource(R.string.set_export_summary),
                        action = stringResource(R.string.set_export_do),
                        onAction = onExport,
                    )
                },
                SettingRow(stringResource(R.string.set_import)) {
                    ActionRow(
                        title = stringResource(R.string.set_import),
                        summary = null,
                        action = stringResource(R.string.set_import_do),
                        onAction = onImport,
                    )
                },
                SettingRow(stringResource(R.string.set_reset)) {
                    ActionRow(
                        title = stringResource(R.string.set_reset),
                        summary = null,
                        action = stringResource(R.string.set_reset_do),
                        onAction = { confirmReset = true },
                    )
                },
                SettingRow(stringResource(R.string.set_about)) {
                    ActionRow(
                        title = stringResource(R.string.set_about),
                        summary = versionName,
                        action = null,
                        onAction = {},
                    )
                },
            ),
        ),
    )

    val visible = sections.mapNotNull { section ->
        val rows = section.rows.filter {
            query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)
        }
        if (rows.isEmpty()) null else SettingSection(section.title, rows)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.settings_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            visible.forEach { section ->
                item(key = "h:" + section.title) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
                    )
                }
                items(section.rows.size) { index ->
                    section.rows[index].content()
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.set_reset)) },
            text = { Text(stringResource(R.string.set_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text(stringResource(R.string.set_reset_do)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

// ------------------------------------------------------------- rows ---------

private fun switchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) = SettingRow(title) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Labels(title, summary, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun <T> choiceRow(
    title: String,
    current: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
) = SettingRow(title) {
    var open by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Labels(title, label(current), Modifier.weight(1f))
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    open = false
                                    onPick(option)
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(selected = option == current, onClick = {
                                open = false
                                onPick(option)
                            })
                            Text(
                                text = label(option),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun sliderRow(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    zeroLabel: String? = null,
    /** How far apart the notches are: 1 for a count, 25 for a percentage. */
    step: Int = 1,
) = SettingRow(title) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Labels(
            title = title,
            summary = if (value == 0 && zeroLabel != null) zeroLabel else value.toString(),
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = value.toFloat(),
            // Rounded to the notch rather than to the nearest whole number: a
            // percentage that can stop at 87 is a percentage nobody chose.
            onValueChange = { picked ->
                val notches = ((picked - range.first) / step).roundToInt()
                onChange((range.first + notches * step).coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    summary: String?,
    action: String?,
    onAction: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Labels(title, summary, Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun Labels(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------- labels -------

private fun themeModeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun paletteLabel(palette: Palette) = when (palette) {
    Palette.COFFEE -> R.string.palette_coffee
    Palette.MONO -> R.string.palette_mono
    Palette.DYNAMIC -> R.string.palette_dynamic
}

private fun sortByLabel(sort: SortBy) = when (sort) {
    SortBy.TAKEN -> R.string.sort_taken
    SortBy.MODIFIED -> R.string.sort_modified
    SortBy.NAME -> R.string.sort_name
    SortBy.SIZE -> R.string.sort_size
}

private fun sortOrderLabel(order: SortOrder) = when (order) {
    SortOrder.NEWEST_FIRST -> R.string.sort_newest
    SortOrder.OLDEST_FIRST -> R.string.sort_oldest
}

private fun languageLabel(tag: String) = when (tag) {
    "ru" -> R.string.lang_ru
    "en" -> R.string.lang_en
    else -> R.string.lang_system
}

private fun accentLabel(accent: Accent) = when (accent) {
    Accent.DEFAULT -> R.string.accent_default
    Accent.AMBER -> R.string.accent_amber
    Accent.AZURE -> R.string.accent_azure
    Accent.MOSS -> R.string.accent_moss
    Accent.PLUM -> R.string.accent_plum
}

private fun tileShapeLabel(shape: TileShape) = when (shape) {
    TileShape.SQUARE -> R.string.tiles_square
    TileShape.MOSAIC -> R.string.tiles_mosaic
}

private fun viewerTitleLabel(title: ViewerTitle) = when (title) {
    ViewerTitle.DATE_AND_NAME -> R.string.title_date_and_name
    ViewerTitle.DATE -> R.string.title_date
    ViewerTitle.NAME -> R.string.title_name
    ViewerTitle.NOTHING -> R.string.title_nothing
}

private fun saveChoiceLabel(choice: SaveChoice) = when (choice) {
    SaveChoice.ASK -> R.string.save_ask
    SaveChoice.COPY -> R.string.save_copy
    SaveChoice.OVERWRITE -> R.string.save_overwrite
}

private fun dateStyleLabel(style: DateStyle) = when (style) {
    DateStyle.AUTO -> R.string.date_style_auto
    DateStyle.DAY_FIRST -> R.string.date_style_day
    DateStyle.YEAR_FIRST -> R.string.date_style_year
}
