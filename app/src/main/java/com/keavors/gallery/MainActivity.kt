package com.keavors.gallery

import android.app.LocaleManager
import android.app.PictureInPictureParams
import android.util.Rational
import android.content.Intent
import android.os.LocaleList
import android.graphics.Color
import androidx.core.graphics.drawable.toDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keavors.gallery.data.GallerySettings
import androidx.core.net.toUri
import com.keavors.gallery.data.DEFAULT_THUMB_BUCKET
import com.keavors.gallery.data.contentUri
import com.keavors.gallery.data.provisionalItem
import com.keavors.gallery.data.startPreview
import com.keavors.gallery.ui.ExternalOpen
import com.keavors.gallery.ui.GalleryApp
import com.keavors.gallery.ui.LaunchMode
import com.keavors.gallery.ui.PipHolder
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

    /** What the viewer would keep playing in a corner, and whether it may. */
    private val pip = PipHolder()
    private var pipAllowed = false

    /** True while the app is that corner. Everything but the video hides. */
    private var inPip by mutableStateOf(false)

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
            window.setBackgroundDrawable(Color.BLACK.toDrawable())
        }

        val app = application as GalleryApplication
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(GallerySettings())

            // Android 13 keeps a per-app language of its own, which also puts the
            // gallery in the system language list. Setting it there rather than
            // swapping resources ourselves means the choice survives a restart
            // and shows up where people look for it.
            LaunchedEffect(settings.language) {
                val manager = getSystemService(LocaleManager::class.java)
                val wanted = if (settings.language.isBlank()) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(settings.language)
                }
                if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
            }

            // Not FLAG_SECURE: that would also stop screenshots being taken
            // of the gallery, which is a different wish entirely.
            LaunchedEffect(settings.pictureInPicture) {
                pipAllowed = settings.pictureInPicture
            }

            LaunchedEffect(settings.hideInRecents) {
                setRecentsScreenshotEnabled(!settings.hideInRecents)
            }

            GalleryTheme(
                themeMode = settings.themeMode,
                palette = settings.palette,
                pureBlack = settings.pureBlack,
                accent = settings.accent,
                fontScale = settings.fontScale,
            ) {
                GalleryApp(
                    settings = settings,
                    settingsStore = app.settings,
                    repository = app.media,
                    albumStore = app.albums,
                    vaultStore = app.vault,
                    watchStore = app.watched,
                    launchMode = launchMode,
                    pip = pip,
                    inPip = inPip,
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

    /**
     * The one moment Android gives an app to say "carry on in the corner".
     *
     * Only for a video, only when it has been asked for in the settings, and
     * only with a shape the system will accept: too tall or too wide and it
     * refuses the whole request rather than trimming it.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val video = pip.video ?: return
        if (!pipAllowed) return

        val ratio = Rational(
            video.width.coerceAtLeast(1),
            video.height.coerceAtLeast(1),
        ).takeIf { video.width > 0 && video.height > 0 && it.toFloat() in 0.42f..2.39f }
            ?: Rational(16, 9)

        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        inPip = isInPictureInPictureMode
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

                // The picture is started here rather than where it is shown,
                // because this is the earliest instant the app knows which
                // photograph is wanted — before the screen that will show it has
                // been composed, laid out or drawn. Reading a thumbnail takes
                // longer than all three, so the head start is the whole
                // difference between the photo arriving with the interface and
                // arriving after it.
                uri?.let {
                    startPreview(provisionalItem(it, intent.type), DEFAULT_THUMB_BUCKET)
                }
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
