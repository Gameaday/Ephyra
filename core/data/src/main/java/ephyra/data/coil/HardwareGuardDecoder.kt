package ephyra.data.coil

import android.graphics.Bitmap
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.bitmapConfig
import ephyra.core.common.util.system.ImageUtil

/**
 * A [Decoder.Factory] that enforces the [GL_MAX_TEXTURE_SIZE][ImageUtil.hardwareBitmapThreshold]
 * safety limit on the decode path.
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
 * The [delegate] defaults to the platform [BitmapFactoryDecoder] but any upstream decoder
 * (e.g. the JXL decoder, registered before this guard) can be wrapped the same way so
 * oversized sources of every format fall back to software.
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

            // NB: `sourceOrNull()` returns the shared stream that the downstream decoder
            // will consume, so it must NOT be closed here (closing it breaks every decode
            // — blank covers/pages). Peek a copy for header inspection instead: the peek
            // shares the buffer without consuming it, and closing only the peek leaves
            // the original intact.
            val oversized = result.source.sourceOrNull()?.peek()?.use { peeked ->
                !ImageUtil.canUseHardwareBitmap(peeked)
            } ?: false

            val effectiveOptions = if (oversized) options.toSoftwareDecoding() else options
            return delegate.create(result, effectiveOptions, imageLoader)
                ?.let(::HardwareGuardDecoder)
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}

/**
 * Returns a copy of these [Options] whose preferred [Bitmap.Config] is
 * [Bitmap.Config.ARGB_8888].
 *
 * Coil's [Options] is not a data class and `bitmapConfig` is an [Extras]-backed accessor rather
 * than a constructor parameter, so the override is applied to the extras map instead of via a
 * hypothetical `copy(bitmapConfig = ...)` argument.
 */
private fun Options.toSoftwareDecoding(): Options = copy(
    extras = extras.newBuilder()
        .set(Extras.Key.bitmapConfig, Bitmap.Config.ARGB_8888)
        .build(),
)
