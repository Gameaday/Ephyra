package ephyra.domain.reader.viewport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Screen position of a document point under a transform, using the viewport-centre origin that
 * `graphicsLayer` defaults to:
 *
 *     screen = centre + (doc - centre) * scale + offset
 *
 * The focal tests assert against this projection directly rather than against the algebra that
 * produced it, so a sign or origin mistake cannot hide behind a matching implementation bug.
 */
class PagerZoomPolicyTest {

    private val policy = PagerZoomPolicy()
    private val viewport = ViewportSize(1000f, 2000f)
    private val centre = PagerTransformPoint(500f, 1000f)

    private fun projectX(doc: Float, t: PagerZoomTransform): Float =
        viewport.width / 2f + (doc - viewport.width / 2f) * t.scale + t.offsetX

    private fun projectY(doc: Float, t: PagerZoomTransform): Float =
        viewport.height / 2f + (doc - viewport.height / 2f) * t.scale + t.offsetY

    private fun documentUnderFocal(focal: PagerTransformPoint, t: PagerZoomTransform): Pair<Float, Float> =
        Pair(
            viewport.width / 2f + (focal.x - viewport.width / 2f - t.offsetX) / t.scale,
            viewport.height / 2f + (focal.y - viewport.height / 2f - t.offsetY) / t.scale,
        )

    @Test
    fun `identity is scale one at no offset`() {
        val next = policy.next(PagerZoomTransform.IDENTITY, zoom = 1f, focal = centre, viewportSize = viewport)
        assertTrue(next.isCloseTo(PagerZoomTransform.IDENTITY))
    }

    @Test
    fun `a pinch increases the scale`() {
        val next = policy.next(PagerZoomTransform.IDENTITY, zoom = 2f, focal = centre, viewportSize = viewport)
        assertEquals(2f, next.scale, 1e-4f)
    }

    @Test
    fun `scale is clamped to the maximum`() {
        var transform = PagerZoomTransform.IDENTITY
        repeat(10) { transform = policy.next(transform, zoom = 2f, focal = centre, viewportSize = viewport) }
        assertEquals(5f, transform.scale, 1e-4f)
    }

    @Test
    fun `scale is clamped to the minimum`() {
        var transform = PagerZoomTransform(2f, 0f, 0f)
        repeat(10) { transform = policy.next(transform, zoom = 0.5f, focal = centre, viewportSize = viewport) }
        assertEquals(1f, transform.scale, 1e-4f)
    }

    @Test
    fun `zooming out to fit always re-centres`() {
        val zoomed = PagerZoomTransform(2f, 300f, 400f)
        val next = policy.next(zoomed, zoom = 0.5f, focal = PagerTransformPoint(100f, 100f), viewportSize = viewport)
        assertEquals(1f, next.scale, 1e-4f)
        assertEquals(0f, next.offsetX, 1e-4f)
        assertEquals(0f, next.offsetY, 1e-4f)
    }

    @Test
    fun `an off centre focal point does not drift`() {
        val focal = PagerTransformPoint(200f, 400f)
        val (docX, docY) = documentUnderFocal(focal, PagerZoomTransform.IDENTITY)
        val next = policy.next(PagerZoomTransform.IDENTITY, zoom = 2f, focal = focal, viewportSize = viewport)

        assertEquals(focal.x, projectX(docX, next), 1e-2f, "content drifted horizontally")
        assertEquals(focal.y, projectY(docY, next), 1e-2f, "content drifted vertically")
    }

    @Test
    fun `the focal invariant holds across a whole gesture`() {
        val focal = PagerTransformPoint(300f, 900f)
        val (docX, docY) = documentUnderFocal(focal, PagerZoomTransform.IDENTITY)
        var transform = PagerZoomTransform.IDENTITY
        repeat(6) { transform = policy.next(transform, zoom = 1.4f, focal = focal, viewportSize = viewport) }

        assertEquals(focal.x, projectX(docX, transform), 1e-1f)
        assertEquals(focal.y, projectY(docY, transform), 1e-1f)
    }

    @Test
    fun `a focal point at the exact centre produces no offset`() {
        val next = policy.next(PagerZoomTransform.IDENTITY, zoom = 2.5f, focal = centre, viewportSize = viewport)
        assertEquals(0f, next.offsetX, 1e-2f)
        assertEquals(0f, next.offsetY, 1e-2f)
    }

    @Test
    fun `panning moves the offset by the drag distance`() {
        val start = PagerZoomTransform(2f, 0f, 0f)
        val next = policy.next(start, zoom = 1f, pan = 40f, panY = -25f, focal = centre, viewportSize = viewport)
        assertEquals(40f, next.offsetX, 1e-3f)
        assertEquals(-25f, next.offsetY, 1e-3f)
    }

    @Test
    fun `pan is clamped to the scaled viewport`() {
        val (maxX, maxY) = policy.panBounds(2f, viewport)
        val next = policy.next(
            PagerZoomTransform(2f, 0f, 0f),
            zoom = 1f,
            pan = 99_999f,
            panY = 99_999f,
            focal = centre,
            viewportSize = viewport,
        )
        assertEquals(maxX, next.offsetX, 1e-3f)
        assertEquals(maxY, next.offsetY, 1e-3f)
    }

    @Test
    fun `pan bounds are zero at fit scale`() {
        val (fitX, fitY) = policy.panBounds(1f, viewport)
        assertEquals(0f, fitX, 1e-4f)
        assertEquals(0f, fitY, 1e-4f)
    }

    @Test
    fun `pan bounds grow with scale`() {
        val (fitX, _) = policy.panBounds(1f, viewport)
        val (zoomedX, zoomedY) = policy.panBounds(3f, viewport)
        assertTrue(zoomedX > fitX)
        assertTrue(zoomedY > 0f)
    }

    @Test
    fun `pan bounds use the scaled extent not the unscaled one`() {
        // (1000*3 - 1000)/2 = 1000 horizontally, (2000*3 - 2000)/2 = 2000 vertically.
        val (maxX, maxY) = policy.panBounds(3f, viewport)
        assertEquals(1000f, maxX, 1e-3f)
        assertEquals(2000f, maxY, 1e-3f)
    }

    @Test
    fun `double tap zooms to the requested scale`() {
        val next = policy.doubleTap(PagerZoomTransform.IDENTITY, PagerTransformPoint(500f, 500f), viewport, 2.5f)
        assertEquals(2.5f, next.scale, 1e-4f)
    }

    @Test
    fun `double tap is anchored on the tapped point`() {
        val tapped = PagerTransformPoint(250f, 500f)
        val (docX, docY) = documentUnderFocal(tapped, PagerZoomTransform.IDENTITY)
        val next = policy.doubleTap(PagerZoomTransform.IDENTITY, tapped, viewport, 2.5f)
        assertEquals(tapped.x, projectX(docX, next), 1e-2f)
        assertEquals(tapped.y, projectY(docY, next), 1e-2f)
    }

    @Test
    fun `double tap toggling back to fit recentres`() {
        val zoomed = policy.doubleTap(PagerZoomTransform.IDENTITY, PagerTransformPoint(250f, 500f), viewport, 2.5f)
        val back = policy.doubleTap(zoomed, PagerTransformPoint(500f, 1000f), viewport, 1f)
        assertEquals(1f, back.scale, 1e-4f)
        assertEquals(0f, back.offsetX, 1e-4f)
        assertEquals(0f, back.offsetY, 1e-4f)
    }

    @Test
    fun `invalid gesture input is rejected`() {
        assertThrows<IllegalArgumentException> {
            policy.next(PagerZoomTransform.IDENTITY, zoom = 0f, focal = centre, viewportSize = viewport)
        }
        assertThrows<IllegalArgumentException> {
            policy.next(PagerZoomTransform.IDENTITY, zoom = Float.NaN, focal = centre, viewportSize = viewport)
        }
        assertThrows<IllegalArgumentException> {
            policy.next(PagerZoomTransform.IDENTITY, zoom = 1f, focal = centre, viewportSize = ViewportSize(0f, 100f))
        }
        assertThrows<IllegalArgumentException> { PagerZoomTransform(0f, 0f, 0f) }
    }

    @Test
    fun `a policy with an inverted range is rejected`() {
        assertThrows<IllegalArgumentException> { PagerZoomPolicy(minScale = 3f, maxScale = 1f) }
        assertThrows<IllegalArgumentException> { PagerZoomPolicy(minScale = 0f, maxScale = 1f) }
    }
}
