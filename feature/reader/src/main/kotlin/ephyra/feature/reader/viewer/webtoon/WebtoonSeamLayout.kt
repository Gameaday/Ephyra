package ephyra.feature.reader.viewer.webtoon

/**
 * Pixel-exact seam layout for stacked webtoon slices — the ~1px hairline-seam fix.
 *
 * The previous renderer laid slices out with Compose `weight` distribution and drew each
 * bitmap with `ContentScale.FillWidth`: the height a slice was *allocated* (a rounded share
 * of the item box, derived from SOURCE slice heights) and the height its bitmap actually
 * *needed* at display width (`displayWidth * bitmapHeight / bitmapWidth`, derived from the
 * DECODED bitmap) were two independent roundings that could disagree by a device pixel at
 * every joint. That disagreement was the reported ~1px gap — visible when zoomed in, and on
 * any device density or image format where the two roundings diverged, which is all of them
 * sooner or later.
 *
 * The fix: one canvas, one continuous scale, slices drawn at their cumulative DECODED
 * bitmap offsets. Adjacent slices share ONE exact boundary — slice i's bottom edge and
 * slice i+1's top edge are the same number — so no rounding can open a gap, duplicate a
 * displayed row, or drop one:
 *
 *  - Any zoom: the item-level zoom transform scales this canvas uniformly, so boundaries
 *    stay coincident at every zoom factor.
 *  - Any device resolution/density: layout depends only on the decoded bitmap heights and
 *    the resolved canvas size; the boundary between two slices never passes through an
 *    independent per-slice rounding.
 *  - Any format: placement consumes the decoded bitmaps' actual heights, so however the
 *    region decoder rounded each region (inSampleSize, odd strips), the layout is computed
 *    from what was really decoded, never from what was planned.
 *
 * Pure and JVM-testable: [ephyra.feature.reader.viewer.webtoon.WebtoonSeamLayoutTest] pins
 * the no-gap / no-duplicate / no-missing invariants across a matrix of slice counts, bitmap
 * heights, display sizes and zoom scales.
 */
data class WebtoonSeamSlice(
    val index: Int,
    /** Top of this slice's bitmap in decoded bitmap pixels — the drawImage dstOffset y. */
    val bitmapTop: Int,
    /** Height of this slice's decoded bitmap in bitmap pixels. */
    val bitmapHeight: Int,
    /** Display-space top boundary (cumulative, exact by construction). Test invariant. */
    val dstTop: Double,
    /** Display-space bottom boundary — exactly the next slice's [dstTop], or the box edge. */
    val dstBottom: Double,
)

object WebtoonSeamLayout {

    /**
     * Places slices of the given decoded bitmap heights into a display box of
     * [displayHeight]. Each slice's display height is proportional to its bitmap height, so
     * the union of the placements is exactly `[0, displayHeight]`: no gap between slices, no
     * duplicated band, no missing row.
     *
     * Fail-closed on degenerate input (empty slices, non-positive heights, non-positive
     * display height): a caller that cannot satisfy the contract renders its whole-image
     * fallback instead of a strip with holes.
     */
    fun place(bitmapHeights: List<Int>, displayHeight: Double): List<WebtoonSeamSlice> {
        require(displayHeight > 0.0) { "displayHeight must be positive, was $displayHeight" }
        require(bitmapHeights.isNotEmpty()) { "at least one slice is required" }
        require(bitmapHeights.all { it > 0 }) {
            "bitmap heights must be positive, was $bitmapHeights"
        }
        val scale = displayHeight / bitmapHeights.sumOf { it.toDouble() }
        var bitmapTop = 0
        var dst = 0.0
        return bitmapHeights.mapIndexed { index, height ->
            val slice = WebtoonSeamSlice(
                index = index,
                bitmapTop = bitmapTop,
                bitmapHeight = height,
                dstTop = dst,
                dstBottom = dst + height * scale,
            )
            bitmapTop += height
            dst = slice.dstBottom
            slice
        }
    }
}
