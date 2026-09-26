package ephyra.domain.reader.viewport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The `RDR-004` viewport-state contract.
 *
 * The central claim is the one `DEF-001` disproved: **a zoom gesture must produce a different
 * transform than the one it started from.** The original defect computed a scale and offset on every
 * pinch and never bound them to anything drawn, so state changed and the screen did not. These tests
 * are written to catch that class of defect rather than re-check arithmetic — `PagerZoomPolicyTest`
 * already covers the maths. Several assert on the *action* the viewport returns rather than on policy
 * internals, so a transform that is computed but never applied cannot pass.
 */
class PagerViewportTest {

    private val size = ViewportSize(width = 1080f, height = 1920f)
    private val measured = PagerViewportState(viewportSize = size)

    @Test
    fun `a fresh viewport is at fit and unmeasured`() {
        val state = PagerViewportState()
        assertEquals(PagerZoomTransform.IDENTITY, state.transform)
        assertFalse(state.isMeasured)
        assertTrue(state.isAtFit)
    }

    @Test
    fun `a pinch changes the transform the viewport renders`() {
        val action = PagerViewport.onTransform(measured, 2f, 0f, 0f, 540f, 960f)
        assertTrue(action.changed, "A zoom factor of 2 must change the rendered transform")
        assertEquals(2f, action.transform.scale, 1e-3f)
    }

    @Test
    fun `a claimed transform is reported as owning the gesture`() {
        val action = PagerViewport.onTransform(measured, 1.5f, 0f, 0f, 540f, 960f)
        assertTrue(
            action.claimsGesture,
            "Once the viewport applies a transform the pager must not also act on the same gesture, " +
                "or a page swipe fires during a zoom",
        )
    }

    @Test
    fun `an unmeasured viewport refuses to transform rather than guessing a size`() {
        val action = PagerViewport.onTransform(PagerViewportState(), 2f, 0f, 0f, 540f, 960f)
        assertFalse(action.changed, "Without a measured size the focal inversion is undefined")
        assertFalse(action.claimsGesture)
    }

    @Test
    fun `double tap is anchored on the tapped point, not the viewport centre`() {
        val offCentre = PagerViewport.onDoubleTap(measured, 200f, 400f)
        val atCentre = PagerViewport.onDoubleTap(measured, 540f, 960f)
        assertTrue(offCentre.changed)
        assertEquals(2.5f, offCentre.transform.scale, 1e-3f)
        assertTrue(
            offCentre.transform != atCentre.transform,
            "A double tap off-centre must differ from one at the centre; identical results mean the " +
                "focal point is discarded, the defect pattern behind DEF-001",
        )
    }

    @Test
    fun `toggling fit zooms in, and toggling again re-centres`() {
        val zoomed = PagerViewport.onToggleFit(measured)
        assertEquals(2f, zoomed.transform.scale, 1e-3f)
        assertEquals(0f, zoomed.transform.offsetX, 1e-3f)

        val panned = zoomed.transform.copy(offsetX = 120f, offsetY = 80f)
        val reset = PagerViewport.onToggleFit(measured.copy(transform = panned))
        assertEquals(
            PagerZoomTransform.IDENTITY,
            reset.transform,
            "Returning to fit must re-centre; at fit the page is fully visible and a residual " +
                "offset is by definition out of bounds",
        )
    }

    @Test
    fun `a transform is clamped to the scaled viewport`() {
        val action = PagerViewport.onTransform(measured, 1f, 100_000f, 0f, 540f, 960f)
        val (maxX, _) = PagerZoomPolicy().panBounds(action.transform.scale, size)
        assertTrue(
            action.transform.offsetX <= maxX + 1e-3f,
            "Pan offset ${action.transform.offsetX} exceeds the bound $maxX; the page can be " +
                "dragged into empty space",
        )
    }

    @Test
    fun `shrinking the viewport re-clamps a transform that is now out of bounds`() {
        val zoomed = PagerViewport.onTransform(measured, 4f, 0f, 0f, 540f, 960f)
        val dragged = PagerViewport.onTransform(
            measured.copy(transform = zoomed.transform),
            1f,
            100_000f,
            0f,
            540f,
            960f,
        )
        val small = ViewportSize(width = 400f, height = 800f)
        val rescaled = PagerViewport.onViewportSizeChanged(
            measured.copy(transform = dragged.transform),
            small,
        )
        val (maxX, _) = PagerZoomPolicy().panBounds(rescaled.transform.scale, small)
        assertTrue(
            rescaled.transform.offsetX <= maxX + 1e-3f,
            "After a resize the offset ${rescaled.transform.offsetX} must be re-clamped to $maxX, " +
                "or content stays outside the viewport with no gesture left to correct it",
        )
    }

    @Test
    fun `a document change invalidates any active transform`() {
        val action = PagerViewport.onDocumentChanged()
        assertEquals(PagerZoomTransform.IDENTITY, action.transform)
        assertTrue(action.changed)
        assertFalse(
            PagerViewportState(transform = PagerZoomTransform(3f, 100f, 50f)).isAtFit,
            "sanity: a 3x transform is genuinely zoomed, so this test would be vacuous otherwise",
        )
    }

    @Test
    fun `cancelling a transform restores the last committed one`() {
        val committed = PagerZoomTransform(2f, 40f, 20f)
        val inFlight = PagerZoomTransform(3.5f, 200f, 150f)
        val action = PagerViewport.onTransformCancelled(
            measured.copy(transform = inFlight),
            committed,
        )
        assertEquals(
            committed,
            action.transform,
            "A cancelled transform must restore the committed value, not keep the in-flight one",
        )
    }

    @Test
    fun `committing keeps the transform that was reached`() {
        val reached = PagerZoomTransform(2.5f, 30f, 10f)
        val action = PagerViewport.onTransformCommitted(measured.copy(transform = reached))
        assertEquals(reached, action.transform)
        assertFalse(action.claimsGesture, "A commit releases the gesture")
    }

    @Test
    fun `pan ownership follows the zoom lock threshold, not a constant`() {
        assertTrue(PagerViewport.ownsPan(1.1f, 1.11f))
        assertFalse(PagerViewport.ownsPan(1.1f, 1.09f))
        assertFalse(
            PagerViewport.ownsPan(1.1f, 1.05f),
            "A sub-visual 1.05x zoom must not lock interaction; that band trapped users",
        )
    }
}
