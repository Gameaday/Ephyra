package ephyra.domain.reader.gesture

import kotlin.math.sqrt

/** Pending single-tap state. [nextToken] is monotonic and never reused within a viewport. */
data class ReaderTapState(
    val pendingToken: Long? = null,
    val pendingX: Float = 0f,
    val pendingY: Float = 0f,
    val pendingAtMillis: Long = 0L,
    val nextToken: Long = 1L,
)

sealed interface ReaderTapEffect {
    data object None : ReaderTapEffect
    data class ScheduleSingleTap(
        val token: Long,
        val timeoutMillis: Long,
        val cancelToken: Long?,
    ) : ReaderTapEffect
    data class CancelScheduledSingleTap(val token: Long) : ReaderTapEffect
    data class SingleTap(val x: Float, val y: Float) : ReaderTapEffect
    data class DoubleTap(val x: Float, val y: Float) : ReaderTapEffect
}

data class ReaderTapTransition(
    val state: ReaderTapState,
    val effect: ReaderTapEffect,
)

/**
 * Defers the first tap until the double-tap interval expires. Timer scheduling is an adapter
 * responsibility; eligibility and exact-once delivery are deterministic here.
 */
object ReaderTapSequencer {
    fun tap(
        state: ReaderTapState,
        x: Float,
        y: Float,
        nowMillis: Long,
        timeoutMillis: Long,
        maxDistance: Float,
    ): ReaderTapTransition {
        require(timeoutMillis > 0L) { "timeoutMillis must be positive" }
        require(maxDistance > 0f) { "maxDistance must be positive" }
        require(nowMillis >= 0L) { "nowMillis cannot be negative" }

        val pendingToken = state.pendingToken
        if (pendingToken != null) {
            val elapsed = nowMillis - state.pendingAtMillis
            val isSecondTap = elapsed in 0 until timeoutMillis && distance(
                state.pendingX,
                state.pendingY,
                x,
                y,
            ) <= maxDistance
            if (isSecondTap) {
                return ReaderTapTransition(
                    state = state.copy(pendingToken = null),
                    effect = ReaderTapEffect.DoubleTap(x, y),
                )
            }
        }

        val token = state.nextToken
        val next = state.copy(
            pendingToken = token,
            pendingX = x,
            pendingY = y,
            pendingAtMillis = nowMillis,
            nextToken = token + 1L,
        )
        val effect = ReaderTapEffect.ScheduleSingleTap(
            token = token,
            timeoutMillis = timeoutMillis,
            cancelToken = pendingToken,
        )
        return ReaderTapTransition(next, effect)
    }

    fun timeout(
        state: ReaderTapState,
        token: Long,
    ): ReaderTapTransition {
        if (state.pendingToken != token) {
            return ReaderTapTransition(state, ReaderTapEffect.None)
        }
        return ReaderTapTransition(
            state = state.copy(pendingToken = null),
            effect = ReaderTapEffect.SingleTap(state.pendingX, state.pendingY),
        )
    }

    fun cancel(state: ReaderTapState): ReaderTapTransition {
        val token = state.pendingToken ?: return ReaderTapTransition(state, ReaderTapEffect.None)
        return ReaderTapTransition(
            state = state.copy(pendingToken = null),
            effect = ReaderTapEffect.CancelScheduledSingleTap(token),
        )
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val x = x2 - x1
        val y = y2 - y1
        return sqrt(x * x + y * y)
    }
}
