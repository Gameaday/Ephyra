package ephyra.feature.reader.viewer.webtoon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Seam-invariant evidence for the webtoon slice renderer's pixel-exact layout
 * ([WebtoonSeamLayout]).
 *
 * The defect these tests exist to keep dead: the old renderer allocated each slice a
 * `weight` share of the item box and drew its bitmap with `ContentScale.FillWidth` — two
 * independent roundings (allocation vs. the bitmap's own display height) that disagreed by
 * up to a device pixel per joint, the reported ~1px seam. The layout now places slices at
 * their cumulative DECODED bitmap offsets under one continuous scale, so:
 *
 *  - adjacent slices share one exact boundary (no gap, no overlap — no duplicated or
 *    missing pixel row),
 *  - the placements are independent of display size, device density and zoom (the
 *    item-level zoom transform scales the whole canvas uniformly), and
 *  - the union of the placements is exactly the display box.
 *
 * The matrix below sweeps slice counts (1..[WebtoonSlicer.MAX_SLICES]), bitmap heights
 * (including 1px slices and odd values that model inSampleSize rounding), display sizes
 * (several plausible device resolutions) and zoom factors, because the guarantee must hold
 * "even if zoomed in or on any device resolution or format".
 */
class WebtoonSeamLayoutTest {

    private fun heightsFor(sliceCount: Int): List<Int> = when (sliceCount) {
        1 -> listOf(2048)
        // Deliberately ragged: models BitmapRegionDecoder's per-region inSampleSize rounding.
        else -> List(sliceCount) { i -> ((i * 37 + 11) % 97) + 1 }
    }

    private fun assertExactTiling(placements: List<WebtoonSeamSlice>, displayHeight: Double) {
        // Bitmap space: the placements' bitmap intervals partition [0, total) exactly —
        // every decoded row is displayed, none twice.
        assertEquals(0, placements.first().bitmapTop)
        placements.zipWithNext().forEach { (a, b) ->
            assertEquals(a.bitmapTop + a.bitmapHeight, b.bitmapTop, "bitmap-space gap at slice ${b.index}")
        }
        val total = placements.sumOf { it.bitmapHeight }
        assertEquals(total, placements.last().bitmapTop + placements.last().bitmapHeight)

        // Display space: adjacent slices share ONE exact boundary — the same double on both
        // sides of the joint. Any epsilon here is a rendered hairline.
        assertEquals(0.0, placements.first().dstTop, 0.0)
        placements.zipWithNext().forEach { (a, b) ->
            assertEquals(a.dstBottom, b.dstTop, 0.0, "display-space seam not exact at slice ${b.index}")
        }
        assertTrue(
            abs(placements.last().dstBottom - displayHeight) < 1e-9,
            "the strip must cover the display box exactly: ${placements.last().dstBottom} vs $displayHeight",
        )
        placements.forEach { p ->
            assertTrue(p.dstBottom > p.dstTop, "slice ${p.index} has non-positive display height")
        }
    }

    @Test
    fun `slices tile the display box exactly - no gap, no overlap, for the full slice matrix`() {
        for (sliceCount in 1..WebtoonSlicer.MAX_SLICES) {
            for (displayHeight in listOf(812.0, 1080.0, 1440.0, 2400.0, 16200.0)) {
                val placements = WebtoonSeamLayout.place(heightsFor(sliceCount), displayHeight)
                assertEquals(sliceCount, placements.size)
                assertExactTiling(placements, displayHeight)
            }
        }
    }

    @Test
    fun `every displayed row belongs to exactly one slice - no pixel duplicated or missing`() {
        val heights = heightsFor(9)
        val displayHeight = 1337.0
        val placements = WebtoonSeamLayout.place(heights, displayHeight)

        // Sample the display box densely (plus every joint exactly) and count how many
        // half-open [dstTop, dstBottom) intervals contain each point: always exactly one.
        val points = buildList {
            for (i in 0..2000) add(displayHeight * i / 2000.0)
            placements.forEach { add(it.dstTop); add(it.dstBottom) }
        }
        points.forEach { point ->
            val owners = placements.count { it.dstTop <= point && point < it.dstBottom }
            assertEquals(1, owners, "display row $point covered ${owners}x — duplicated or missing pixel")
        }
    }

    @Test
    fun `a 1px-tall slice is laid out, not dropped`() {
        val placements = WebtoonSeamLayout.place(listOf(2048, 1, 2048, 1, 2048), 3000.0)

        assertExactTiling(placements, 3000.0)
        assertEquals(listOf(1, 1), placements.filter { it.bitmapHeight == 1 }.map { it.bitmapHeight })
    }

    @Test
    fun `layout is zoom- and resolution-invariant`() {
        val heights = heightsFor(7)

        // The bitmap-space placement — where the seams live — is identical at every display
        // size: zooming (a uniform scale of the whole canvas) and changing device resolution
        // re-scale the strip, they never re-round individual slice boundaries.
        val reference = WebtoonSeamLayout.place(heights, 1080.0)
        for (displayHeight in listOf(812.0, 1440.0, 2400.0, 4320.0)) {
            val other = WebtoonSeamLayout.place(heights, displayHeight)
            assertEquals(
                reference.map { it.bitmapTop to it.bitmapHeight },
                other.map { it.bitmapTop to it.bitmapHeight },
            )
        }

        // The display-space mapping is a pure linear scale: doubling the box doubles every
        // boundary, without re-rounding.
        val doubled = WebtoonSeamLayout.place(heights, 2160.0)
        reference.zip(doubled).forEach { (a, b) ->
            assertEquals(a.dstTop * 2, b.dstTop, 1e-9)
            assertEquals(a.dstBottom * 2, b.dstBottom, 1e-9)
        }
    }

    @Test
    fun `max slice count still tiles exactly`() {
        val heights = List(WebtoonSlicer.MAX_SLICES) { 512 }
        assertExactTiling(WebtoonSeamLayout.place(heights, 16384.0), 16384.0)
    }

    @Test
    fun `degenerate inputs fail closed`() {
        fun expectError(block: () -> Unit) {
            try {
                block()
                fail("expected IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {
                // Fail-closed is the contract: a caller that cannot satisfy it falls back to
                // the whole-image path instead of rendering a strip with holes.
            }
        }
        expectError { WebtoonSeamLayout.place(emptyList(), 100.0) }
        expectError { WebtoonSeamLayout.place(listOf(100, 0), 100.0) }
        expectError { WebtoonSeamLayout.place(listOf(100, -5), 100.0) }
        expectError { WebtoonSeamLayout.place(listOf(100), 0.0) }
        expectError { WebtoonSeamLayout.place(listOf(100), -1.0) }
    }
}
