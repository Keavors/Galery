package com.keavors.gallery.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.keavors.gallery.R
import com.keavors.gallery.data.MediaItem
import com.keavors.gallery.data.MediaThumb
import com.keavors.gallery.data.contentUri
import com.keavors.gallery.data.thumbnailBucketPx
import com.keavors.gallery.data.thumbnailCacheKey

/** Below this a tile is too small for a badge to be anything but a smudge. */
private val BADGE_MIN_TILE = 56.dp

/**
 * Below this the corner radius is invisible and the clip is not worth its cost:
 * at twenty-five columns a tile is about sixteen density-independent pixels, and
 * six hundred rounded clips per frame buy nothing anyone can see.
 */
private val CLIP_MIN_TILE = 28.dp

@Composable
fun Thumbnail(
    item: MediaItem,
    tileSize: Dp,
    corner: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dimmed: Boolean = false,
    badges: Boolean = true,
) {
    val showBadges = badges && tileSize >= BADGE_MIN_TILE

    val context = LocalContext.current
    val density = LocalDensity.current
    val bucket = with(density) { thumbnailBucketPx(tileSize.roundToPx()) }

    // Rebuilt only when the photo or the zoom bucket changes. Building a request
    // per recomposition would hand Coil a new object on every scroll frame.
    val request = remember(item.id, item.isVideo, item.isPrivate, bucket) {
        val key = thumbnailCacheKey(item.id, bucket)
        ImageRequest.Builder(context)
            // A vaulted file has no MediaStore row and so no system thumbnail;
            // it is decoded from the file, downsampled to the tile.
            .data(if (item.isPrivate) item.contentUri() else MediaThumb(item.id, item.isVideo))
            .size(bucket)
            .memoryCacheKey(key)
            // Draws the cached bitmap immediately on the way back into view.
            // Without it a tile that is already in memory still blinks grey for
            // a frame while the request goes round the loader again.
            .placeholderMemoryCacheKey(key)
            .build()
    }

    Box(
        modifier = modifier
            .then(if (tileSize >= CLIP_MIN_TILE) Modifier.clip(RoundedCornerShape(corner)) else Modifier)
            // A placeholder tone under every tile: scrolling fast through a
            // thousand photos should look like a grid filling in, not like holes.
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (showBadges && item.isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = formatDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }

        // Chosen tiles are tinted and the rest are dulled, so a selection reads
        // at a glance from across the grid rather than by hunting for ticks.
        if (selected || dimmed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                        } else {
                            Color.Black.copy(alpha = 0.35f)
                        }
                    ),
            )
        }
        if (selected && showBadges) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp),
            )
        }

        if (showBadges && item.isFavorite) {
            Icon(
                painter = painterResource(R.drawable.ic_heart),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .size(13.dp),
            )
        }
    }
}

/** mm:ss, or h:mm:ss once a video runs past an hour. */
internal fun formatDuration(millis: Long): String {
    val total = millis / 1000
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) {
        "$hours:${minutes.pad()}:${seconds.pad()}"
    } else {
        "$minutes:${seconds.pad()}"
    }
}

private fun Long.pad(): String = if (this < 10) "0$this" else toString()
