package ephyra.presentation.core.data.coil

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import ephyra.core.common.util.system.ImageUtil

/**
 * A [Decoder.Factory] that enforces the [GL_MAX_TEXTURE_SIZE][ImageUtil.hardwareBitmapThreshold]
 * safety limit on the platform decode path.
 *
 * The global [ImageLoader] requests [Bitmap.Config.HARDWARE] bitmaps, which live in
 * `AHardwareBuffer` / GPU memory and are strictly capped by the device's maximum texture
 * size. Long-strip webtoon pages routinely exceed that cap; without this guard the
 * hardware decode fails and the page renders as an error.
 *
 * The guard peeks the stream header (no pixel decoding, no UI-thread blocking) and, for
 * oversized sources, downgrades only that request to software [Bitmap.Config.ARGB_8888]
 * instead of failing — a targeted fallback that avoids any custom tiled renderer.
 *
 * Registered *after* [TachiyomiImageDecoder.Factory] (which handles JXL and applies the
 * same limit internally) so normal images keep hardware decoding and only oversized
 * sources fall back to software.
 */
class HardwareGuardDecoder private constructor(
    private val delegate: Decoder,
) : Decoder by delegate {

    class Factory(
        private val delegate: Decoder.Factory = BitmapFactoryDecoder.Factory(),
    ) : Decoder.Factory {

        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (options.bitmapConfig != Bitmap.Config.HARDWARE) {
                return delegate.create(result, options, imageLoader)
            }

            val oversized = result.source.sourceOrNull()?.use { source ->
                !ImageUtil.canUseHardwareBitmap(source)
            } ?: false

            val effectiveOptions = if (oversized) {
                options.copy(bitmapConfig = Bitmap.Config.ARGB_8888)
            } else {
                options
            }
            return delegate.create(result, effectiveOptions, imageLoader)
                ?.let(::HardwareGuardDecoder)
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}
