package ephyra.feature.reader.viewer.webtoon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.ByteArrayInputStream
import kotlin.math.max

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
     *
     * The sample size is the smallest power of two that keeps the decoded width at or above
     * [targetWidthPx]: rounding to *nearest* (or any non-power-of-two) either upscales a
     * too-small decode (blurry strip) or is silently floored by the decoder, so ceil-to-PoT
     * plus a GPU downscale is the only sharp option region decoding allows.
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
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeForWidth(rect.width(), targetWidthPx)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            runCatching { decoder.decodeRegion(rect, options) }.getOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { decoder?.recycle() }
        }
    }

    /**
     * Decodes every [slices] rect with a single shared [BitmapRegionDecoder]: one header
     * parse and one backing stream for the whole strip instead of N open/parse cycles.
     * Returns `null` when region decoding is unsupported so the caller falls back.
     *
     * Cooperative cancellation: checks [isActive] between slices so a fast fling (1→5)
     * abandons stale page decodes instead of racing them against the visible strip.
     */
    suspend fun decodeSlices(
        bytes: ByteArray,
        srcWidth: Int,
        slices: List<WebtoonSlicer.SliceRect>,
        targetWidthPx: Int,
        isActive: () -> Boolean = { true },
    ): List<Bitmap?>? {
        if (bytes.isEmpty() || srcWidth <= 0 || targetWidthPx <= 0 || slices.isEmpty()) return null
        val sampleSize = sampleSizeForWidth(srcWidth, targetWidthPx)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var decoder: BitmapRegionDecoder? = null
        return try {
            decoder = ByteArrayInputStream(bytes).use { input ->
                runCatching { BitmapRegionDecoder.newInstance(input, false) }.getOrNull()
            } ?: return null
            val activeDecoder = decoder ?: return null
            slices.map { slice ->
                if (!isActive()) return null
                val rect = Rect(0, slice.top, srcWidth, slice.bottom)
                if (rect.height() <= 0 || rect.width() <= 0) {
                    null
                } else {
                    runCatching { activeDecoder.decodeRegion(rect, options) }.getOrNull()
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { decoder?.recycle() }
        }
    }

    /**
     * Largest power-of-two divisor that keeps `srcWidth / sample >= targetWidthPx`
     * (minimum 1). Pure math extracted for JVM unit tests.
     */
    fun sampleSizeForWidth(srcWidth: Int, targetWidthPx: Int): Int {
        if (srcWidth <= 0 || targetWidthPx <= 0) return 1
        var sampleSize = 1
        while (srcWidth / (sampleSize * 2) >= targetWidthPx) sampleSize *= 2
        return max(1, sampleSize)
    }
}
