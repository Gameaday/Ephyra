package ephyra.domain.reader.viewport

/**
 * Transient transform state for the paged reader, owned by the viewport.
 *
 * `READER_ARCHITECTURE.md` assigns the viewport ownership of "transient transform and pointer state",
 * and this is that owner. It exists because the transform was previously implicit: the composable
 * held two `Animatable`s, applied them to a `graphicsLayer`, and re-derived the "is this zoomed
 * enough to lock interaction" question at three separate call sites. Each could disagree, and the
 * reported `DEF-001` is the extreme case — the transform was computed and then never drawn, which
 * no reader of the code could distinguish from correct code.
 *
 * Making the transform a value with an explicit owner turns "was it applied" into a question a test
 * answers, and gives the replacement viewport a single place where zoom lives.
 *
 * **This is state, not policy.** [PagerZoomPolicy] owns the arithmetic; [DocumentViewport] owns the
 * continuous equivalent; this owns only the paged case's current value and the lifecycle around it.
 */
data class PagerViewportState(
    /** The transform currently rendered. Identity means the page is at fit. */
    val transform: PagerZoomTransform = PagerZoomTransform.IDENTITY,
    /** Viewport size the transform is expressed in. Unset until the viewport is measured. */
    val viewportSize: ViewportSize? = null,
    /** `true` while a transform gesture owns the pointer stream. */
    val transforming: Boolean = false,
) {
    /** `true` once the viewport has been measured, which zoom arithmetic requires. */
    val isMeasured: Boolean get() = viewportSize != null

    /** Scale at or below fit, where page swiping belongs to the pager rather than the viewport. */
    val isAtFit: Boolean get() = transform.scale <= 1f
}

/**
 * What a viewport should do in response to a gesture effect.
 *
 * Returned rather than performed so the decision is inspectable in a test without a Compose tree.
 * The transform is always present rather than nullable, so a caller cannot accidentally treat
 * "nothing to do" as a transform to apply.
 */
data class PagerViewportAction(
    /** The transform the viewport should now render. */
    val transform: PagerZoomTransform,
    /** `true` when the viewport consumed the gesture and the pager must not also act on it. */
    val claimsGesture: Boolean = false,
    /** `true` when the transform changed as a result of this action. */
    val changed: Boolean = false,
) {
    companion object {
        /** A no-op: leaves the transform untouched and claims nothing. */
        val NO_OP: PagerViewportAction = PagerViewportAction(PagerZoomTransform.IDENTITY, false, false)
    }
}

/**
 * Interprets arbiter effects into viewport transform changes.
 *
 * Pure functions only — no Compose, no coroutines, no Android. The gesture *arbiter* already decides
 * ownership and [PagerZoomPolicy] already computes the arithmetic, so what remains is a small, total
 * mapping from effect to new state. Keeping it pure is what lets the `RDR-004` acceptance criteria be
 * tested on the JVM.
 */
object PagerViewport {
    /**
     * Applies a transform-starting or -updating gesture.
     *
     * `TransformStarted` and `TransformUpdated` carry the same shape, so they take the same path; the
     * difference matters only to the arbiter, not here.
     *
     * An unmeasured viewport returns [PagerViewportAction.NO_OP] rather than guessing a size. A
     * zero-sized viewport would divide by zero in the focal inversion inside [PagerZoomPolicy.next],
     * and substituting a plausible default would leave the focal anchor wrong by an amount that only
     * shows up on real hardware.
     */
    fun onTransform(
        state: PagerViewportState,
        zoomChange: Float,
        panX: Float,
        panY: Float,
        focalX: Float,
        focalY: Float,
    ): PagerViewportAction {
        val size = state.viewportSize ?: return PagerViewportAction.NO_OP
        val next = PagerZoomPolicy().next(
            current = state.transform,
            zoom = zoomChange,
            pan = panX,
            panY = panY,
            focal = PagerTransformPoint(focalX, focalY),
            viewportSize = size,
        )
        return PagerViewportAction(next, claimsGesture = true, changed = next != state.transform)
    }

    /** A double-tap zoom step anchored on the tapped point. Unmeasured viewports no-op. */
    fun onDoubleTap(
        state: PagerViewportState,
        tappedX: Float,
        tappedY: Float,
        targetScale: Float = 2.5f,
    ): PagerViewportAction {
        val size = state.viewportSize ?: return PagerViewportAction.NO_OP
        val next = PagerZoomPolicy().doubleTap(
            current = state.transform,
            tapped = PagerTransformPoint(tappedX, tappedY),
            viewportSize = size,
            targetScale = targetScale,
        )
        return PagerViewportAction(next, claimsGesture = true, changed = next != state.transform)
    }

    /**
     * Toggles between fit and a documented zoom level.
     *
     * Toggling back to fit re-centres, because at fit the page is fully visible and a residual offset
     * is by definition out of bounds. Preserving it would leave the page visibly off-centre after a
     * reset, which is the behaviour `ZoomPolicy` was introduced to stop.
     */
    fun onToggleFit(state: PagerViewportState, zoomLevel: Float = 2f): PagerViewportAction {
        val next =
            if (state.isAtFit) {
                state.transform.copy(scale = zoomLevel.coerceAtLeast(1f), offsetX = 0f, offsetY = 0f)
            } else {
                PagerZoomTransform.IDENTITY
            }
        return PagerViewportAction(next, claimsGesture = true, changed = next != state.transform)
    }

    /**
     * Records the measured viewport size, re-clamping any active transform.
     *
     * A page zoomed in a 1080px-wide viewport can be dragged past the edge of a 600px one. Without
     * re-clamping, the rendered content would sit partly outside the viewport with no gesture left to
     * correct it — for example after rotation or a window resize.
     */
    fun onViewportSizeChanged(
        state: PagerViewportState,
        size: ViewportSize,
    ): PagerViewportAction {
        val next = reClamp(state.transform, size)
        return PagerViewportAction(next, claimsGesture = false, changed = next != state.transform)
    }

    /**
     * Ends a transform, keeping the transform that was reached.
     *
     * `TransformCommitted` and `GestureCancelled` are deliberately separate: commit keeps the new
     * transform, cancel restores the last committed one. The gesture contract requires a cancelled
     * transform to restore, which is why this is not a boolean parameter.
     */
    fun onTransformCommitted(state: PagerViewportState): PagerViewportAction =
        PagerViewportAction(state.transform, claimsGesture = false, changed = false)

    /** Restores the last committed transform, discarding the in-flight one. */
    fun onTransformCancelled(
        state: PagerViewportState,
        committed: PagerZoomTransform,
    ): PagerViewportAction = PagerViewportAction(committed, claimsGesture = false, changed = true)

    /** A page or chapter change invalidates any transform: the new page starts at fit. */
    fun onDocumentChanged(): PagerViewportAction =
        PagerViewportAction(PagerZoomTransform.IDENTITY, claimsGesture = false, changed = true)

    /**
     * Whether the viewport should own a single-pointer pan at [scale].
     *
     * The threshold stays a parameter rather than a constant here because the shared value lives in
     * `feature:reader`'s `ZoomPolicy`, and `core:domain` must not depend on it. The composable used
     * to recompute this inline at several call sites, which is how the two readers could drift apart
     * on the lock-in behaviour that once trapped users at a 1.05x gate.
     */
    fun ownsPan(zoomLockThreshold: Float, scale: Float): Boolean = scale > zoomLockThreshold

    private fun reClamp(
        transform: PagerZoomTransform,
        size: ViewportSize,
    ): PagerZoomTransform {
        if (transform.scale <= 1f) return PagerZoomTransform.IDENTITY
        val (maxX, maxY) = PagerZoomPolicy().panBounds(transform.scale, size)
        return transform.copy(
            offsetX = transform.offsetX.coerceIn(-maxX, maxX),
            offsetY = transform.offsetY.coerceIn(-maxY, maxY),
        )
    }
}
