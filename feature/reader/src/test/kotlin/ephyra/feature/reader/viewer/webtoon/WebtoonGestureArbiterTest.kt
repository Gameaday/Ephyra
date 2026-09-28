package ephyra.feature.reader.viewer.webtoon

import ephyra.domain.reader.gesture.ReaderGestureArbiter
import ephyra.domain.reader.gesture.ReaderGestureConfig
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.gesture.ReaderGesturePhase
import ephyra.domain.reader.gesture.ReaderGesturePointerSample
import ephyra.domain.reader.gesture.ReaderGestureState
import ephyra.domain.reader.gesture.ReaderViewportMode
import ephyra.feature.reader.viewer.zoom.ZoomPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The continuous reader's gesture rules, now enforced by the shared arbiter.
 *
 * These three cases previously lived in `WebtoonGesturesTest` against the deleted hand-rolled
 * detector. They are **not** deleted coverage: each one is re-expressed here against
 * [ReaderGestureArbiter] in [ReaderViewportMode.CONTINUOUS], which is what now owns the rule. Keeping
 * them would have been a second implementation of the same decisions; dropping them would have lost
 * the guarantee that a continuous vertical drag is not stolen from the `LazyColumn`.
 *
 * The port is not a rename. The arbiter reaches these decisions through its phase machine and its
 * `viewportOwnsPan` rule rather than through inline predicates, so a regression now has to survive
 * the state machine to be invisible — which is the point of having one arbiter.
 */
class WebtoonGestureArbiterTest {

    private val slop = 20f

    private fun config(zoomed: Boolean) = ReaderGestureConfig(
        viewportMode = ReaderViewportMode.CONTINUOUS,
        touchSlop = slop,
        meaningfullyZoomed = zoomed,
    )

    private fun sample(
        pointers: Int = 1,
        panX: Float = 0f,
        panY: Float = 0f,
        zoom: Float = 1f,
        consumed: Boolean = false,
    ) = ReaderGesturePointerSample(
        pressedPointers = pointers,
        centroidX = 0f,
        centroidY = 0f,
        panX = panX,
        panY = panY,
        zoomChange = zoom,
        consumedByParent = consumed,
    )

    private fun down(): ReaderGestureState =
        ReaderGestureArbiter.down(ReaderGestureState(), "chapter-1", 0f, 0f).state

    /**
     * The rule the deleted `shouldClaimWebtoonHorizontalPan` encoded: while zoomed, the viewport
     * claims a *dominant horizontal* pan and declines a vertical or balanced one, because the
     * `LazyColumn` owns vertical position.
     */
    @Test
    fun `a continuous viewport claims only a dominant horizontal pan while zoomed`() {
        val horizontal = ReaderGestureArbiter.sample(down(), sample(panX = 25f, panY = 10f), config(zoomed = true))
        assertEquals(
            ReaderGesturePhase.TRANSFORM,
            horizontal.state.phase,
            "a dominant horizontal pan at zoom belongs to the viewport",
        )

        val vertical = ReaderGestureArbiter.sample(down(), sample(panX = 10f, panY = 25f), config(zoomed = true))
        assertEquals(
            ReaderGestureEffect.DelegateSingleScroll,
            vertical.effect,
            "a vertical pan belongs to the LazyColumn even while zoomed",
        )
        assertEquals(ReaderGesturePhase.SINGLE_SCROLL, vertical.state.phase)

        val balanced = ReaderGestureArbiter.sample(down(), sample(panX = 25f, panY = 25f), config(zoomed = true))
        assertEquals(
            ReaderGestureEffect.DelegateSingleScroll,
            balanced.effect,
            "a tie must go to the list; claiming it would make a diagonal drag feel broken",
        )

        val notZoomed = ReaderGestureArbiter.sample(down(), sample(panX = 25f, panY = 10f), config(zoomed = false))
        assertEquals(
            ReaderGestureEffect.DelegateSingleScroll,
            notZoomed.effect,
            "at fit the viewport must not hijack a pan at all, or the list cannot be scrolled",
        )
    }

    /**
     * A second pointer is an explicit request to transform, claimed immediately.
     *
     * This is the continuous counterpart of the pager rule, and it is why the arbiter observes at
     * `PointerEventPass.Initial`: a `LazyColumn` that consumed the first movement must not be able to
     * swallow the pinch before the viewport sees it.
     */
    @Test
    fun `a second pointer claims the gesture regardless of pan direction`() {
        val transition = ReaderGestureArbiter.sample(down(), sample(pointers = 2, zoom = 1.1f), config(zoomed = false))

        assertEquals(ReaderGesturePhase.TRANSFORM, transition.state.phase)
        assertTrue(
            transition.effect is ReaderGestureEffect.TransformStarted,
            "a pinch must be claimed even at fit, got ${transition.effect}",
        )
    }

    /**
     * The deleted `isWebtoonDoubleTap` is now the arbiter's tap phase plus `ReaderTapSequencer`.
     *
     * Asserted here as the *arbiter-level* guarantee that replaced it: a pointer-up inside slop
     * yields a `TapCandidate`, and anything beyond slop yields nothing at all. The timing and
     * distance halves of the old predicate are `ReaderTapSequencer`'s, already covered by its own
     * suite — re-asserting them here would be the second copy this change exists to remove.
     */
    @Test
    fun `a pointer-up inside slop is a tap candidate and one beyond slop is not`() {
        val inside = ReaderGestureArbiter.up(down(), 5f, 5f, slop)
        assertTrue(inside.effect is ReaderGestureEffect.TapCandidate, "a short press must be a tap candidate")

        val beyond = ReaderGestureArbiter.up(down(), 80f, 80f, slop)
        assertEquals(ReaderGestureEffect.None, beyond.effect, "a press that travelled past slop is not a tap")
    }

    /**
     * The zoom clamp the deleted `coerceWebtoonZoom` applied inline is now inside
     * `WebtoonZoomPolicy` via `applyZoom`, which is also what enforces min/max.
     *
     * Asserted through the reducer rather than the policy directly, because the reducer is the seam
     * that used to be missing: it is what turns an arbiter effect into a clamped transform. A
     * gesture that reports a huge `zoomChange` must not be able to push the strip past `max`.
     */
    @Test
    fun `a transform effect cannot push the scale past the configured maximum`() {
        val state = WebtoonZoomState(min = 1f, max = 4f)
        val committed = CommittedTransform(state.scale, state.offsetX)

        val outcome = state.reduceWebtoonEffect(
            ReaderGestureEffect.TransformStarted(
                documentRevision = "chapter-1",
                centroidX = 100f,
                centroidY = 100f,
                panX = 0f,
                panY = 0f,
                zoomChange = 1000f,
            ),
            committed = committed,
        )

        assertTrue(outcome is WebtoonGestureOutcome.TransformChanged, "the transform must be applied, got $outcome")
        assertTrue(
            state.scale <= 4f,
            "scale ${state.scale} exceeded the configured max of 4; the clamp moved out of the gesture path",
        )
        assertNotEquals(0f, state.scale, "a clamped-to-zero scale would blank the strip")
    }

    /**
     * The decisive `RDR-005` guarantee: the arbiter's *negative* decision reaches the continuous
     * viewport.
     *
     * `B-023` found this effect being swallowed in the pager adapter, and on this surface it was
     * worse — nothing ran the arbiter at all, so no decline could ever be reported. If this reducer
     * ever maps `DelegateSingleScroll` to `NoTransformChange`, the viewport silently loses the
     * ability to know it declined, and the regression is invisible to every other test here.
     */
    @Test
    fun `declining a gesture is reported rather than collapsed into no-change`() {
        val state = WebtoonZoomState(min = 1f, max = 4f)
        state.setViewportWidth(1000f)

        val outcome = state.reduceWebtoonEffect(
            ReaderGestureEffect.DelegateSingleScroll,
            committed = CommittedTransform(state.scale, state.offsetX),
        )

        assertEquals(
            WebtoonGestureOutcome.DelegatedToParent,
            outcome,
            "DelegateSingleScroll must stay distinguishable, or the viewport cannot learn it declined",
        )
    }

    /**
     * A delegated scroll must not discard a zoom the user already has.
     *
     * The decline is about *this* gesture, not about the transform. Resetting on delegation would
     * mean scrolling the list while zoomed silently dropped the user back to fit, which reads as
     * the reader randomly losing zoom.
     */
    @Test
    fun `a delegated scroll keeps the existing transform`() {
        val state = WebtoonZoomState(min = 1f, max = 4f)
        state.setViewportWidth(1000f)
        state.restore(scale = 2f, offsetX = 0f)

        state.reduceWebtoonEffect(
            ReaderGestureEffect.DelegateSingleScroll,
            committed = CommittedTransform(state.scale, state.offsetX),
        )

        assertEquals(2f, state.scale, 0.0001f, "delegation must not reset the zoom the user already applied")
    }

    /**
     * A cancelled gesture restores the committed transform.
     *
     * Without this, an abandoned swipe leaves the strip wherever the finger last reached — a
     * half-applied zoom with no way back. The null case is asserted too: a gesture cancelled before
     * any transform was committed has nothing to restore, and must not fabricate one.
     */
    @Test
    fun `a cancelled gesture restores the committed transform and tolerates none`() {
        val state = WebtoonZoomState(min = 1f, max = 4f)
        state.setViewportWidth(1000f)
        state.restore(scale = 2f, offsetX = 0f)
        state.restore(scale = 3.5f, offsetX = 40f)

        state.reduceWebtoonEffect(
            ReaderGestureEffect.GestureCancelled("chapter-1"),
            committed = CommittedTransform(2f, 0f),
        )
        assertEquals(2f, state.scale, 0.0001f, "a cancelled gesture must restore the committed scale")
        assertEquals(0f, state.offsetX, 0.0001f, "a cancelled gesture must restore the committed offset")

        val untouched = WebtoonZoomState(min = 1f, max = 4f)
        untouched.restoreCommitted(null)
        assertEquals(ZoomPolicy.FIT, untouched.scale, 0.0001f, "a null commit point leaves the state alone")
        assertFalse(untouched.scale.isNaN())
    }
}
