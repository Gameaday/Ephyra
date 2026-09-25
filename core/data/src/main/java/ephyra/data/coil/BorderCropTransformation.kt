package ephyra.data.coil

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.min

/**
 * Removes a small, uniform border from a decoded reader page.
 *
 * Detection is deliberately evidence-based: an edge counts as border only while a sampled
 * majority of its pixels match the corner colour, and a page is trimmed only when something
 * that is not border colour survives inside the proposed bounds. Artwork with noisy or
 * non-uniform edges, and single-colour images, are returned unchanged.
 */
class BorderCropTransformation : Transformation() {
    // Bumped to v2 when per-edge measurement and outlier tolerance changed the resulting pixels.
    // Coil keys its memory cache on this, so a stale value would keep serving bitmaps produced
    // by the previous algorithm after the fix ships.
    override val cacheKey: String = "ephyra-border-crop-v2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val bounds = findUniformBorderBounds(input) ?: return input
        return Bitmap.createBitmap(
            input,
            bounds[0],
            bounds[1],
            bounds[2] - bounds[0],
            bounds[3] - bounds[1],
        )
    }

    companion object {
        private const val COLOR_TOLERANCE = 12
        private const val MIN_BORDER = 2
        private const val MAX_BORDER_RATIO = 0.12f

        /**
         * Fraction of sampled pixels on an edge line that must match the border colour.
         *
         * Requiring a perfect 100% match is not "conservative", it is unusable: a single
         * compression or anti-aliasing artifact anywhere along a full-length edge collapses that
         * border to zero and, because every edge is required, disables cropping for the whole
         * page. Decoded pages are essentially never perfectly clean, so a tolerant majority is
         * both the correct reading of "noisy edges are not borders" and far cheaper.
         */
        private const val EDGE_MATCH_RATIO = 0.9f

        /** Caps per-line sampling so detection cost stays bounded on very large pages. */
        private const val MAX_EDGE_SAMPLES = 64

        /** Step used when probing the surviving interior for actual content. */
        private const val CONTENT_PROBE_STEP = 8

        /** Returns [left, top, right, bottom], or null when no confident border is present. */
        internal fun findUniformBorderBounds(bitmap: Bitmap): IntArray? {
            val width = bitmap.width
            val height = bitmap.height
            if (width < MIN_BORDER * 2 || height < MIN_BORDER * 2) return null

            val maxBorderX = max(MIN_BORDER, (width * MAX_BORDER_RATIO).toInt())
            val maxBorderY = max(MIN_BORDER, (height * MAX_BORDER_RATIO).toInt())
            val border = bitmap.getPixel(0, 0)
            if (!matches(border, bitmap.getPixel(width - 1, 0)) ||
                !matches(border, bitmap.getPixel(0, height - 1)) ||
                !matches(border, bitmap.getPixel(width - 1, height - 1))
            ) {
                return null
            }

            // Each inset is measured along its own axis: left/right walk columns, top/bottom walk
            // rows. Keeping these distinct matters because page borders are frequently of
            // different thickness per edge.
            val left = uniformBorderLength(bitmap, alongColumn = true, fromStart = true, max = maxBorderX, border)
            val right = uniformBorderLength(bitmap, alongColumn = true, fromStart = false, max = maxBorderX, border)
            val top = uniformBorderLength(bitmap, alongColumn = false, fromStart = true, max = maxBorderY, border)
            val bottom = uniformBorderLength(bitmap, alongColumn = false, fromStart = false, max = maxBorderY, border)
            if (left < MIN_BORDER || right < MIN_BORDER || top < MIN_BORDER || bottom < MIN_BORDER) return null

            val bounds = intArrayOf(left, top, width - right, height - bottom)
            if (bounds[0] >= bounds[2] || bounds[1] >= bounds[3]) return null
            if (!hasContentInside(bitmap, bounds, border)) return null
            return bounds
        }

        /**
         * Length of the uniform run of border-coloured pixels starting at one edge.
         *
         * When [alongColumn] is true the run advances over x and probes down y, otherwise it
         * advances over y and probes across x.
         */
        private fun uniformBorderLength(
            bitmap: Bitmap,
            alongColumn: Boolean,
            fromStart: Boolean,
            max: Int,
            border: Int,
        ): Int {
            val outer = if (alongColumn) bitmap.width else bitmap.height
            val cross = if (alongColumn) bitmap.height else bitmap.width
            val limit = min(max, outer / 2)
            var length = 0
            while (length < limit) {
                val edgeIndex = if (fromStart) length else outer - length - 1
                if (!lineMatches(bitmap, edgeIndex, cross, alongColumn, border)) break
                length++
            }
            return length
        }

        /** True when enough sampled pixels along this line match the border colour. */
        private fun lineMatches(
            bitmap: Bitmap,
            edgeIndex: Int,
            cross: Int,
            alongColumn: Boolean,
            border: Int,
        ): Boolean {
            val stride = max(1, cross / MAX_EDGE_SAMPLES)
            var matched = 0
            var sampled = 0
            var crossIndex = 0
            while (crossIndex < cross) {
                val x = if (alongColumn) edgeIndex else crossIndex
                val y = if (alongColumn) crossIndex else edgeIndex
                if (matches(border, bitmap.getPixel(x, y))) matched++
                sampled++
                crossIndex += stride
            }
            return sampled > 0 && matched >= sampled * EDGE_MATCH_RATIO
        }

        /**
         * Guards against cropping a single-colour image.
         *
         * Every pixel of such an image matches the border colour, so a uniform-run measurement
         * finds a "border" on all four sides and trims the middle for zero visual benefit while
         * also forcing a needless re-encode of the result.
         */
        private fun hasContentInside(bitmap: Bitmap, bounds: IntArray, border: Int): Boolean {
            val (left, top, right, bottom) = bounds
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    if (!matches(border, bitmap.getPixel(x, y))) return true
                    x += CONTENT_PROBE_STEP
                }
                y += CONTENT_PROBE_STEP
            }
            return false
        }

        private fun matches(first: Int, second: Int): Boolean {
            return kotlin.math.abs((first shr 16 and 0xff) - (second shr 16 and 0xff)) <= COLOR_TOLERANCE &&
                kotlin.math.abs((first shr 8 and 0xff) - (second shr 8 and 0xff)) <= COLOR_TOLERANCE &&
                kotlin.math.abs((first and 0xff) - (second and 0xff)) <= COLOR_TOLERANCE
        }
    }
}
