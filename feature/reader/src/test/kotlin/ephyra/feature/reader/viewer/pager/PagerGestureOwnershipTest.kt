package ephyra.feature.reader.viewer.pager

import ephyra.domain.reader.gesture.ReaderGestureArbiter
import ephyra.domain.reader.gesture.ReaderGestureConfig
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.gesture.ReaderGesturePointerSample
import ephyra.domain.reader.gesture.ReaderGestureState
import ephyra.domain.reader.gesture.ReaderViewportMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Gesture ownership for the paged reader, asserted against the arbiter that now makes the decision.
 *
 * These three cases used to test `shouldClaimPagerTransform`, a hand-rolled predicate inside
 * `ZoomableMangaPage`. That function is deleted as of `RDR-004` stage 2, because keeping it would
 * mean two implementations of "who owns this gesture" — which is the ownership conflict the
 * reconstruction exists to remove.
 *
 * The behaviour is unchanged; only the owner moved. Asserting the same three properties against
 * [ReaderGestureArbiter] keeps the coverage where the policy now lives, and the arbiter's own suite
 * covers the wider state machine.
 */
class PagerGestureOwnershipTest {

    private val slop = 20f

    private fun config(meaningfullyZoomed: Boolean) = ReaderGestureConfig(
        viewportMode = ReaderViewportMode.PAGED,
        touchSlop = slop,
        meaningfullyZoomed = meaningfullyZoomed,
    )

    private fun started() = ReaderGestureArbiter.down(ReaderGestureState(), "page-1", 0f, 0f).state

    private fun sample(pressedPointers: Int = 1, panX: Float = 0f) = ReaderGesturePointerSample(
        pressedPointers = pressedPointers,
        centroidX = 0f,
        centroidY = 0f,
        panX = panX,
        panY = 0f,
        zoomChange = 1f,
        consumedByParent = false,
    )

    @Test
    fun `second pointer claims pinch immediately`() {
        val transition = ReaderGestureArbiter.sample(
            started(),
            sample(pressedPointers = 2),
            config(meaningfullyZoomed = false),
        )
        assertEquals(
            ReaderGestureEffect.TransformStarted("page-1", 0f, 0f, 0f, 0f, 1f),
            transition.effect,
        )
    }

    @Test
    fun `single pointer remains with pager at fit`() {
        val transition = ReaderGestureArbiter.sample(
            started(),
            sample(panX = slop * 5f),
            config(meaningfullyZoomed = false),
        )
        assertEquals(
            ReaderGestureEffect.DelegateSingleScroll,
            transition.effect,
            "At fit scale the pager owns the drag, so the arbiter must delegate rather than claim " +
                "a transform the viewport cannot use",
        )
    }

    @Test
    fun `single pointer pans only after slop while zoomed`() {
        val below = ReaderGestureArbiter.sample(
            started(),
            sample(panX = slop / 2f),
            config(meaningfullyZoomed = true),
        )
        assertEquals(
            ReaderGestureEffect.None,
            below.effect,
            "Movement inside slop must not claim the viewport, or an incidental touch steals the pan",
        )

        val above = ReaderGestureArbiter.sample(
            started(),
            sample(panX = slop * 1.25f),
            config(meaningfullyZoomed = true),
        )
        assertEquals(
            ReaderGestureEffect.TransformStarted("page-1", 0f, 0f, slop * 1.25f, 0f, 1f),
            above.effect,
        )
    }
}
