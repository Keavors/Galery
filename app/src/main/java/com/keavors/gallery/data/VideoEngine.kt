package com.keavors.gallery.data

import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * What the viewer needs of a video, whichever thing is playing it.
 *
 * There are two, and there have to be. ExoPlayer is what the app is built
 * around — it does picture-in-picture, it hands frames to the trimmer, it is
 * what Android itself uses — but it can only open containers Media3 knows how to
 * take apart, and Media3 has never known RealMedia or the ASF that WMV lives in.
 * Files in those formats are not rare on a phone that has been fed downloads for
 * fifteen years, and until now they showed a black screen with no explanation.
 *
 * So the second engine is VLC, which opens everything, and this interface is the
 * seam between them. It carries exactly what the controls and the viewer ask of
 * a video and nothing else, so that neither of them has to know which engine it
 * is talking to.
 */
interface VideoEngine {
    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long

    /** Frames per second, or null when the file does not say. */
    val frameRate: Float?

    /** 0 for silence, 1 for the file's own level. */
    var volume: Float

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(multiplier: Float)

    /** Starts a file. [startAtMs] is where to begin, 0 for the beginning. */
    fun open(uri: String, playWhenReady: Boolean, repeat: Boolean, startAtMs: Long)

    /** Stops holding the decoder. The engine is not usable afterwards. */
    fun release()
}

/**
 * The ordinary engine: hardware decoders, and the software ones behind them.
 *
 * A thin cover over ExoPlayer rather than a reimplementation — everything here
 * is one line, and the point of it is only that the caller does not have to know
 * whether it is holding this or the other one.
 */
@UnstableApi
class ExoVideoEngine(val player: ExoPlayer) : VideoEngine {

    override val isPlaying: Boolean get() = player.isPlaying
    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)
    override val durationMs: Long get() = player.duration.takeIf { it > 0 } ?: 0
    override val frameRate: Float? get() = player.videoFormat?.frameRate?.takeIf { it > 1f }

    override var volume: Float
        get() = player.volume
        set(value) { player.volume = value }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun setSpeed(multiplier: Float) = player.setPlaybackSpeed(multiplier)

    override fun open(uri: String, playWhenReady: Boolean, repeat: Boolean, startAtMs: Long) {
        player.setMediaItem(Media3Item.fromUri(uri))
        player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.playWhenReady = playWhenReady
        player.prepare()
        // After prepare rather than before: a seek on a player that has not been
        // given a file yet is a seek into nothing.
        if (startAtMs > 0) player.seekTo(startAtMs)
    }

    override fun release() = player.release()
}
