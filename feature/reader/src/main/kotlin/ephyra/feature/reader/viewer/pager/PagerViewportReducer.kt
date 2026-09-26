package ephyra.feature.reader.viewer.pager

import ephyra.domain.reader.gesture.ReaderGestureEffect
import ephyra.domain.reader.viewport.PagerViewport
import ephyra.domain.reader.viewport.PagerViewportAction
import ephyra.domain.reader.viewport.PagerViewportState
import ephyra.domain.reader.viewport.PagerZoomTransform

/**
 * Reduces one arbiter effect into the next viewport state.
 *
 * A single function rather than a `when` at the call site, so the mapping is testable without a
 * Compose tree and so no effect can be silently dropped. Every branch of [ReaderGestureEffect] is
 * handled explicitly; an effect this reducer does not recognise would fall through to
 * [PagerViewportAction.NO_OP] rather than being ignored, so a new effect cannot quietly do nothing.
 *
 * `SingleTap`, `TapCandidate` and `LongPress` deliberately do not change the transform. They are
 * page-level commands: the viewport owns transform state and `ReaderSession` owns workflow state, so
 * claiming them here would be the same ownership error in a smaller box. They are passed through by
 * the host, not consumed.
 *
 * [committed] is the last transform the viewport settled on. A cancelled gesture restores it, which is
 * why it is threaded through rather than read from the state: the in-flight transform is exactly what
 * cancellation has to discard.
 */
fun PagerViewportState.reduceEffect(
    effect: ReaderGestureEffect,
    committed: PagerZoomTransform = transform,
): PagerViewportState {
    val action: PagerViewportAction =
        when (effect) {
            is ReaderGestureEffect.TransformStarted ->
                PagerViewport.onTransform(
                    state = this,
                    zoomChange = effect.zoomChange,
                    panX = effect.panX,
                    panY = effect.panY,
                    focalX = effect.centroidX,
                    focalY = effect.centroidY,
                )

            is ReaderGestureEffect.TransformUpdated ->
                PagerViewport.onTransform(
                    state = this,
                    zoomChange = effect.zoomChange,
                    panX = effect.panX,
                    panY = effect.panY,
                    focalX = effect.centroidX,
                    focalY = effect.centroidY,
                )

            is ReaderGestureEffect.DoubleTap ->
                PagerViewport.onDoubleTap(this, effect.x, effect.y)

            // Delegation releases the transform but keeps it: the user may be mid-zoom and a
            // delegated scroll must not discard the zoom they already have.
            ReaderGestureEffect.DelegateSingleScroll ->
                PagerViewportAction(transform, claimsGesture = false, changed = false)

            is ReaderGestureEffect.GestureCancelled ->
                PagerViewport.onTransformCancelled(this, committed)

            is ReaderGestureEffect.TransformCommitted ->
                PagerViewport.onTransformCommitted(this)

            is ReaderGestureEffect.SingleTap,
            is ReaderGestureEffect.TapCandidate,
            ReaderGestureEffect.LongPress,
            ReaderGestureEffect.None,
            -> return this
        }

    return copy(
        transform = action.transform,
        transforming = action.claimsGesture || (transforming && action.transform != transform),
    )
}
