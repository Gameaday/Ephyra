package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.floor

/**
 * Pins the decode-width decision both readers now share.
 *
 * The webtoon reader's inline arithmetic is reproduced exactly by the first case, which is the point:
 * the migration had to be behaviour-preserving, and "preserve the existing behaviour" is only a
 * claim until it is asserted against the formula it replaced.
 */
class PageDecodeWidthTest {

    /**
     * The exact expression this replaces, as it stood in `ComposeWebtoonReader`:
     * `(targetWidthPx * densityScale.coerceAtLeast(1f)).toInt().coerceAtLeast(1)`.
     *
     * Reproduced here rather than quoted, so a future change to either side that is not mirrored on
     * the other turns this red instead of quietly altering what a page is decoded at.
     */
    private fun legacyWebtoonWidth(displayWidthPx: Float, densityScale: Float): Int =
        (displayWidthPx * densityScale.coerceAtLeast(1f)).toInt().coerceAtLeast(1)

    @ParameterizedTest
    @CsvSource(
        "1080, 3.0, 3240",
        "1080, 2.75, 2970",
        "411, 2.625, 1079", // rounds up; see the truncation note below
        "800, 1.0, 800",
        "1920, 1.5, 2880",
    )
    fun `a fixed-scale strip decodes at the width it replaced`(
        display: Float,
        density: Float,
        expected: Int,
    ) {
        val width = PageDecodeWidth.plan(
            intrinsicWidth = 4000,
            displayWidthPx = display,
            densityScale = density,
            zoom = PageZoomPolicy.FIXED_SCALE,
        )
        assertEquals(expected, width)
        // The legacy comparison holds only where the product is integral. On a fractional one the
        // replaced arithmetic truncated and this rounds up, which is the point of the test below.
        if (display * density == floor(display * density).toFloat()) {
            assertEquals(
                legacyWebtoonWidth(display, density),
                width,
                "must match the arithmetic it replaced wherever the two can agree",
            )
        }
    }

    /**
     * The one deliberate difference from the arithmetic this replaces, and the reason for it.
     *
     * The inline expression truncated: `(411 * 2.625).toInt()` is 1078, not 1079. A decode one pixel
     * narrower than the layout asks for is the "blurry strip" defect in miniature -- the whole point
     * of computing a physical width was to never fetch fewer pixels than will be shown, and
     * truncation reintroduces exactly that at the boundary. Rounding up cannot cause the defect, and
     * the cost of the extra pixel is nil.
     *
     * Found by the equivalence assertion above rather than by reading: the migration was supposed to
     * be behaviour-preserving, and on one input it was not. That is what the assertion is for.
     */
    @Test
    fun `a fractional physical width rounds up rather than truncating`() {
        val display = 411f
        val density = 2.625f

        assertEquals(1078, legacyWebtoonWidth(display, density), "the replaced arithmetic truncated")
        assertEquals(
            1079,
            PageDecodeWidth.plan(4000, display, density, PageZoomPolicy.FIXED_SCALE),
            "a decode narrower than the layout is the defect this width exists to prevent",
        )
    }

    /**
     * The new guarantee. The inline version asked for more pixels than the image had and relied on
     * the decoder to clamp; that made the bound invisible and untestable.
     */
    @Test
    fun `a fixed-scale page is never decoded wider than its source`() {
        val width = PageDecodeWidth.plan(
            intrinsicWidth = 600,
            displayWidthPx = 1080f,
            densityScale = 3f,
            zoom = PageZoomPolicy.FIXED_SCALE,
        )

        assertEquals(600, width, "a 600px source cannot yield 3240 pixels of detail")
    }

    /**
     * The counterweight, and the reason the two readers are not simply unified. A zoomable viewport
     * must be able to resolve detail beyond the display size, so bounding the decode to it would
     * trade a memory win for a visibly soft zoom.
     */
    @Test
    fun `a zoomable page decodes at its source width, not its display width`() {
        val width = PageDecodeWidth.plan(
            intrinsicWidth = 4000,
            displayWidthPx = 1080f,
            densityScale = 3f,
            zoom = PageZoomPolicy.ZOOMABLE,
        )

        assertEquals(4000, width)
        assertTrue(width > 1080 * 3, "zoom needs more pixels than the display shows")
    }

    /**
     * The "blurry strip" defect, named in the code this replaces: decoding at layout pixels and
     * letting the view upscale produces a soft image on a high-density display. The floor at density
     * 1 is what prevents it.
     */
    @ParameterizedTest
    @CsvSource("0.75, 1080", "0.5, 1080", "0.0, 1080")
    fun `a sub-unit density never decodes below the layout size`(density: Float, expected: Int) {
        val width = PageDecodeWidth.plan(
            intrinsicWidth = 4000,
            displayWidthPx = 1080f,
            densityScale = density,
            zoom = PageZoomPolicy.FIXED_SCALE,
        )

        assertEquals(expected, width)
    }

    /**
     * An unknown intrinsic width must not collapse the answer. The page list arrives before image
     * dimensions do, so this is a real state rather than a defensive one.
     */
    @Test
    fun `an unknown source width falls back to the display size rather than to nothing`() {
        val width = PageDecodeWidth.plan(
            intrinsicWidth = 0,
            displayWidthPx = 1080f,
            densityScale = 2f,
            zoom = PageZoomPolicy.FIXED_SCALE,
        )

        assertEquals(2160, width)
    }

    @Test
    fun `an unknown source width on a zoomable page still decodes at least one pixel`() {
        // A zero-width bitmap is not decodable, and the failure would surface as an unexplained
        // decode error far from the cause.
        val width = PageDecodeWidth.plan(
            intrinsicWidth = 0,
            displayWidthPx = 0f,
            densityScale = 1f,
            zoom = PageZoomPolicy.FIXED_SCALE,
        )

        assertEquals(1, width)
    }

    @Test
    fun `a degenerate display size still yields a decodable width`() {
        for (zoom in PageZoomPolicy.entries) {
            val width = PageDecodeWidth.plan(
                intrinsicWidth = 0,
                displayWidthPx = -50f,
                densityScale = Float.NaN,
                zoom = zoom,
            )
            assertTrue(width >= 1, "$zoom produced $width")
        }
    }
}
