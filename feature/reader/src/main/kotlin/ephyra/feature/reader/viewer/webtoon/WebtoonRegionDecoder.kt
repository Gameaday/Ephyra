package ephyra.feature.reader.viewer.webtoon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import ephyra.core.common.util.system.logcat
import ephyra.domain.reader.media.PixelRect
import ephyra.domain.reader.media.SampleSize
import logcat.LogPriority
import java.io.ByteArrayInputStream
import kotlin.math.max

/**
 * Decodes vertical slices of a webtoon strip at full screen-width resolution.
 *
 * Each slice is small enough to stay under the GPU texture ceiling, so — unlike a single
 * full-strip Coil request (which forces a global `inSampleSize` downscale) — text stays sharp.
 * Callers share the page bytes they already buffered; no extra network fetch per slice.
 *
 * A1: the sample size and the rectangle to slice are decisions made upstream by
 * [WebtoonSlicePlanner] (width fit plus hysteresis, crop-aware rect). This object only
 * executes them against BitmapRegionDecoder — it no longer derives a sample of its own, so
 * the policy and the decoder cannot disagree about how finely a strip is fetched.
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
        } catch (e: Throwable) {
            logcat(LogPriority.DEBUG, e) { "Failed to decode region" }
            null
        } finally {
            runCatching { decoder?.recycle() }
        }
    }

    /**
     * Decodes every [slices] rect of [sliceRect] with a single shared [BitmapRegionDecoder]:
     * one header parse and one backing stream for the whole strip instead of N open/parse
     * cycles. Returns `null` when region decoding is unsupported so the caller falls back.
     *
     * [slices] are local to [sliceRect] (they partition it exactly); each is offset by the
     * rect's origin before decoding, which is what makes a cropped page slice from its
     * artwork rather than from raw image coordinates.
     *
     * [sampleSize] comes from the caller's plan: [WebtoonSlicePlanner] owns the choice, so the
     * width-fit rule and its hysteresis live in one testable place.
     *
     * Cooperative cancellation: checks [isActive] between slices so a fast fling (1→5)
     * abandons stale page decodes instead of racing them against the visible strip.
     */
    suspend fun decodeSlices(
        bytes: ByteArray,
        sliceRect: PixelRect,
        slices: List<WebtoonSlicer.SliceRect>,
        sampleSize: SampleSize,
        isActive: () -> Boolean = { true },
    ): List<Bitmap?>? {
        if (bytes.isEmpty() || slices.isEmpty()) return null
        if (sliceRect.width <= 0 || sliceRect.height <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.inSampleSize
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
                val rect = Rect(
                    sliceRect.left,
                    sliceRect.top + slice.top,
                    sliceRect.right,
                    sliceRect.top + slice.bottom,
                )
                if (rect.height() <= 0 || rect.width() <= 0) {
                    null
                } else {
                    runCatching { activeDecoder.decodeRegion(rect, options) }.getOrNull()
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.DEBUG, e) { "Failed to decode region" }
            null
        } finally {
            runCatching { decoder?.recycle() }
        }
    }

    /**
     * Largest power-of-two divisor that keeps `srcWidth / sample >= targetWidthPx`
     * (minimum 1). Pure math extracted for JVM unit tests. Consumed by [WebtoonSlicePlanner]
     * as the width-fit rule its hysteresis refines within.
     */
    fun sampleSizeForWidth(srcWidth: Int, targetWidthPx: Int): Int {
        if (srcWidth <= 0 || targetWidthPx <= 0) return 1
        var sampleSize = 1
        while (srcWidth / (sampleSize * 2) >= targetWidthPx) sampleSize *= 2
        return max(1, sampleSize)
    }
}
