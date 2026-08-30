package com.keavors.gallery

import android.app.Application
import com.keavors.gallery.data.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the objects that outlive a screen. One module means one container and no
 * dependency-injection framework: adding one would generate more code than it
 * would save.
 */
class GalleryApplication : Application() {

    /** Lives as long as the process; the library index outlives every screen. */
    private val appScope = CoroutineScope(SupervisorJob())

    lateinit var media: MediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        media = MediaRepository(this, appScope)
    }
}
