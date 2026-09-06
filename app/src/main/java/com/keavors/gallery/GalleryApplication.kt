package com.keavors.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.size.Precision
import com.keavors.gallery.data.AlbumStore
import com.keavors.gallery.data.GallerySettings
import com.keavors.gallery.data.MediaRepository
import com.keavors.gallery.data.SettingsStore
import com.keavors.gallery.data.VaultStore
import com.keavors.gallery.data.WatchStore
import coil3.gif.AnimatedImageDecoder
import coil3.video.VideoFrameDecoder
import com.keavors.gallery.data.MediaThumbnailFetcher
import com.keavors.gallery.data.RawPreviewDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds the objects that outlive a screen. One module means one container and no
 * dependency-injection framework: adding one would generate more code than it
 * would save.
 */
class GalleryApplication : Application(), SingletonImageLoader.Factory {

    /** Lives as long as the process; the library index outlives every screen. */
    private val appScope = CoroutineScope(SupervisorJob())

    lateinit var media: MediaRepository
        private set

    /** Pinned, hidden, chosen covers and albums that exist nowhere else. */
    lateinit var albums: AlbumStore
        private set

    lateinit var settings: SettingsStore
        private set

    /** Files taken out of the library and kept where nothing else can see them. */
    lateinit var vault: VaultStore
        private set

    /** Where each video was left off. */
    lateinit var watched: WatchStore
        private set

    /**
     * How large the thumbnail cache on disk may grow, in bytes.
     *
     * Kept as a plain number rather than read from the settings when the image
     * loader is built: the loader is built the first time anything asks for a
     * picture, and making that moment wait on a file read would be paid for in
     * the one place the app must never be slow. A change to the setting is
     * therefore honoured from the next launch, which is what the setting says.
     */
    @Volatile
    private var cacheLimitBytes: Long = GallerySettings().cacheLimitMb * BYTES_PER_MB

    override fun onCreate() {
        super.onCreate()
        media = MediaRepository(this, appScope)
        albums = AlbumStore(this)
        settings = SettingsStore(this)
        vault = VaultStore(this)
        watched = WatchStore(this)

        appScope.launch {
            settings.settings.collect { cacheLimitBytes = it.cacheLimitMb * BYTES_PER_MB }
        }
    }

    /**
     * Every grid tile goes through the thumbnail fetcher rather than decoding the
     * original file, which is the difference between a scrolling grid and a
     * stuttering one.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbnailFetcher.Factory(this@GalleryApplication))
                // Raw first, because it is the one Android cannot open at all
                // and the only one worth taking off the platform.
                add(RawPreviewDecoder.Factory())
                // A GIF that does not move is a still of a GIF, and this is also
                // what brings AVIF and animated WebP in: they go through the
                // system's own ImageDecoder rather than through BitmapFactory,
                // which has never heard of either.
                add(AnimatedImageDecoder.Factory())
                // A frame out of a video file, for the videos in the vault: those
                // are files in the app's own storage with no library row behind
                // them, so there is no thumbnail to ask the system for.
                add(VideoFrameDecoder.Factory())
            }
            // Thumbnails are bucketed to a few sizes, so a cached one is nearly
            // always usable for a tile of a slightly different width. Demanding
            // an exact match would re-decode the library on every zoom step.
            .precision(Precision.INEXACT)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.35)
                    .build()
            }
            // Asked for lazily, and that is the point: the first picture of the
            // run is decided by whatever the setting was when the app started.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(cacheLimitBytes)
                    .build()
            }
            // A grid of six hundred tiles fading in individually looks like
            // noise, and the fade costs a draw pass per tile.
            .crossfade(false)
            .build()
}

/** One megabyte, as the settings screen means it. */
private const val BYTES_PER_MB = 1024L * 1024L
