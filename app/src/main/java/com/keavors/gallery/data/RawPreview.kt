package com.keavors.gallery.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "RawPreview"

/**
 * Opens a raw photograph by handing over the picture inside it.
 *
 * Android cannot decode a raw file: no camera manufacturer's format is in the
 * platform decoders, and a gallery that shows a black rectangle where a DNG
 * should be is a gallery that cannot be used with a camera. Every raw file
 * carries a finished JPEG of itself for exactly this purpose — it is what every
 * other program shows too — so that is what is shown.
 *
 * The specification allowed for this from the start: raw files are displayed
 * through their preview and their metadata, and a converter that develops the
 * sensor data is a different project.
 */
class RawPreviewDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult? = withContext(Dispatchers.IO) {
        // Read through the stream rather than into memory: a raw file is forty
        // megabytes and the picture wanted out of it is one.
        val exif = runCatching { ExifInterface(source.source().inputStream()) }
            .onFailure { Log.w(TAG, "could not read the raw file", it) }
            .getOrNull() ?: return@withContext null

        val preview = exif.thumbnailBytes ?: return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(preview, 0, preview.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val wanted = BitmapFactory.Options().apply {
            inSampleSize = DecodeUtils.calculateInSampleSize(
                srcWidth = bounds.outWidth,
                srcHeight = bounds.outHeight,
                dstWidth = options.size.width.pxOrElse { bounds.outWidth },
                dstHeight = options.size.height.pxOrElse { bounds.outHeight },
                scale = options.scale,
            )
        }
        val bitmap = BitmapFactory.decodeByteArray(preview, 0, preview.size, wanted)
            ?: return@withContext null

        // The preview inside a raw file is stored the way the sensor read it, so
        // the turn belongs to the file rather than to the picture: a photograph
        // taken sideways arrives sideways unless this is applied.
        val turned = when (val degrees = exif.rotationDegrees) {
            0 -> bitmap
            else -> runCatching {
                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    Matrix().apply { postRotate(degrees.toFloat()) },
                    true,
                )
            }.getOrDefault(bitmap)
        }

        DecodeResult(
            image = turned.asImage(),
            // Scaled down to the screen rather than the whole preview, so nothing
            // downstream believes it is looking at the original pixels.
            isSampled = wanted.inSampleSize > 1,
        )
    }

    /**
     * Takes on the files nothing else will.
     *
     * Placed before the platform decoders on purpose, and only for raw files:
     * everything else is left to Android, which is better at it.
     */
    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val name = result.source.file().name
            if (!isRawPhoto(result.mimeType, name)) return null
            return RawPreviewDecoder(result.source, options)
        }
    }
}
