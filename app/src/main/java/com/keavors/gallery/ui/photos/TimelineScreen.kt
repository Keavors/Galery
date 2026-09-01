package com.keavors.gallery.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.GallerySettings
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaWriter
import com.keavors.gallery.data.TimelineRow
import com.keavors.gallery.data.ZoomLevel
import com.keavors.gallery.data.buildTimeline
import com.keavors.gallery.data.matching
import com.keavors.gallery.data.parseSearch
import com.keavors.gallery.data.canBeHidden
import com.keavors.gallery.data.firstItemFrom
import com.keavors.gallery.data.rowOf
import com.keavors.gallery.data.sectionItems
import com.keavors.gallery.data.shareMedia
import com.keavors.gallery.data.thumbnailBucketPx
import com.keavors.gallery.data.zoomSteps
import com.keavors.gallery.ui.common.ConfirmDialog
import com.keavors.gallery.ui.common.TextPromptDialog
import com.keavors.gallery.ui.common.pinchZoom
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Bounds on the live scale during a pinch, so the grid cannot be dragged to nothing. */
private const val MIN_LIVE_SCALE = 0.55f
private const val MAX_LIVE_SCALE = 1.9f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    items: List<MediaItem>,
    settings: GallerySettings,
    /** Whether the list carries a search box at the top of it. */
    searchable: Boolean = false,
    writer: MediaWriter,
    albumActions: AlbumActions,
    onHide: (List<MediaItem>) -> Unit,
    onOpen: (item: MediaItem, thumbBucketPx: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
    val today = remember(items) { LocalDate.now(zone) }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.viewer_share)

    val tileGap = settings.tileGapDp.dp
    val tileCorner = settings.tileCornerDp.dp

    // Keyed on the setting so changing the starting zoom takes effect at once
    // rather than waiting for the app to be restarted.
    var levelOrdinal by rememberSaveable(settings.defaultZoomColumns) {
        mutableIntStateOf(settings.defaultZoom.ordinal)
    }
    val level = ZoomLevel.entries[levelOrdinal]

    // Kept across a rotation but not across a visit: coming back to the tab is
    // coming back to the pictures, not to what somebody was looking for once.
    var query by remember { mutableStateOf("") }
    val terms = remember(query, locale) { parseSearch(query, locale) }
    val found = remember(items, terms, zone) { items.matching(terms, zone) }
    val rows = remember(found, level, searchable) {
        buildTimeline(found, level, zone, withSearch = searchable)
    }

    // The level keys the gesture handler, but the rows can also change under it
    // when the library reloads mid-pinch, and that must not rebuild the handler.
    val currentRows by rememberUpdatedState(rows)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    // The photo the fingers were on when the pinch started. After the rows are
    // re-cut it is the only way back to the place the person was looking at.
    var anchorId by remember { mutableStateOf<Long?>(null) }

    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var choosingAlbum by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf(false) }
    var namingAlbum by remember { mutableStateOf(false) }
    // Which of the two folder errands is being run, or null while nobody is
    // choosing a folder. One piece of state rather than two booleans that could
    // both be true.
    var folderErrand by remember { mutableStateOf<FolderErrand?>(null) }

    // Files can vanish from under a selection — deleted here, or by another app
    // entirely — and a count of things nobody can point at helps no one.
    val present = remember(items) { items.mapTo(HashSet()) { it.id } }
    if (selected.any { it !in present }) selected = selected.intersect(present)
    val chosen = remember(items, selected) { items.filter { it.id in selected } }

    if (selected.isNotEmpty()) {
        BackHandler { selected = emptySet() }
    }

    LaunchedEffect(level) {
        val id = anchorId ?: return@LaunchedEffect
        anchorId = null
        val index = rows.rowOf(id)
        // One row up so the heading above the anchor stays on screen; landing
        // exactly on the photo row makes it look like the heading was lost.
        if (index >= 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pinchZoom(
                // Without this the callbacks below keep answering with the level
                // the grid had when it was first drawn.
                key = level,
                onStart = {
                    anchorId = currentRows.firstItemFrom(listState.firstVisibleItemIndex)?.id
                },
                onZoom = { zoom ->
                    scope.launch { scale.snapTo(zoom.coerceIn(MIN_LIVE_SCALE, MAX_LIVE_SCALE)) }
                },
                onEnd = { zoom ->
                    val next = level.stepBy(zoomSteps(zoom))
                    if (next != level) levelOrdinal = next.ordinal else anchorId = null
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                },
            ),
    ) {
        val density = LocalDensity.current
        val tileSize = (maxWidth - tileGap * (level.columns - 1)) / level.columns
        val bucket = with(density) { thumbnailBucketPx(tileSize.roundToPx()) }
        val tilePitchPx = with(density) { (tileSize + tileGap).toPx() }

        // Which photo is under a finger, worked out from the list's own layout
        // rather than by hit-testing tiles: dragging across a selection has to
        // name every tile the finger passes over, not only the one it began on.
        val itemUnder: (Offset) -> MediaItem? = { offset ->
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { offset.y >= it.offset && offset.y < it.offset + it.size }
                ?.let { info ->
                    (currentRows.getOrNull(info.index) as? TimelineRow.Photos)?.items?.getOrNull(
                        (offset.x / tilePitchPx).toInt().coerceIn(0, level.columns - 1)
                    )
                }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(tileGap),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Scaling the whole list follows the fingers exactly, which
                    // no amount of re-laying-out mid-gesture could keep up with.
                    scaleX = scale.value
                    scaleY = scale.value
                    transformOrigin = TransformOrigin(0.5f, 0.35f)
                }
                // A long press starts a selection and the same gesture extends
                // it, so picking a run of photos is one movement instead of
                // forty taps. Scrolling is untouched: nothing starts here until
                // the finger has been still long enough.
                .pointerInput(level, items) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            itemUnder(offset)?.let { selected = selected + it.id }
                        },
                        onDrag = { change, _ ->
                            itemUnder(change.position)?.let { selected = selected + it.id }
                        },
                    )
                },
        ) {
            rows.forEachIndexed { rowIndex, row ->
                when (row) {
                    is TimelineRow.Header -> stickyHeader(key = row.key, contentType = "header") {
                        val sectionIds = remember(rows, rowIndex) {
                            rows.sectionItems(rowIndex).map { it.id }
                        }
                        SectionHeader(
                            row = row,
                            locale = locale,
                            today = today,
                            relativeDates = settings.relativeDates,
                            selecting = selected.isNotEmpty(),
                            allSelected = sectionIds.isNotEmpty() && selected.containsAll(sectionIds),
                            onToggleSection = {
                                selected = if (selected.containsAll(sectionIds)) {
                                    selected - sectionIds.toSet()
                                } else {
                                    selected + sectionIds
                                }
                            },
                        )
                    }

                    TimelineRow.Search -> item(key = row.key, contentType = "search") {
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = 4.dp,
                            ),
                        )
                    }

                    is TimelineRow.Photos -> item(key = row.key, contentType = "photos") {
                        PhotoRow(
                            row = row,
                            columns = level.columns,
                            tileSize = tileSize,
                            gap = tileGap,
                            corner = tileCorner,
                            badges = settings.tileBadges,
                            selectedIds = selected,
                            selecting = selected.isNotEmpty(),
                        ) { item ->
                            if (selected.isEmpty()) {
                                onOpen(item, bucket)
                            } else {
                                selected = if (item.id in selected) {
                                    selected - item.id
                                } else {
                                    selected + item.id
                                }
                            }
                        }
                    }
                }
            }
        }

        // Said plainly rather than left as an empty grid: an empty grid looks
        // like a library with nothing in it, which is a different and much more
        // alarming thing than a search that found nothing.
        if (found.isEmpty() && query.isNotBlank()) {
            Text(
                text = stringResource(R.string.search_nothing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
            )
        }

        FastScroller(
            listState = listState,
            rows = rows,
            locale = locale,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxSize(),
        )

        ScrollToTop(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        )

        if (chosen.isNotEmpty()) {
            SelectionBar(
                count = chosen.size,
                allFavorite = chosen.all { it.isFavorite },
                onClose = { selected = emptySet() },
                onToggleFavorite = {
                    writer.setFavorite(chosen, favorite = !chosen.all { it.isFavorite })
                    selected = emptySet()
                },
                onShare = { context.shareMedia(chosen, shareTitle) },
                onAddToAlbum = { choosingAlbum = true },
                // Both absent for anything not in the library: a file in the
                // vault is in no folder, and a folder is what these choose.
                onMoveToFolder = if (chosen.all { canBeHidden(it) }) {
                    ({ folderErrand = FolderErrand.MOVE })
                } else {
                    null
                },
                onCopyToFolder = if (chosen.all { canBeHidden(it) }) {
                    ({ folderErrand = FolderErrand.COPY })
                } else {
                    null
                },
                onHide = if (chosen.all { canBeHidden(it) }) ({ confirmHide = true }) else null,
                onRemoveFromAlbum = albumActions.onRemoveFrom?.let { remove ->
                    {
                        remove(selected)
                        selected = emptySet()
                    }
                },
                onDelete = {
                    if (chosen.none { canBeHidden(it) }) {
                        // Nothing to trash: these files are not in the library.
                        selected = emptySet()
                    } else if (writer.needsOwnConfirmation) {
                        confirmDelete = true
                    } else {
                        writer.setTrashed(chosen, trashed = true)
                        selected = emptySet()
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (choosingAlbum) {
        ChooseAlbumDialog(
            albums = albumActions.userAlbums,
            onChoose = { albumId ->
                choosingAlbum = false
                albumActions.onAddTo(albumId, selected)
                selected = emptySet()
            },
            onCreateNew = {
                choosingAlbum = false
                namingAlbum = true
            },
            onDismiss = { choosingAlbum = false },
        )
    }

    folderErrand?.let { errand ->
        ChooseFolderDialog(
            title = stringResource(
                if (errand == FolderErrand.MOVE) R.string.folder_move_to else R.string.folder_copy_to
            ),
            folders = albumActions.folders,
            onChoose = { path ->
                folderErrand = null
                when (errand) {
                    FolderErrand.MOVE -> albumActions.onMoveTo(path, chosen)
                    FolderErrand.COPY -> albumActions.onCopyTo(path, chosen)
                }
                selected = emptySet()
            },
            onDismiss = { folderErrand = null },
        )
    }

    if (namingAlbum) {
        TextPromptDialog(
            title = stringResource(R.string.albums_create),
            initial = "",
            confirm = stringResource(R.string.albums_create_confirm),
            onConfirm = { name ->
                namingAlbum = false
                albumActions.onCreateWith(name, selected)
                selected = emptySet()
            },
            onDismiss = { namingAlbum = false },
        )
    }

    if (confirmHide) {
        ConfirmDialog(
            title = stringResource(R.string.vault_hide),
            body = stringResource(R.string.vault_hide_body),
            confirm = stringResource(R.string.vault_hide_confirm),
            onConfirm = {
                confirmHide = false
                onHide(chosen)
                selected = emptySet()
            },
            onDismiss = { confirmHide = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            body = stringResource(
                if (chosen.size == 1) R.string.delete_body else R.string.delete_body_many
            ),
            confirm = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                writer.setTrashed(chosen, trashed = true)
                selected = emptySet()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SectionHeader(
    row: TimelineRow.Header,
    locale: Locale,
    today: LocalDate,
    relativeDates: Boolean,
    selecting: Boolean,
    allSelected: Boolean,
    onToggleSection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Opaque, not translucent: the header slides over photos as the
            // section scrolls under it, and a see-through one turns to mud.
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sectionTitle(row.bucket, row.grouping, locale, today, relativeDates),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = row.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Only while something is selected. A circle sitting on every heading
        // the rest of the time would be a control nobody asked for on a screen
        // whose whole point is the photographs.
        if (selecting) {
            IconButton(onClick = onToggleSection) {
                Icon(
                    painter = painterResource(
                        if (allSelected) R.drawable.ic_circle_check else R.drawable.ic_circle_outline
                    ),
                    contentDescription = stringResource(
                        if (allSelected) R.string.section_deselect else R.string.section_select
                    ),
                    tint = if (allSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun PhotoRow(
    row: TimelineRow.Photos,
    columns: Int,
    tileSize: Dp,
    gap: Dp,
    corner: Dp,
    badges: Boolean,
    selectedIds: Set<Long>,
    selecting: Boolean,
    modifier: Modifier = Modifier,
    onOpen: (MediaItem) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.fillMaxWidth(),
    ) {
        row.items.forEach { item ->
            Thumbnail(
                item = item,
                tileSize = tileSize,
                corner = corner,
                selected = item.id in selectedIds,
                dimmed = selecting && item.id !in selectedIds,
                badges = badges,
                onClick = { onOpen(item) },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
        }
        // A short last row must keep its tiles the same size as every other row,
        // so the missing ones are held open rather than letting the rest stretch.
        repeat(columns - row.items.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Which errand the folder picker is being opened for. */
private enum class FolderErrand { MOVE, COPY }

/**
 * The box at the top of the list.
 *
 * One line, no button to press: the library narrows as the letters arrive, so
 * there is no moment where something has been typed and nothing has happened.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
