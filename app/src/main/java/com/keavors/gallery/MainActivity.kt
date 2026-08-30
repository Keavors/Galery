package com.keavors.gallery

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.keavors.gallery.data.contentUri
import com.keavors.gallery.ui.ExternalOpen
import com.keavors.gallery.ui.GalleryApp
import com.keavors.gallery.ui.LaunchMode
import com.keavors.gallery.ui.theme.GalleryTheme

/**
 * The only activity in the app.
 *
 * Declared singleTask so a photo opened from another app lands in the running
 * instance rather than starting a second gallery. That is also why the intent is
 * read in two places: onCreate for a cold start, onNewIntent for every time
 * afterwards.
 */
class MainActivity : ComponentActivity() {

    /** A photo another app asked to open, until the UI has taken it. */
    private var pendingOpen by mutableStateOf<ExternalOpen?>(null)

    /** Whether this run is browsing or handing a photo back to another app. */
    private var launchMode by mutableStateOf(LaunchMode.BROWSE)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge from the very first frame: the viewer relies on the
        // content never being re-laid-out when the system bars come and go.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        readIntent(intent)

        // Before any frame is drawn. The window is otherwise transparent, and on
        // the way to a photo from another app that shows through to whatever
        // sent it — a flash of the wrong screen either way.
        if (pendingOpen != null) {
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }

        val app = application as GalleryApplication
        setContent {
            GalleryTheme {
                GalleryApp(
                    repository = app.media,
                    albumStore = app.albums,
                    launchMode = launchMode,
                    pendingOpen = pendingOpen,
                    onExternalHandled = { pendingOpen = null },
                    onPicked = { item ->
                        // Whoever asked has no standing right to the file, so the
                        // permission travels back with the answer.
                        setResult(
                            RESULT_OK,
                            Intent()
                                .setData(item.contentUri())
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                        finish()
                    },
                    onFinish = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                launchMode = LaunchMode.BROWSE
                val uri = intent.data ?: intent.dataString?.toUri()
                pendingOpen = uri?.let { ExternalOpen(it, intent.type) }
            }

            Intent.ACTION_PICK, Intent.ACTION_GET_CONTENT -> {
                launchMode = LaunchMode.PICK
                pendingOpen = null
            }

            else -> {
                launchMode = LaunchMode.BROWSE
                pendingOpen = null
            }
        }
    }
}
