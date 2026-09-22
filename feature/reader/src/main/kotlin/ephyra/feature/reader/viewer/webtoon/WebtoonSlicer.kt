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
 * Slices form an **exact partition**: `slices[0].top == 0`, `slices.last().bottom == srcHeight`
 * and `slices[i].bottom == slices[i + 1].top`. Every source row is decoded and displayed
 * exactly once, so stacked slices can never double-render content.
 *
 * Overlap was deliberately removed: decoding overlapping rows and stacking both copies at full
 * height duplicated a band of content at every joint (2x overlap), broke aspect-ratio
 * accounting, and made revisit/layout unstable. If a 1px hairline seam is ever observed on a
 * specific GPU, the fix is to decode with overlap but *clip* (display only the non-overlapping
 * window) — never to stack overlapping bitmaps at full height.
 */
object WebtoonSlicer {

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
     * The result is an exact partition with no overlap and no gaps: slice `i` covers
     * `[i * srcHeight / partCount, (i + 1) * srcHeight / partCount)` (last slice clamped
     * to [srcHeight]), so stacked slices render each source row exactly once.
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

        // Exact tiling: partCount is sized directly from the ceiling, no overlap budget.
        var partCount = ceil(displayHeight / ceiling).toInt()
        // Merge slivers: if the remainder would be shorter than the minimum, use fewer slices.
        while (partCount > 1 && displayHeight / partCount < MIN_SLICE_DISPLAY_PX) partCount--
        if (partCount < 2) return listOf(SliceRect(0, 1, 0, srcHeight))
        if (partCount > MAX_SLICES) return emptyList()

        // Distribute remainder rows across leading slices so no slice exceeds the ceiling
        // and no thin sliver remains: top(i) = floor(i * srcHeight / partCount).
        return List(partCount) { index ->
            val top = (index.toLong() * srcHeight / partCount).toInt()
            val bottom = ((index + 1).toLong() * srcHeight / partCount).toInt()
            SliceRect(index, partCount, top, bottom)
        }
    }
}
