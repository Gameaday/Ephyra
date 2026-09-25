package ephyra.feature.reader.viewer.gesture

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.util.fastAny
import ephyra.domain.reader.gesture.ReaderGestureArbiter
import ephyra.domain.reader.gesture.ReaderGestureConfig
import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.gesture.ReaderGesturePhase
import ephyra.domain.reader.gesture.ReaderGesturePointerSample
import ephyra.domain.reader.gesture.ReaderGestureState
import ephyra.domain.reader.gesture.ReaderGestureTransition
import ephyra.domain.reader.gesture.ReaderTapEffect
import ephyra.domain.reader.gesture.ReaderTapSequencer
import ephyra.domain.reader.gesture.ReaderTapState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Thin Android pointer adapter for the pure reader gesture arbiter.
 *
 * This observes before parent navigation/scroll consumers, but consumes an event only after the
 * arbiter claims a transform. Reader policy remains in core:domain; this adapter only translates
 * Compose pointer events and dispatches semantic effects.
 */
suspend fun PointerInputScope.detectReaderGestures(
    documentRevision: () -> String,
    config: () -> ReaderGestureConfig,
    onEffect: (ReaderGestureEffect) -> Unit,
) {
    val tapScope = CoroutineScope(currentCoroutineContext())
    var tapState = ReaderTapState()
    var pendingTapJob: Job? = null
    var pendingTapToken: Long? = null

    fun cancelPendingTap() {
        val transition = ReaderTapSequencer.cancel(tapState)
        tapState = transition.state
        pendingTapJob?.cancel()
        pendingTapJob = null
        pendingTapToken = null
    }

    fun dispatchTapCandidate(revision: String, x: Float, y: Float) {
        if (documentRevision() != revision) {
            cancelPendingTap()
            return
        }
        val transition = ReaderTapSequencer.tap(
            state = tapState,
            x = x,
            y = y,
            nowMillis = SystemClock.uptimeMillis(),
            timeoutMillis = viewConfiguration.doubleTapTimeoutMillis.toLong().coerceAtLeast(1L),
            maxDistance = viewConfiguration.touchSlop * 3f,
        )
        tapState = transition.state
        when (val effect = transition.effect) {
            ReaderTapEffect.None,
            is ReaderTapEffect.CancelScheduledSingleTap,
            is ReaderTapEffect.SingleTap,
            -> Unit

            is ReaderTapEffect.DoubleTap -> {
                pendingTapJob?.cancel()
                pendingTapJob = null
                pendingTapToken = null
                onEffect(ReaderGestureEffect.DoubleTap(effect.x, effect.y))
            }

            is ReaderTapEffect.ScheduleSingleTap -> {
                if (effect.cancelToken != null && effect.cancelToken == pendingTapToken) {
                    pendingTapJob?.cancel()
                    pendingTapJob = null
                }
                pendingTapToken = effect.token
                pendingTapJob = tapScope.launch {
                    delay(effect.timeoutMillis)
                    if (pendingTapToken != effect.token) return@launch
                    if (documentRevision() != revision) {
                        cancelPendingTap()
                        return@launch
                    }
                    val timedOut = ReaderTapSequencer.timeout(tapState, effect.token)
                    tapState = timedOut.state
                    pendingTapJob = null
                    pendingTapToken = null
                    val timedOutEffect = timedOut.effect
                    if (timedOutEffect is ReaderTapEffect.SingleTap) {
                        onEffect(ReaderGestureEffect.SingleTap(timedOutEffect.x, timedOutEffect.y))
                    }
                }
            }
        }
    }

    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        val revision = documentRevision()
        var transition = ReaderGestureArbiter.down(
            state = ReaderGestureState(),
            documentRevision = revision,
            x = down.position.x,
            y = down.position.y,
        )
        var state = transition.state
        val longPressAt = SystemClock.uptimeMillis() + viewConfiguration.longPressTimeoutMillis
        var terminal = false

        try {
            while (true) {
                val event = try {
                    val remaining = (longPressAt - SystemClock.uptimeMillis()).coerceAtLeast(1L)
                    withTimeout(remaining) { awaitPointerEvent(PointerEventPass.Initial) }
                } catch (_: TimeoutCancellationException) {
                    cancelPendingTap()
                    transition = ReaderGestureArbiter.longPress(state)
                    state = transition.state
                    if (transition.effect != ReaderGestureEffect.None) onEffect(transition.effect)
                    terminal = true
                    break
                }

                val pressedPointers = event.changes.count { it.pressed }
                if (pressedPointers == 0) {
                    val up = event.changes.firstOrNull { it.id == down.id } ?: down
                    transition = ReaderGestureArbiter.up(
                        state = state,
                        x = up.position.x,
                        y = up.position.y,
                        touchSlop = viewConfiguration.touchSlop,
                    )
                    state = transition.state
                    when (val effect = transition.effect) {
                        ReaderGestureEffect.None -> Unit
                        is ReaderGestureEffect.TapCandidate -> {
                            dispatchTapCandidate(revision, effect.x, effect.y)
                        }
                        else -> onEffect(effect)
                    }
                    terminal = true
                    break
                }

                val centroid = event.calculateCentroid(useCurrent = false)
                val pan = event.calculatePan()
                transition = ReaderGestureArbiter.sample(
                    state = state,
                    sample = ReaderGesturePointerSample(
                        pressedPointers = pressedPointers,
                        centroidX = centroid.x,
                        centroidY = centroid.y,
                        panX = pan.x,
                        panY = pan.y,
                        zoomChange = event.calculateZoom(),
                        consumedByParent = event.changes.fastAny { it.isConsumed },
                    ),
                    config = config(),
                )
                state = transition.state
                when (val effect = transition.effect) {
                    ReaderGestureEffect.None -> Unit

                    ReaderGestureEffect.DelegateSingleScroll -> cancelPendingTap()

                    is ReaderGestureEffect.TapCandidate,
                    is ReaderGestureEffect.SingleTap,
                    is ReaderGestureEffect.DoubleTap,
                    -> error("Tap effects are emitted only by pointer-up and ReaderTapSequencer")

                    ReaderGestureEffect.LongPress,
                    is ReaderGestureEffect.TransformStarted,
                    is ReaderGestureEffect.TransformUpdated,
                    -> {
                        cancelPendingTap()
                        onEffect(effect)
                        if (effect !is ReaderGestureEffect.LongPress) {
                            event.changes.forEach { it.consume() }
                        }
                    }

                    is ReaderGestureEffect.TransformCommitted,
                    is ReaderGestureEffect.GestureCancelled,
                    -> {
                        cancelPendingTap()
                        onEffect(effect)
                    }
                }
            }
        } finally {
            if (!terminal && state.phase != ReaderGesturePhase.IDLE) {
                cancelPendingTap()
                onEffect(ReaderGestureArbiter.cancel(state).effect)
            }
        }
    }
}
