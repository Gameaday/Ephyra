package ephyra.feature.reader.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Width of the sampled border band, in pixels.
 *
 * Only the outermost few pixels of a page are read when deriving the "automatic" reader
 * background. A full-bitmap palette extraction walks every pixel of every page and causes
 * dropped frames while scrolling, whereas a 1-2px perimeter walk is a tiny, predictable
 * amount of work that can safely run off the main thread.
 */
internal const val EDGE_BAND_PX = 2

/**
 * Longest edge a page is downscaled to before its perimeter is sampled.
 *
 * Sampling never needs full resolution, so decoding is requested at a reduced size. This
 * keeps the cost of deriving a background colour independent of the page's native size.
 */
internal const val EDGE_SAMPLE_TARGET_DIMENSION = 256

/**
 * Raw ARGB pixels are not premultiplied, so the derived colour is forced fully opaque by
 * or-ing in a fixed alpha byte.
 */
private const val OPAQUE_ALPHA = 0xFF000000.toInt()

/**
 * Decodes [bytes] at a reduced size and returns the arithmetic mean of the page's outer
 * border pixels, or `null` when the payload cannot be decoded.
 *
 * Called from `Dispatchers.Default`: the decode is small but never free, and it must not
 * compete with the main thread's rendering work.
 */
suspend fun extractEdgeColor(bytes: ByteArray, band: Int = EDGE_BAND_PX): Int? =
    withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, EDGE_SAMPLE_TARGET_DIMENSION)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
        bitmap.averageEdgeColor(band)
    }

/**
 * Reads only [Bitmap]'s border band and averages it.
 *
 * The reference is dropped rather than explicitly recycled: a Compose snapshot may still
 * hold the bitmap, and the graphics pipeline reclaims it once unreferenced.
 */
suspend fun Bitmap.averageEdgeColor(band: Int = EDGE_BAND_PX): Int? =
    withContext(Dispatchers.Default) {
        if (isRecycled) return@withContext null
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return@withContext null

        val pixels = IntArray(w * h)
        getPixels(pixels, 0, w, 0, 0, w, h)
        averageEdgeColor(pixels, w, h, band)
    }

/**
 * Averages the perimeter band of a row-major ARGB buffer.
 *
 * Expressed over an [IntArray] rather than a [Bitmap] so the sampling arithmetic is
 * verifiable in a plain JVM unit test.
 *
 * The interior of the page is deliberately ignored: page gutters and margins carry the
 * colour that visually borders the artwork, while a centred subject would dominate a
 * whole-image average and produce an unrelated tint.
 *
 * @param pixels row-major ARGB pixels, at least `width * height` entries.
 * @param band border thickness in pixels; clamped to a sane range.
 * @return an opaque ARGB colour, or `null` for degenerate input.
 */
internal fun averageEdgeColor(pixels: IntArray, width: Int, height: Int, band: Int): Int? {
    if (width <= 0 || height <= 0) return null
    if (pixels.size < width * height) return null

    val b = band.coerceAtLeast(1).coerceAtMost(minOf(width, height))

    // Degenerate thumbnail: the border band would overlap itself, so average the whole
    // buffer instead of double-counting shared rows and columns.
    if (width <= 2 * b || height <= 2 * b) {
        return averageRange(pixels, 0, width * height)
    }

    var r = 0L
    var g = 0L
    var bl = 0L
    var n = 0

    fun accumulate(x: Int, y: Int) {
        val p = pixels[y * width + x]
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        bl += p and 0xFF
        n++
    }

    for (y in 0 until b) {
        for (x in 0 until width) accumulate(x, y)
    }
    for (y in height - b until height) {
        for (x in 0 until width) accumulate(x, y)
    }
    for (y in b until height - b) {
        for (x in 0 until b) accumulate(x, y)
        for (x in width - b until width) accumulate(x, y)
    }

    if (n == 0) return null
    return OPAQUE_ALPHA or
        ((r / n).toInt() shl 16) or
        ((g / n).toInt() shl 8) or
        (bl / n).toInt()
}

private fun averageRange(pixels: IntArray, from: Int, until: Int): Int? {
    if (until <= from) return null
    var r = 0L
    var g = 0L
    var bl = 0L
    for (i in from until until) {
        val p = pixels[i]
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        bl += p and 0xFF
    }
    val n = (until - from).toLong()
    return OPAQUE_ALPHA or
        ((r / n).toInt() shl 16) or
        ((g / n).toInt() shl 8) or
        (bl / n).toInt()
}

/**
 * Largest power-of-two downscale that keeps both dimensions at or above [target].
 *
 * @return a `BitmapFactory.Options.inSampleSize`-compatible value, always `>= 1`.
 */
internal fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= target || height / (sample * 2) >= target) {
        sample *= 2
    }
    return sample
}
