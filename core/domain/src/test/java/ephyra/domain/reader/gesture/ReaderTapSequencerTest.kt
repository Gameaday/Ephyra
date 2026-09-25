package ephyra.domain.reader.gesture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderTapSequencerTest {
    private val timeout = 350L
    private val maxDistance = 40f

    @Test
    fun `first tap is deferred by a unique token`() {
        val transition = tap(ReaderTapState(), 10f, 20f, now = 100L)

        assertEquals(1L, transition.state.pendingToken)
        assertEquals(
            ReaderTapEffect.ScheduleSingleTap(1L, timeout, cancelToken = null),
            transition.effect,
        )
    }

    @Test
    fun `matching second tap cancels the pending single tap`() {
        val first = tap(ReaderTapState(), 10f, 20f, now = 100L)
        val second = tap(first.state, 12f, 22f, now = 300L)

        assertEquals(null, second.state.pendingToken)
        assertEquals(ReaderTapEffect.DoubleTap(12f, 22f), second.effect)
        assertEquals(
            ReaderTapEffect.None,
            ReaderTapSequencer.timeout(second.state, token = 1L).effect,
        )
    }

    @Test
    fun `only the current token can emit a single tap`() {
        val scheduled = tap(ReaderTapState(), 10f, 20f, now = 100L)
        val stale = ReaderTapSequencer.timeout(scheduled.state, token = 0L)

        assertEquals(scheduled.state, stale.state)
        assertEquals(ReaderTapEffect.None, stale.effect)
    }

    @Test
    fun `current timeout emits the original position exactly once`() {
        val scheduled = tap(ReaderTapState(), 10f, 20f, now = 100L)
        val delivered = ReaderTapSequencer.timeout(scheduled.state, token = 1L)
        val repeated = ReaderTapSequencer.timeout(delivered.state, token = 1L)

        assertEquals(null, delivered.state.pendingToken)
        assertEquals(ReaderTapEffect.SingleTap(10f, 20f), delivered.effect)
        assertEquals(ReaderTapEffect.None, repeated.effect)
    }

    @Test
    fun `slow or distant tap is a new pending tap and invalidates the old timer`() {
        val first = tap(ReaderTapState(), 10f, 20f, now = 100L)
        val slow = tap(first.state, 12f, 22f, now = 500L)
        assertEquals(2L, slow.state.pendingToken)
        assertEquals(
            ReaderTapEffect.ScheduleSingleTap(2L, timeout, cancelToken = 1L),
            slow.effect,
        )

        val distant = tap(slow.state, 200f, 200f, now = 600L)
        assertEquals(3L, distant.state.pendingToken)
        assertEquals(
            ReaderTapEffect.ScheduleSingleTap(3L, timeout, cancelToken = 2L),
            distant.effect,
        )
    }

    @Test
    fun `cancel invalidates exactly the pending token`() {
        val scheduled = tap(ReaderTapState(), 10f, 20f, now = 100L)
        val cancelled = ReaderTapSequencer.cancel(scheduled.state)
        val repeated = ReaderTapSequencer.cancel(cancelled.state)

        assertEquals(null, cancelled.state.pendingToken)
        assertEquals(ReaderTapEffect.CancelScheduledSingleTap(1L), cancelled.effect)
        assertEquals(ReaderTapEffect.None, repeated.effect)
    }

    private fun tap(
        state: ReaderTapState,
        x: Float,
        y: Float,
        now: Long,
    ): ReaderTapTransition = ReaderTapSequencer.tap(
        state = state,
        x = x,
        y = y,
        nowMillis = now,
        timeoutMillis = timeout,
        maxDistance = maxDistance,
    )
}
