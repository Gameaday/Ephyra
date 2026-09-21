package ephyra.feature.reader.viewer.webtoon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes vertical slices of a webtoon strip at full screen-width resolution.
 *
 * Each slice is small enough to stay under the GPU texture ceiling, so — unlike a single
 * full-strip Coil request (which forces a global `inSampleSize` downscale) — text stays sharp.
 * Callers share the page bytes they already buffered; no extra network fetch per slice.
 */
object WebtoonRegionDecoder {

    /**
     * Decodes [slice] out of [bytes], subsampled so the result is ~[targetWidthPx] wide.
     * Returns `null` when region decoding is unsupported (progressive JPEG, JXL, HEIF, …)
     * so the caller can fall back to single-image rendering.
     */
    fun decodeSlice(
        bytes: ByteArray,
        srcWidth: Int,
        slice: WebtoonSlicer.SliceRect,
        targetWidthPx: Int,
    ): Bitmap? {
        if (bytes.isEmpty() || srcWidth <= 0 || targetWidthPx <= 0) return null
        val rect = Rect(0, slice.top, srcWidth, slice.bottom)
        if (rect.height() <= 0 || rect.width() <= 0) return null
        var decoder: BitmapRegionDecoder? = null
        return try {
            decoder = ByteArrayInputStream(bytes).use { input ->
                runCatching { BitmapRegionDecoder.newInstance(input, false) }.getOrNull()
            } ?: return null
            val sampleSize = max(1, (rect.width().toFloat() / targetWidthPx).roundToInt())
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            runCatching { decoder.decodeRegion(rect, options) }.getOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { decoder?.recycle() }
        }
    }
}
