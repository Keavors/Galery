package com.keavors.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.keavors.gallery.ui.GalleryApp
import com.keavors.gallery.ui.theme.GalleryTheme

/**
 * The only activity in the app.
 *
 * It is declared singleTask so that a photo opened from another app lands in the
 * running instance rather than spawning a second copy of the gallery; the intent
 * that carries it will be handled in onNewIntent once the viewer exists.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge from the very first frame: the viewer relies on the
        // content never being re-laid-out when the system bars come and go.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val media = (application as GalleryApplication).media
        setContent {
            GalleryTheme {
                GalleryApp(media)
            }
        }
    }
}
