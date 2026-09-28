package ephyra.feature.reader.viewer.webtoon

import ephyra.domain.reader.gesture.ReaderGestureEffect

/**
 * What the continuous viewport should do with one arbiter effect.
 *
 * Kept as its own type rather than returning a nullable zoom result, because the viewport has to
 * distinguish three outcomes that a `WebtoonZoomResult?` would collapse: "the transform changed",
 * "the arbiter declined, so the list may scroll" and "nothing happened". The middle one is the
 * whole point of the wiring.
 */
sealed interface WebtoonGestureOutcome {
    /**
     * The transform changed and the viewport keeps the gesture.
     *
     * [scrollCorrection] is handed to the list, which owns vertical position. The continuous reader
     * cannot consume vertical movement itself -- that is the `B-025` finding: `DocumentViewport`
     * owns `offset.y` while the `LazyColumn` also owns it, so giving the viewport the vertical axis
     * would leave two owners fighting. The correction is how the focal point stays under the same
     * screen position without the viewport taking an axis it does not own.
     */
    data class TransformChanged(val scrollCorrection: Float) : WebtoonGestureOutcome

    /**
     * The arbiter declined: the parent owns this gesture.
     *
     * This is the effect that was previously unreachable on this surface. Consuming nothing is what
     * lets the `LazyColumn` scroll, but the viewport must also *know*, so it can stop presenting
     * zoom affordances and stop suppressing the menu for a gesture it does not own.
     */
    data object DelegatedToParent : WebtoonGestureOutcome

    /** The effect does not concern the transform; the host handles it. */
    data object NoTransformChange : WebtoonGestureOutcome
}

/**
 * The transform an in-flight gesture will be restored to if it is abandoned.
 *
 * Held by the host rather than inside [WebtoonZoomState] because it is a property of a *gesture*,
 * not of the document: a second concurrent gesture must not overwrite the first's restore point,
 * and a value stored in the shared chapter state would.
 */
data class CommittedTransform(val scale: Float, val offsetX: Float)

/**
 * Reduces one arbiter effect into the continuous viewport's next outcome.
 *
 * A single function rather than a `when` at the call site, for the same reason
 * `PagerViewportState.reduceEffect` is one: so the mapping is testable without a Compose tree, and
 * so **no effect can be silently dropped**. Every branch of [ReaderGestureEffect] is handled
 * explicitly; anything unrecognised falls through to [WebtoonGestureOutcome.NoTransformChange]
 * rather than being ignored, so a new effect cannot quietly do nothing.
 *
 * The transform branches share one helper rather than repeating the arithmetic, because the
 * `TransformStarted` and `TransformUpdated` cases are identical here and a divergence between two
 * copies of focal maths is exactly the `DEF-002` defect waiting to happen.
 *
 * [onDoubleTap] is invoked rather than performed, because fit-vs-zoom is a page-level command and
 * this reducer is only about the transform. Taps and long presses are passed through to the host
 * for the same reason the pager reducer does not claim them: claiming them here would be the same
 * ownership error in a smaller box.
 */
fun WebtoonZoomState.reduceWebtoonEffect(
    effect: ReaderGestureEffect,
    committed: CommittedTransform?,
    onDoubleTap: () -> Unit = {},
): WebtoonGestureOutcome = when (effect) {
    is ReaderGestureEffect.TransformStarted ->
        applyTransform(effect.zoomChange, effect.panX, effect.centroidX, effect.centroidY)

    is ReaderGestureEffect.TransformUpdated ->
        applyTransform(effect.zoomChange, effect.panX, effect.centroidX, effect.centroidY)

    is ReaderGestureEffect.DoubleTap -> {
        toggleFit()
        onDoubleTap()
        WebtoonGestureOutcome.NoTransformChange
    }

    // A delegated scroll must NOT discard the transform: the user may be mid-zoom and scrolling the
    // list should not silently reset the zoom they already have. Same reasoning as
    // `PagerViewportState.reduceEffect`, and the reason this is not an error path.
    ReaderGestureEffect.DelegateSingleScroll -> WebtoonGestureOutcome.DelegatedToParent

    // A committed transform is the transform, so there is nothing to do. A cancelled one restores
    // the last committed value, which is why [committed] is threaded through rather than read from
    // this state: the in-flight transform is exactly what cancellation has to discard.
    is ReaderGestureEffect.TransformCommitted -> WebtoonGestureOutcome.NoTransformChange

    is ReaderGestureEffect.GestureCancelled -> {
        restoreCommitted(committed)
        WebtoonGestureOutcome.NoTransformChange
    }

    is ReaderGestureEffect.SingleTap,
    is ReaderGestureEffect.TapCandidate,
    ReaderGestureEffect.LongPress,
    ReaderGestureEffect.None,
    -> WebtoonGestureOutcome.NoTransformChange
}

/**
 * Applies one transform sample and reports the correction the list needs.
 *
 * `zoomChange` is a *factor* from the pointer sample, emitted unchanged by the arbiter, so it
 * composes with the current scale exactly as the old detector's `getScale() * zoomChange` did.
 * Converted to an absolute scale because [applyZoom] takes the absolute form and clamps in one
 * place. A non-positive or non-finite factor is treated as "no zoom this frame" rather than
 * propagated, because `applyZoom` divides by the current scale and a zero factor would collapse the
 * requested scale to zero and hand the policy a degenerate transform.
 */
private fun WebtoonZoomState.applyTransform(
    zoomChange: Float,
    panX: Float,
    focalX: Float,
    focalY: Float,
): WebtoonGestureOutcome {
    val factor = if (zoomChange.isFinite() && zoomChange > 0f) zoomChange else 1f
    val result = applyZoom(
        requestedScale = scale * factor,
        panX = panX,
        focalX = focalX,
        focalY = focalY,
    )
    return WebtoonGestureOutcome.TransformChanged(result.scrollCorrection)
}

/**
 * Restores the last committed transform, discarding whatever an abandoned gesture left behind.
 *
 * [committed] is nullable because a gesture can be cancelled before any transform was ever committed
 * -- the user pressed and released without the arbiter ever starting a transform. In that case there
 * is nothing to restore and the current value is left alone, which is correct: it was never modified.
 */
fun WebtoonZoomState.restoreCommitted(committed: CommittedTransform?) {
    if (committed == null) return
    restore(scale = committed.scale, offsetX = committed.offsetX)
}
