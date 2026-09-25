package ephyra.domain.reader.gesture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderGestureArbiterTest {
    private val slop = 20f
    private val fitConfig = ReaderGestureConfig(
        viewportMode = ReaderViewportMode.PAGED,
        touchSlop = slop,
        meaningfullyZoomed = false,
    )

    @Test
    fun `down starts a candidate without claiming input`() {
        val transition = ReaderGestureArbiter.down(ReaderGestureState(), "page-1", 10f, 20f)
        assertEquals(ReaderGesturePhase.CANDIDATE, transition.state.phase)
        assertEquals(ReaderGestureEffect.None, transition.effect)
    }

    @Test
    fun `movement below slop remains a tap candidate`() {
        val candidate = started().state
        val transition = ReaderGestureArbiter.sample(candidate, sample(panX = 5f), fitConfig)
        assertEquals(ReaderGesturePhase.CANDIDATE, transition.state.phase)
        assertEquals(ReaderGestureEffect.None, transition.effect)
    }

    @Test
    fun `fit movement delegates ordinary single scroll`() {
        val transition = ReaderGestureArbiter.sample(started().state, sample(panX = 25f), fitConfig)
        assertEquals(ReaderGesturePhase.SINGLE_SCROLL, transition.state.phase)
        assertEquals(ReaderGestureEffect.DelegateSingleScroll, transition.effect)
    }

    @Test
    fun `parent consumption delegates but does not end candidate recognition`() {
        val consumed = ReaderGestureArbiter.sample(
            started().state,
            sample(panX = 5f, consumed = true),
            fitConfig,
        )
        assertEquals(ReaderGesturePhase.SINGLE_SCROLL, consumed.state.phase)
        assertEquals(ReaderGestureEffect.DelegateSingleScroll, consumed.effect)

        val continued = ReaderGestureArbiter.sample(
            consumed.state,
            sample(panX = 30f),
            fitConfig.copy(meaningfullyZoomed = true),
        )
        assertEquals(ReaderGesturePhase.SINGLE_SCROLL, continued.state.phase)
        assertEquals(ReaderGestureEffect.DelegateSingleScroll, continued.effect)

        val pinch = ReaderGestureArbiter.sample(
            consumed.state,
            sample(pointers = 2, panX = 1f, zoom = 1.1f),
            fitConfig,
        )
        assertEquals(ReaderGesturePhase.TRANSFORM, pinch.state.phase)
        assertInstanceOf(ReaderGestureEffect.TransformStarted::class.java, pinch.effect)
    }

    @Test
    fun `paged meaningful zoom owns any one-finger pan after slop`() {
        val config = fitConfig.copy(meaningfullyZoomed = true)
        val transition = ReaderGestureArbiter.sample(started().state, sample(panY = 25f), config)
        assertEquals(ReaderGesturePhase.TRANSFORM, transition.state.phase)
        assertInstanceOf(ReaderGestureEffect.TransformStarted::class.java, transition.effect)
    }

    @Test
    fun `continuous document delegates dominant vertical pan but owns dominant horizontal pan`() {
        val config = ReaderGestureConfig(
            viewportMode = ReaderViewportMode.CONTINUOUS,
            touchSlop = slop,
            meaningfullyZoomed = true,
        )
        val vertical = ReaderGestureArbiter.sample(started().state, sample(panX = 5f, panY = 30f), config)
        assertEquals(ReaderGesturePhase.SINGLE_SCROLL, vertical.state.phase)
        assertEquals(ReaderGestureEffect.DelegateSingleScroll, vertical.effect)

        val current = ReaderGestureArbiter.down(ReaderGestureState(), "page-1", 0f, 0f).state
        val horizontal = ReaderGestureArbiter.sample(current, sample(panX = 30f, panY = 5f), config)
        assertEquals(ReaderGesturePhase.TRANSFORM, horizontal.state.phase)
    }

    @Test
    fun `transform continues after one pointer lifts and commits on final up`() {
        val start = ReaderGestureArbiter.sample(
            started().state,
            sample(pointers = 2, zoom = 1.2f),
            fitConfig,
        ).state
        val update = ReaderGestureArbiter.sample(
            start,
            sample(pointers = 1, panX = 12f, zoom = 1f),
            fitConfig,
        )
        assertEquals(ReaderGesturePhase.TRANSFORM, update.state.phase)
        assertInstanceOf(ReaderGestureEffect.TransformUpdated::class.java, update.effect)

        val committed = ReaderGestureArbiter.up(update.state, 0f, 0f, slop)
        assertEquals(ReaderGesturePhase.IDLE, committed.state.phase)
        assertEquals(ReaderGestureEffect.TransformCommitted("page-1"), committed.effect)
    }

    @Test
    fun `tap long press and cancellation are terminal semantic effects`() {
        val tap = ReaderGestureArbiter.up(started().state, 5f, 5f, slop)
        assertEquals(ReaderGestureEffect.TapCandidate(5f, 5f), tap.effect)

        val longPress = ReaderGestureArbiter.longPress(started().state)
        assertEquals(ReaderGestureEffect.LongPress, longPress.effect)
        assertEquals(ReaderGesturePhase.IDLE, longPress.state.phase)

        val cancelled = ReaderGestureArbiter.cancel(started().state)
        assertEquals(ReaderGestureEffect.GestureCancelled("page-1"), cancelled.effect)
        assertEquals(ReaderGesturePhase.IDLE, cancelled.state.phase)
    }

    @Test
    fun `long press cannot replace a claimed transform`() {
        val transform = ReaderGestureArbiter.sample(
            started().state,
            sample(pointers = 2, zoom = 1.1f),
            fitConfig,
        ).state
        val ignored = ReaderGestureArbiter.longPress(transform)
        assertEquals(ReaderGestureEffect.None, ignored.effect)
        assertTrue(ignored.state.wasMultiTouch)
    }

    private fun started(): ReaderGestureTransition =
        ReaderGestureArbiter.down(ReaderGestureState(), "page-1", 0f, 0f)

    private fun sample(
        pointers: Int = 1,
        panX: Float = 0f,
        panY: Float = 0f,
        zoom: Float = 1f,
        consumed: Boolean = false,
    ) = ReaderGesturePointerSample(
        pressedPointers = pointers,
        centroidX = 50f,
        centroidY = 50f,
        panX = panX,
        panY = panY,
        zoomChange = zoom,
        consumedByParent = consumed,
    )
}
