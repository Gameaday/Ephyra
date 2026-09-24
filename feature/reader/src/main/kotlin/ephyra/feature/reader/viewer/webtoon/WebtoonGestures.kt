package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.util.fastAny
import ephyra.feature.reader.viewer.zoom.ZoomPolicy
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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

internal fun shouldClaimWebtoonHorizontalPan(
    accumulatedPan: Offset,
    locksInteraction: Boolean,
    touchSlop: Float,
): Boolean {
    return locksInteraction &&
        kotlin.math.abs(accumulatedPan.x) >= touchSlop &&
        kotlin.math.abs(accumulatedPan.x) > kotlin.math.abs(accumulatedPan.y)
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
        val downPosition = down.position
        val touchSlop = viewConfiguration.touchSlop
        val longPressAt = System.currentTimeMillis() + viewConfiguration.longPressTimeoutMillis
        var accumulatedPan = Offset.Zero
        var transformStarted = false
        var wasMultiTouch = false

        while (true) {
            val event = try {
                val remaining = (longPressAt - System.currentTimeMillis()).coerceAtLeast(1L)
                withTimeout(remaining) { awaitPointerEvent() }
            } catch (_: TimeoutCancellationException) {
                if (!transformStarted && !wasMultiTouch && !ZoomPolicy.locksInteraction(getScale())) {
                    onLongPress()
                }
                break
            }
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount == 0) {
                val up = event.changes.firstOrNull { it.id == down.id }
                if (!transformStarted && !wasMultiTouch && up != null &&
                    (up.position - downPosition).getDistance() < touchSlop
                ) {
                    val now = System.currentTimeMillis()
                    if (isWebtoonDoubleTap(now, lastTapTime, up.position, lastTapOffset, touchSlop = touchSlop)) {
                        onDoubleTapToggle()
                        lastTapTime = 0L
                    } else {
                        lastTapTime = now
                        lastTapOffset = up.position
                    }
                }
                break
            }
            if (event.changes.fastAny { it.isConsumed }) break

            val panChange = event.calculatePan()
            accumulatedPan += panChange
            if (pressedCount >= 2) {
                // A second pointer is an explicit request to transform this strip. Claim it
                // immediately so the LazyColumn cannot consume the pinch as a vertical drag.
                wasMultiTouch = true
                transformStarted = true
                val zoomChange = event.calculateZoom()
                if (zoomChange != 1f || panChange.x != 0f) {
                    onZoom(coerceWebtoonZoom(getScale() * zoomChange, zoomMin, zoomMax), panChange.x)
                }
                event.changes.forEach { it.consume() }
            } else if (ZoomPolicy.locksInteraction(getScale())) {
                val horizontalIntent = shouldClaimWebtoonHorizontalPan(
                    accumulatedPan = accumulatedPan,
                    locksInteraction = ZoomPolicy.locksInteraction(getScale()),
                    touchSlop = touchSlop,
                )
                if (horizontalIntent) {
                    transformStarted = true
                    onZoom(getScale(), panChange.x)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}
