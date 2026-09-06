package com.keavors.gallery.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * The player every screen here uses.
 *
 * One place, because the interesting part is the same everywhere: the phone's
 * own decoders first, and a set of software ones behind them for the files it
 * has no decoder for. Android guarantees H.264 and AAC and little else; a
 * gallery that inherits fifteen years of downloads meets AC3 and DTS soundtracks
 * in MKVs, DivX in AVIs, and files that played once on a computer and have never
 * played on a phone. Those are what the extra renderers are for.
 *
 * Hardware first rather than software first, deliberately: the phone's decoders
 * are faster and cheaper on the battery, and the software ones are there to
 * answer the cases where there is otherwise nothing at all. Falling back is
 * allowed as well, so a hardware decoder that refuses a file — which happens —
 * hands over instead of failing the playback.
 */
@UnstableApi
fun Context.mediaPlayer(): ExoPlayer = ExoPlayer.Builder(this)
    .setRenderersFactory(
        NextRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
    )
    .build()
