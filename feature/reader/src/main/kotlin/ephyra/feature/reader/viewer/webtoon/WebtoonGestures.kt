package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.util.fastAny
import ephyra.feature.reader.viewer.zoom.ZoomPolicy
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

/**
 * Pure tap-timing helper: `true` when [now] + [pos] form a double-tap with the previous tap.
 * Extracted for JVM unit tests (no Compose runtime needed).
 */
fun isWebtoonDoubleTap(
    now: Long,
    lastTapTime: Long,
    pos: Offset,
    lastPos: Offset,
    timeoutMs: Long = 350L,
    slopMultiplier: Float = 3f,
    touchSlop: Float,
): Boolean {
    return now - lastTapTime < timeoutMs &&
        (pos - lastPos).getDistance() < touchSlop * slopMultiplier
}

/** Pure zoom clamp: keeps [requested] inside [min]..[max]. Extracted for JVM unit tests. */
fun coerceWebtoonZoom(requested: Float, min: Float, max: Float): Float {
    return requested.coerceIn(min, max)
}

/**
 * Combined tap + pinch gesture detector for a webtoon page item.
 *
 * One detector chain owns the whole item so competing pointerInput blocks can never
 * double-apply scale or steal scroll: single-finger tap / double-tap (1x-fit ↔ 2x toggle)
 * / long-press are handled inline, and two-finger pinch rescales with horizontal pan.
 * Vertical scroll is never consumed so the surrounding LazyColumn keeps moving between
 * sections. All output is visual-only (graphicsLayer); layout size and scroll position
 * are untouched.
 */
suspend fun PointerInputScope.detectWebtoonGestures(
    zoomMin: Float,
    zoomMax: Float,
    getScale: () -> Float,
    onZoom: (Float, Float) -> Unit,
    onDoubleTapToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    var lastTapTime = 0L
    var lastTapOffset = Offset.Zero
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downPos = down.position
        var tapTimeout = false
        // Long-press and tap share the pager's timeout: a hold fires onLongPress and ends
        // the gesture; a quick release falls through to tap / double-tap handling.
        try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()
            }
        } catch (_: TimeoutCancellationException) {
            tapTimeout = true
        }
        if (tapTimeout && !ZoomPolicy.locksInteraction(getScale())) {
            onLongPress()
            return@awaitEachGesture
        }

        var pinchActive = false
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        do {
            val event = awaitPointerEvent()
            if (event.changes.fastAny { it.isConsumed }) break
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) pinchActive = true
            if (!pinchActive) {
                val up = event.changes.firstOrNull { !it.pressed }
                if (up != null) {
                    val tapOffset = up.position
                    val moved = (tapOffset - downPos).getDistance() > viewConfiguration.touchSlop
                    if (!moved && !tapTimeout) {
                        val now = System.currentTimeMillis()
                        if (isWebtoonDoubleTap(now, lastTapTime, tapOffset, lastTapOffset, touchSlop = touchSlop)) {
                            onDoubleTapToggle()
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            lastTapOffset = tapOffset
                        }
                    }
                    break
                }
                // Single finger held/moved: horizontal pan only while zoomed (vertical
                // belongs to the list); tapTimeout already ruled out long-press above.
                if (ZoomPolicy.locksInteraction(getScale())) {
                    val panX = event.calculatePan().x
                    if (abs(panX) > 0.5f) {
                        event.changes.forEach { it.consume() }
                        onZoom(getScale(), panX)
                    }
                }
                continue
            }
            // Pinch loop: rescale + horizontal pan only; vertical deltas stay with the list.
            val zoomChange = event.calculateZoom()
            val panX = event.calculatePan().x
            if (!pastTouchSlop) {
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                if (abs(zoomChange - 1f) * centroidSize < touchSlop && abs(panX) < touchSlop) {
                    if (!event.changes.fastAny { it.pressed }) break
                    continue
                }
                pastTouchSlop = true
            }
            if (zoomChange != 1f || panX != 0f) {
                event.changes.forEach { it.consume() }
                onZoom(coerceWebtoonZoom(getScale() * zoomChange, zoomMin, zoomMax), panX)
            }
            if (!event.changes.fastAny { it.pressed }) break
        } while (true)
    }
}
