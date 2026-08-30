package com.keavors.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.keavors.gallery.data.MediaRepository
import com.keavors.gallery.data.MediaThumbnailFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

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

    override fun onCreate() {
        super.onCreate()
        media = MediaRepository(this, appScope)
    }

    /**
     * Every grid tile goes through the thumbnail fetcher rather than decoding the
     * original file, which is the difference between a scrolling grid and a
     * stuttering one.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(MediaThumbnailFetcher.Factory(this@GalleryApplication)) }
            .crossfade(false)
            .build()
}
