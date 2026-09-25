package ephyra.domain.reader.viewport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Semantics: the list is laid out in document units and the painted transform is a top-start
 * origin scale, so a document x maps to screen x as `documentX * scale + offsetX`.
 */
class WebtoonZoomPolicyTest {

    private val policy = WebtoonZoomPolicy()
    private val viewportWidth = 1000f

    private fun screenX(documentX: Float, zoom: WebtoonDocumentZoom): Float =
        documentX * zoom.scale + zoom.offsetX

    @Test
    fun `identity is scale one at no offset`() {
        val result = policy.apply(WebtoonDocumentZoom.IDENTITY, 1f, 0f, 500f, 500f, viewportWidth)
        assertEquals(1f, result.zoom.scale, 1e-4f)
        assertEquals(0f, result.zoom.offsetX, 1e-4f)
        assertEquals(0f, result.scrollCorrection, 1e-4f)
    }

    @Test
    fun `pinch zooms in on BOTH axes`() {
        // The reported defect: scaleY was pinned to 1, so a pinch only widened the strip. The
        // policy has a single isotropic scale, which the viewport applies to both axes.
        val result = policy.apply(WebtoonDocumentZoom.IDENTITY, 2f, 0f, 500f, 500f, viewportWidth)
        assertEquals(2f, result.zoom.scale, 1e-4f)
    }

    @Test
    fun `the focal point stays under the focal point`() {
        val focal = 250f
        val documentUnderFocal = (focal - 0f) / 1f
        val result = policy.apply(WebtoonDocumentZoom.IDENTITY, 2.5f, 0f, focal, 500f, viewportWidth)
        assertEquals(focal, screenX(documentUnderFocal, result.zoom), 1e-2f)
    }

    @Test
    fun `the focal invariant holds for an off centre pinch when within bounds`() {
        val focal = 300f
        val documentUnderFocal = focal / 1f
        val result = policy.apply(WebtoonDocumentZoom.IDENTITY, 1.5f, 0f, focal, 500f, viewportWidth)
        assertEquals(focal, screenX(documentUnderFocal, result.zoom), 1e-2f)
    }

    @Test
    fun `the focal yields to the pan clamp when the strip cannot fill the viewport`() {
        // At 3x with a focal at x=700, holding the focal would put the strip entirely off screen:
        // document 700 paints at 2100px, past the 1000px viewport. Clamping is correct here, and
        // the focal invariant is only claimed while the clamp is inactive.
        val result = policy.apply(WebtoonDocumentZoom.IDENTITY, 3f, 0f, 700f, 500f, viewportWidth)
        val bounds = policy.maxOffsetX(3f, viewportWidth)
        assertEquals(-bounds, result.zoom.offsetX, 1e-3f, "expected the clamp to bind")
    }

    @Test
    fun `the strip always covers the viewport once zoomed in`() {
        // At 3x the strip is 3000px wide in a 1000px viewport, so it is *supposed* to overflow:
        // that is what makes it pannable. The property that must hold is coverage, not containment.
        // If the painted strip ever became narrower than the viewport, empty background would show
        // at the edges, which is a visible hole in the strip.
        listOf(0f, 250f, 500f, 750f, 1000f).forEach { focal ->
            listOf(1.25f, 2f, 3f, 4f).forEach { factor ->
                val result = policy.apply(WebtoonDocumentZoom.IDENTITY, factor, 0f, focal, 500f, viewportWidth)
                val leftEdge = screenX(0f, result.zoom)
                val rightEdge = screenX(1000f, result.zoom)
                assertTrue(
                    leftEdge <= 0.5f && rightEdge >= viewportWidth - 0.5f,
                    "gap at focal=$focal factor=$factor: painted $leftEdge..$rightEdge",
                )
            }
        }
    }

    @Test
    fun `the focal invariant holds across a whole gesture`() {
        var zoom = WebtoonDocumentZoom.IDENTITY
        val focal = 300f
        val documentUnderFocal = (focal - zoom.offsetX) / zoom.scale
        repeat(5) {
            zoom = policy.apply(zoom, 1.4f, 0f, focal, 500f, viewportWidth).zoom
        }
        assertEquals(focal, screenX(documentUnderFocal, zoom), 1e-1f)
    }

    @Test
    fun `a zoom change produces a scroll correction`() {
        // Without this the list stays put and the strip jumps under the fingers even though the
        // scale maths is correct.
        val result = policy.apply(WebtoonDocumentZoom.IDENTITY, 2f, 0f, 250f, 500f, viewportWidth)
        assertTrue(
            abs(result.scrollCorrection) > 0.5f,
            "expected a non-trivial correction, was ${result.scrollCorrection}",
        )
    }

    @Test
    fun `a no-op gesture produces no scroll correction`() {
        val result = policy.apply(WebtoonDocumentZoom.IDENTITY, 1f, 0f, 250f, 500f, viewportWidth)
        assertEquals(0f, result.scrollCorrection, 1e-6f)
    }

    @Test
    fun `scale is clamped to the maximum`() {
        var zoom = WebtoonDocumentZoom.IDENTITY
        repeat(10) { zoom = policy.apply(zoom, 2f, 0f, 500f, 500f, viewportWidth).zoom }
        assertEquals(4f, zoom.scale, 1e-4f)
    }

    @Test
    fun `scale is clamped to the minimum`() {
        var zoom = WebtoonDocumentZoom(3f, 0f)
        repeat(10) { zoom = policy.apply(zoom, 0.5f, 0f, 500f, 500f, viewportWidth).zoom }
        assertEquals(1f, zoom.scale, 1e-4f)
    }

    @Test
    fun `zooming out to fit re-centres`() {
        val zoomed = WebtoonDocumentZoom(2f, 200f)
        val result = policy.apply(zoomed, 0.5f, 0f, 100f, 500f, viewportWidth)
        assertEquals(1f, result.zoom.scale, 1e-4f)
        assertEquals(0f, result.zoom.offsetX, 1e-4f)
    }

    @Test
    fun `pan is clamped to the scaled viewport`() {
        val result = policy.apply(WebtoonDocumentZoom(2f, 0f), 1f, 99_999f, 500f, 500f, viewportWidth)
        val bounds = policy.maxOffsetX(2f, viewportWidth)
        assertEquals(bounds, result.zoom.offsetX, 1e-3f)
    }

    @Test
    fun `pan bounds are zero at fit scale`() {
        assertEquals(0f, policy.maxOffsetX(1f, viewportWidth), 1e-4f)
    }

    @Test
    fun `pan bounds grow with scale`() {
        assertTrue(policy.maxOffsetX(3f, viewportWidth) > policy.maxOffsetX(1.5f, viewportWidth))
    }

    @Test
    fun `the visible document window shrinks as zoom increases`() {
        val fit = policy.visibleDocumentHeight(2000f, 1f)
        val zoomed = policy.visibleDocumentHeight(2000f, 2f)
        assertEquals(2000f, fit, 1e-3f)
        assertEquals(1000f, zoomed, 1e-3f)
    }

    @Test
    fun `a settled zoom keeps its focal across repeated identical gestures`() {
        // Convergence: a still gesture must stop producing corrections, or the strip drifts.
        var zoom = WebtoonDocumentZoom.IDENTITY
        val focal = 500f
        val documentUnderFocal = focal / 1f
        repeat(10) { zoom = policy.apply(zoom, 1.2f, 0f, focal, 500f, viewportWidth).zoom }
        assertEquals(focal, screenX(documentUnderFocal, zoom), 1e-1f)
    }

    @Test
    fun `the scroll correction is vertical, not horizontal`() {
        // The horizontal focal is already absorbed into offsetX. Returning a horizontal correction
        // as well would double-apply it and slide the strip sideways on every pinch. The list
        // scroll owns the vertical axis, so the correction is a vertical term only.
        val centred = policy.apply(WebtoonDocumentZoom.IDENTITY, 2f, 0f, 500f, 0f, viewportWidth)
        assertEquals(0f, centred.scrollCorrection, 1e-3f, "a top-of-viewport focal must not move the list")

        val offCentre = policy.apply(WebtoonDocumentZoom.IDENTITY, 2f, 0f, 250f, 800f, viewportWidth)
        assertTrue(offCentre.scrollCorrection > 0f, "expected a downward list move, was ${offCentre.scrollCorrection}")
    }

    @Test
    fun `the scroll correction is zero for a no-op scale change`() {
        val result = policy.apply(WebtoonDocumentZoom.IDENTITY, 1f, 0f, 250f, 800f, viewportWidth)
        assertEquals(0f, result.scrollCorrection, 1e-6f)
    }

    @Test
    fun `zooming back out reverses the list movement`() {
        // The corrections must be symmetric, or a round trip through zoom would drift down the
        // document instead of returning to where it started.
        val up = policy.apply(WebtoonDocumentZoom.IDENTITY, 2f, 0f, 500f, 800f, viewportWidth)
        val down = policy.apply(up.zoom, 0.5f, 0f, 500f, 800f, viewportWidth)
        assertEquals(0f, up.scrollCorrection + down.scrollCorrection, 1e-3f)
    }

    @Test
    fun `invalid input is rejected`() {
        assertThrows<IllegalArgumentException> {
            policy.apply(WebtoonDocumentZoom.IDENTITY, 0f, 0f, 500f, 500f, viewportWidth)
        }
        assertThrows<IllegalArgumentException> {
            policy.apply(WebtoonDocumentZoom.IDENTITY, Float.NaN, 0f, 500f, 500f, viewportWidth)
        }
        assertThrows<IllegalArgumentException> {
            policy.apply(WebtoonDocumentZoom.IDENTITY, 1f, 0f, 500f, 500f, 0f)
        }
        assertThrows<IllegalArgumentException> { WebtoonDocumentZoom(0f, 0f) }
    }

    @Test
    fun `a policy with an inverted range is rejected`() {
        assertThrows<IllegalArgumentException> { WebtoonZoomPolicy(minScale = 3f, maxScale = 1f) }
        assertThrows<IllegalArgumentException> { WebtoonZoomPolicy(minScale = 0f, maxScale = 1f) }
    }

    private fun abs(value: Float): Float = if (value < 0) -value else value
}
