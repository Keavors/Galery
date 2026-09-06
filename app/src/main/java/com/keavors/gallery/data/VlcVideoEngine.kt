package com.keavors.gallery.data

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

private const val TAG = "VlcVideoEngine"

/**
 * How VLC is asked to behave.
 *
 * Frames are never dropped or skipped: this plays files off local storage, where
 * there is no network to fall behind, and dropping frames to keep a clock that
 * is not under pressure only makes the picture worse. Everything else is left at
 * its default — VLC's defaults are the result of twenty years of meeting broken
 * files, and second-guessing them from here would be arrogance.
 */
private val VLC_OPTIONS = arrayListOf("--no-drop-late-frames", "--no-skip-frames")

/**
 * The engine for the files nothing else will open.
 *
 * VLC brings its own demuxers, so it takes apart the containers Media3 has never
 * known — RealMedia, the ASF that WMV lives in, Flash — and its own decoders for
 * what is inside them. It is not the ordinary player here and should not be: it
 * carries twenty megabytes of native code, it draws into a surface of its own
 * rather than into the one the rest of the viewer uses, and it knows nothing
 * about picture-in-picture. It is what happens when the alternative is a black
 * rectangle.
 *
 * The file is handed over as an open descriptor rather than as a uri. A uri
 * would make VLC ask Android for permission it was never granted; the
 * descriptor is one this app already holds, and it seeks like a file because it
 * is one.
 */
class VlcVideoEngine(context: Context) : VideoEngine {

    private val libVlc = LibVLC(context.applicationContext, VLC_OPTIONS)
    private val player = MediaPlayer(libVlc)
    private val resolver = context.applicationContext.contentResolver

    private var descriptor: ParcelFileDescriptor? = null
    private var repeating = false
    private var pauseOnFirstFrame = false
    private var attachedTo: VLCVideoLayout? = null

    init {
        player.setEventListener { event ->
            when (event.type) {
                // VLC has no repeat of its own in this generation, so the end of
                // the file is caught and the file started again by hand.
                MediaPlayer.Event.EndReached -> if (repeating) {
                    player.time = 0
                    player.play()
                }

                // A video opened with autoplay off still has to show something,
                // and VLC decodes nothing at all until it is told to play — so
                // it is told to, and stopped again the moment a picture exists.
                // ExoPlayer does this by itself; this is the price of the second
                // engine, and it is one line.
                MediaPlayer.Event.Vout -> if (pauseOnFirstFrame && event.voutCount > 0) {
                    pauseOnFirstFrame = false
                    player.pause()
                }
            }
        }
    }

    override val isPlaying: Boolean get() = runCatching { player.isPlaying }.getOrDefault(false)
    override val positionMs: Long get() = runCatching { player.time }.getOrDefault(0L).coerceAtLeast(0)
    override val durationMs: Long get() = runCatching { player.length }.getOrDefault(0L).coerceAtLeast(0)

    /**
     * Not asked for.
     *
     * VLC knows the rate but only once the file is open and only through the
     * track it is currently decoding, and the one thing that wants it — the
     * frame step — has a sensible answer without it. A step a frame or two out
     * on an unusual file is not a thing anybody can see.
     */
    override val frameRate: Float? get() = null

    override var volume: Float
        get() = runCatching { player.volume / 100f }.getOrDefault(1f)
        set(value) { runCatching { player.volume = (value * 100).toInt().coerceIn(0, 100) } }

    override fun play() { runCatching { player.play() } }
    override fun pause() { runCatching { player.pause() } }
    override fun seekTo(positionMs: Long) { runCatching { player.time = positionMs } }
    override fun setSpeed(multiplier: Float) { runCatching { player.rate = multiplier } }

    override fun open(uri: String, playWhenReady: Boolean, repeat: Boolean, startAtMs: Long) {
        repeating = repeat
        runCatching {
            closeDescriptor()
            val opened = resolver.openFileDescriptor(uri.toUri(), "r")
                ?: return@runCatching
            descriptor = opened

            // The player takes a reference of its own, so this one is given
            // back straight away: VLC counts them, and a media object nobody
            // releases is a file nobody closes.
            val media = Media(libVlc, opened.fileDescriptor)
            player.media = media
            media.release()
            if (startAtMs > 0) player.time = startAtMs
            // Always started, and stopped again at the first frame when nobody
            // asked for it to play. See the listener above.
            pauseOnFirstFrame = !playWhenReady
            player.play()
        }.onFailure { Log.w(TAG, "could not open $uri", it) }
    }

    /**
     * Gives VLC somewhere to draw.
     *
     * Called by the page that shows the video, which recomposes for its own
     * reasons — so attaching the same surface twice has to cost nothing rather
     * than tearing the picture down and building it again.
     */
    fun attach(layout: VLCVideoLayout) {
        if (attachedTo === layout) return
        runCatching {
            player.attachViews(layout, null, false, false)
            attachedTo = layout
        }.onFailure { Log.w(TAG, "could not attach the video surface", it) }
    }

    fun detach() {
        runCatching { player.detachViews() }
        attachedTo = null
    }

    override fun release() {
        runCatching {
            player.stop()
            player.detachViews()
            attachedTo = null
            player.release()
            libVlc.release()
        }.onFailure { Log.w(TAG, "could not let go of the player", it) }
        closeDescriptor()
    }

    private fun closeDescriptor() {
        runCatching { descriptor?.close() }
        descriptor = null
    }
}
