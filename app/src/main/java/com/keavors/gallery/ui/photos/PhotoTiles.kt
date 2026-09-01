package com.keavors.gallery.ui.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.TileShape
import com.keavors.gallery.data.previewCacheKey
import com.keavors.gallery.data.previewRequest
import com.keavors.gallery.data.standInBuckets
import com.keavors.gallery.data.tileAspect
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * One row of photographs, drawn rather than composed.
 *
 * Every tile used to be its own element: a box with a background, a clip, a
 * click handler and an image loader of its own. At four columns that is sixteen
 * elements on the screen and nobody notices. At twenty-five it is six hundred,
 * and the grid managed seven frames a second.
 *
 * So a row is one canvas now. The pictures are looked up from the image cache
 * and painted straight onto it; the taps are worked out from the arithmetic that
 * placed them. What is lost is a ripple under the finger, which is drawn here by
 * hand, and per-tile accessibility labels, which is a real loss and the price of
 * the grid being usable at all at the zoom levels the specification asks for.
 */
@Composable
fun PhotoRowCanvas(
    items: List<MediaItem>,
    columns: Int,
    shape: TileShape,
    rowWidth: Dp,
    gap: Dp,
    corner: Dp,
    bucketPx: Int,
    selectedIds: Set<Long>,
    selecting: Boolean,
    badges: Boolean,
    tileSize: Dp,
    onOpen: (MediaItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which tile a finger is on, for the moment it is on it. Plain state rather
    // than an indication node per tile: the whole point here is that there are
    // no nodes per tile.
    var pressedId by remember { mutableStateOf<Long?>(null) }

    // Where the row is on the screen, so a tap can say which rectangle the
    // viewer should grow out of. Written on every placement, read on a tap.
    val rowOrigin = remember { RowOrigin() }
    val density = LocalDensity.current
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHigh
    val chosen = MaterialTheme.colorScheme.primary

    val rects = remember(items, columns, shape, rowWidth, gap, density) {
        with(density) {
            rowTileRects(
                count = items.size,
                columns = columns,
                widthPx = rowWidth.toPx(),
                gapPx = gap.toPx(),
                aspects = if (shape == TileShape.MOSAIC) items.map { it.tileAspect() } else null,
            )
        }
    }
    val height = remember(rects) { with(density) { rowHeight(rects).toDp() } }
    val cornerPx = remember(corner, density) { with(density) { corner.toPx() } }
    val rounded = corner > 0.dp && height >= CLIP_MIN_TILE

    val bitmaps = rememberRowBitmaps(items, bucketPx)

    // The marks are composed rather than drawn, and only where they can be read:
    // four across a screen at most. Beyond that they would be smudges, and there
    // are six hundred of them.
    val showBadges = badges && tileSize >= BADGE_MIN_TILE

    Box(modifier = modifier.fillMaxWidth().height(height)) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .onPlaced { rowOrigin.at = it.positionInWindow() }
            // One gesture handler for a row of twenty-five instead of
            // twenty-five handlers. Which tile was touched is arithmetic, and
            // the arithmetic is the same one that placed them.
            .pointerInput(rects, items) {
                detectTapGestures(
                    onPress = { offset ->
                        val index = tileAt(rects, offset.x, offset.y)
                        pressedId = items.getOrNull(index)?.id
                        tryAwaitRelease()
                        pressedId = null
                    },
                    onTap = { offset ->
                        val index = tileAt(rects, offset.x, offset.y)
                        val item = items.getOrNull(index) ?: return@detectTapGestures
                        val rect = rects[index].translate(rowOrigin.at)
                        onOpen(item, rect)
                    },
                )
            },
    ) {
        items.forEachIndexed { index, item ->
            val rect = rects.getOrNull(index) ?: return@forEachIndexed
            val bitmap = bitmaps.value[item.id]

            translate(rect.left, rect.top) {
                val tile = Size(rect.width, rect.height)
                if (rounded) {
                    clipPath(roundedPath(tile, cornerPx)) { paintTile(bitmap, tile, placeholder) }
                } else {
                    paintTile(bitmap, tile, placeholder)
                }

                // Chosen tiles are tinted and the rest dulled, so a selection
                // reads at a glance rather than by hunting for ticks.
                val tint = when {
                    item.id in selectedIds -> chosen.copy(alpha = 0.40f)
                    selecting -> Color.Black.copy(alpha = 0.35f)
                    item.id == pressedId -> Color.Black.copy(alpha = 0.22f)
                    else -> null
                }
                tint?.let { drawRect(color = it, size = tile) }
            }
        }
    }

    if (showBadges) {
        items.forEachIndexed { index, item ->
            val rect = rects.getOrNull(index) ?: return@forEachIndexed
            TileBadges(
                item = item,
                modifier = Modifier
                    .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
                    .size(
                        width = with(density) { rect.width.toDp() },
                        height = with(density) { rect.height.toDp() },
                    ),
            )
        }
    }
    }
}

/** Paints one tile: the picture if there is one, and the waiting tone if not. */
private fun DrawScope.paintTile(bitmap: ImageBitmap?, tile: Size, placeholder: Color) {
    if (bitmap == null) {
        drawRect(color = placeholder, size = tile)
        return
    }
    val (srcOffset, srcSize) = centreCrop(bitmap.width, bitmap.height, tile)
    drawImage(
        image = bitmap,
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(tile.width.toInt().coerceAtLeast(1), tile.height.toInt().coerceAtLeast(1)),
    )
}

private fun roundedPath(tile: Size, cornerPx: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect = Rect(Offset.Zero, tile),
            radiusX = cornerPx,
            radiusY = cornerPx,
        )
    )
}

/**
 * The pictures a row draws, by photograph id.
 *
 * Held in a map that only the drawing reads, so a picture arriving repaints the
 * row without recomposing anything: at six hundred tiles a screen, a
 * recomposition per arriving thumbnail is the difference between a grid and a
 * slideshow.
 *
 * What is already in memory is taken at once, and a bigger copy of the same
 * photograph counts — a thumbnail cached for four columns is a perfectly good
 * picture for a tile a quarter of the size, and reusing it is what makes zooming
 * out instant instead of five thousand new requests to the media store.
 */
@Composable
private fun rememberRowBitmaps(items: List<MediaItem>, bucketPx: Int): State<Map<Long, ImageBitmap>> {
    val context = LocalContext.current
    val loader = remember(context) { SingletonImageLoader.get(context) }
    val found = remember { mutableStateMapOf<Long, ImageBitmap>() }

    LaunchedEffect(items, bucketPx) {
        // A row's slot in the list is reused for other rows as the grid scrolls,
        // and the pictures it held would otherwise pile up in here for as long
        // as the slot lives. Only what this row draws is kept.
        found.keys.retainAll(items.mapTo(HashSet(items.size)) { it.id })

        // The lookup of what is already in memory happens away from the main
        // thread — twenty-five entries per row, on every row that scrolls in.
        val missing = withContext(Dispatchers.Default) {
            val absent = ArrayList<MediaItem>(items.size)
            val ready = ArrayList<Pair<Long, ImageBitmap>>(items.size)
            for (item in items) {
                val cached = loader.cachedTile(item, bucketPx)
                if (cached != null) ready += item.id to cached else absent += item
            }
            found.putAll(ready)
            absent
        }
        if (missing.isEmpty()) return@LaunchedEffect

        // Arrivals are filed once a frame rather than one at a time.
        //
        // Six loads in flight against storage this quick means a thumbnail every
        // millisecond or two, and every one of them told the screen to redraw.
        // Coalescing them costs nothing — a frame is the soonest anybody could
        // see the difference anyway — and it turns hundreds of interruptions a
        // second into at most one per frame.
        val arrived = Channel<Pair<Long, ImageBitmap>>(Channel.UNLIMITED)
        launch {
            while (true) {
                val first = arrived.receive()
                val batch = ArrayList<Pair<Long, ImageBitmap>>(16)
                batch += first
                while (true) batch += arrived.tryReceive().getOrNull() ?: break
                found.putAll(batch)
                withFrameNanos { }
            }
        }

        for (item in missing) {
            launch {
                // A few at a time across the whole grid, and this is the single
                // most important line in the file.
                //
                // Twenty-five columns puts six hundred tiles on the screen. Six
                // hundred requests let go at once means six hundred calls into
                // the media store, six hundred thumbnails decoded and six
                // hundred bitmaps allocated in the time it takes to flick a
                // finger — and the phone spends that time on them rather than on
                // drawing, which is how a grid ends up at seven frames a second
                // while doing nothing visible.
                //
                // Held to a handful, the pictures fill in over a second or two
                // and the scrolling never stops being smooth. A tile that
                // scrolls away before its turn comes takes its request with it.
                tileLoads.withPermit {
                    val result = loader.execute(previewRequest(context, item, bucketPx))
                    (result.image as? BitmapImage)?.let {
                        arrived.send(item.id to it.bitmap.asImageBitmap())
                    }
                }
            }
        }
    }

    return remember(found) { derivedStateOf { found } }
}

/** A picture of this photograph already in memory, at this size or a larger one. */
private fun ImageLoader.cachedTile(item: MediaItem, bucketPx: Int): ImageBitmap? {
    val cache = memoryCache ?: return null
    for (size in standInBuckets(bucketPx)) {
        val image = cache[MemoryCache.Key(previewCacheKey(item, size))]?.image
        if (image is BitmapImage) return image.bitmap.asImageBitmap()
    }
    return null
}

/**
 * How many thumbnails the grid reads at once, across every row on the screen.
 *
 * Six, which is more than the storage will answer in parallel anyway and few
 * enough that the work never crowds out the drawing.
 */
private val tileLoads = Semaphore(3)

/** Somewhere to keep a position that is written constantly and read on a tap. */
private class RowOrigin {
    var at: Offset = Offset.Zero
}
