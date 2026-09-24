package ephyra.data.coil

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.min

/**
 * Removes a small, uniform border from a decoded reader page.
 *
 * This is intentionally conservative. A page is cropped only when all four corners share the
 * same border colour and each edge contains a meaningful run of that colour. Artwork with noisy
 * or non-uniform edges is returned unchanged.
 */
class BorderCropTransformation : Transformation() {
    override val cacheKey: String = "ephyra-border-crop-v1"

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

            val top = runLength(bitmap, horizontal = true, fromStart = true, max = maxBorderY, border)
            val bottom = runLength(bitmap, horizontal = true, fromStart = false, max = maxBorderY, border)
            val left = runLength(bitmap, horizontal = false, fromStart = true, max = maxBorderX, border)
            val right = runLength(bitmap, horizontal = false, fromStart = false, max = maxBorderX, border)
            if (top < MIN_BORDER || bottom < MIN_BORDER || left < MIN_BORDER || right < MIN_BORDER) return null

            return intArrayOf(left, top, width - right, height - bottom)
        }

        private fun runLength(
            bitmap: Bitmap,
            horizontal: Boolean,
            fromStart: Boolean,
            max: Int,
            border: Int,
        ): Int {
            val outer = if (horizontal) bitmap.width else bitmap.height
            var count = 0
            while (count < min(max, outer / 2)) {
                val index = if (fromStart) count else outer - count - 1
                val x = if (horizontal) {
                    index
                } else if (fromStart) {
                    0
                } else {
                    bitmap.width - 1
                }
                val y = if (horizontal) if (fromStart) 0 else bitmap.height - 1 else index
                if (!matches(border, bitmap.getPixel(x, y))) break
                count++
            }
            return count
        }

        private fun matches(first: Int, second: Int): Boolean {
            return kotlin.math.abs((first shr 16 and 0xff) - (second shr 16 and 0xff)) <= COLOR_TOLERANCE &&
                kotlin.math.abs((first shr 8 and 0xff) - (second shr 8 and 0xff)) <= COLOR_TOLERANCE &&
                kotlin.math.abs((first and 0xff) - (second and 0xff)) <= COLOR_TOLERANCE
        }
    }
}
