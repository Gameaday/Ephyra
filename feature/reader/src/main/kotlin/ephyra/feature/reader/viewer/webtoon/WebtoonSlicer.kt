package ephyra.feature.reader.viewer.webtoon

import kotlin.math.ceil
import kotlin.math.max

/**
 * Pure (JVM-testable) math for virtual-strip slicing of long webtoon pages.
 *
 * Slices are computed in **source pixels** but sized from the **display** height so a slice
 * never exceeds the GPU texture ceiling once decoded at screen width:
 *
 * ```
 * scale = targetWidthPx / srcWidth
 * displayHeight = srcHeight * scale
 * partCount = ceil(displayHeight / maxSliceDisplayPx)
 * ```
 *
 * Adjacent slices overlap by [OVERLAP_DISPLAY_PX] display pixels (converted back to source
 * pixels) so bilinear sampling at slice edges cannot leave 1px hairline seams.
 */
object WebtoonSlicer {

    /** Overlap between adjacent slices, measured in display pixels. Must comfortably exceed
     * the 1px hairline-seam artifact it exists to prevent, with margin for decoder rounding
     * drift at any display density (10px ≈ 10x headroom at 1x, ~3x at 3x). */
    const val OVERLAP_DISPLAY_PX = 10

    /** Never emit more slices than this; beyond it the caller must use the single-image fallback. */
    const val MAX_SLICES = 32

    /** Minimum slice height in display pixels; avoids degenerate slivers on tiny remainders. */
    const val MIN_SLICE_DISPLAY_PX = 256

    /** Default per-slice ceiling when the caller has no viewport measurement. */
    const val DEFAULT_MAX_SLICE_DISPLAY_PX = 2048

    /**
     * A single slice rect in source pixels. [top] is inclusive, [bottom] is exclusive except
     * for the final slice, which is clamped to [srcHeight].
     */
    data class SliceRect(
        val index: Int,
        val count: Int,
        val top: Int,
        val bottom: Int,
    ) {
        val srcHeight: Int get() = bottom - top
    }

    /**
     * Returns `true` when the page needs slicing: its height once decoded at [targetWidthPx]
     * would exceed [textureLimitPx] (the device `GL_MAX_TEXTURE_SIZE` safety threshold).
     */
    fun needsSlicing(
        srcWidth: Int,
        srcHeight: Int,
        targetWidthPx: Int,
        textureLimitPx: Int,
    ): Boolean {
        if (srcWidth <= 0 || srcHeight <= 0 || targetWidthPx <= 0 || textureLimitPx <= 0) return false
        val displayHeight = srcHeight.toDouble() * targetWidthPx / srcWidth
        return displayHeight > textureLimitPx
    }

    /**
     * Effective per-slice ceiling in display pixels: the texture limit, optionally tightened by
     * the viewport (`viewportHeightPx * [viewportFactor]`) so only ~1.5 viewports of pixels are
     * resident per slice.
     */
    fun maxSliceDisplayPx(
        textureLimitPx: Int,
        viewportHeightPx: Int? = null,
        viewportFactor: Float = 1.5f,
    ): Int {
        val viewportCap = viewportHeightPx?.let { (it * viewportFactor).toInt() }
        val cap = if (viewportCap != null && viewportCap > 0) minOf(textureLimitPx, viewportCap) else textureLimitPx
        return max(cap, MIN_SLICE_DISPLAY_PX)
    }

    /**
     * Splits a [srcWidth]×[srcHeight] image into [SliceRect]s sized so no slice exceeds
     * [maxSliceDisplayPx] display pixels tall once scaled to [targetWidthPx].
     *
     * Returns a single slice when no split is needed, or an empty list when the page would need
     * more than [MAX_SLICES] (caller must fall back to single-image rendering).
     */
    fun computeSlices(
        srcWidth: Int,
        srcHeight: Int,
        targetWidthPx: Int,
        maxSliceDisplayPx: Int,
    ): List<SliceRect> {
        require(srcWidth > 0 && srcHeight > 0 && targetWidthPx > 0) {
            "Invalid dimensions src=${srcWidth}x$srcHeight targetWidth=$targetWidthPx"
        }
        val ceiling = max(maxSliceDisplayPx, MIN_SLICE_DISPLAY_PX)
        val scale = targetWidthPx.toDouble() / srcWidth
        val displayHeight = srcHeight * scale
        if (displayHeight <= ceiling) return listOf(SliceRect(0, 1, 0, srcHeight))

        // Reserve the overlap budget from the ceiling so a slice's own height plus the
        // overlap added to its bottom edge can never exceed the texture limit.
        val sliceBudget = (ceiling - OVERLAP_DISPLAY_PX).coerceAtLeast(MIN_SLICE_DISPLAY_PX / 2)
        var partCount = ceil(displayHeight / sliceBudget).toInt()
        // Merge slivers: if the remainder would be shorter than the minimum, use fewer slices.
        while (partCount > 1 && displayHeight / partCount < MIN_SLICE_DISPLAY_PX) partCount--
        if (partCount < 2) return listOf(SliceRect(0, 1, 0, srcHeight))
        if (partCount > MAX_SLICES) return emptyList()

        val overlapSrc = max(1, ceil(OVERLAP_DISPLAY_PX / scale).toInt())
        val baseHeight = srcHeight / partCount
        return List(partCount) { index ->
            val top = max(0, index * baseHeight - if (index > 0) overlapSrc else 0)
            val rawBottom = if (index == partCount - 1) {
                srcHeight
            } else {
                (index + 1) * baseHeight + overlapSrc
            }
            SliceRect(index, partCount, top, minOf(rawBottom, srcHeight))
        }
    }
}
