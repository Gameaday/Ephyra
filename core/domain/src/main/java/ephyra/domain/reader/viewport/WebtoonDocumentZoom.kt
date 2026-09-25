package ephyra.domain.reader.viewport

/**
 * Document-space zoom for the continuous reader.
 *
 * The continuous reader previously applied its zoom **per LazyColumn item**, scaling X only and
 * leaving Y at 1. Three defects followed from that placement, and all three are reported symptoms:
 *
 *  1. Pinch only widened the strip, which is exactly the report that webtoon zoom "primarily
 *     widens content". The vertical axis was never scaled at all.
 *  2. The gesture centroid was never passed to the zoom state, so even the horizontal zoom was not
 *     anchored to the fingers; content slid away from the pinch instead of staying under it.
 *  3. Per-item transforms scale each item's painted bounds while `LazyColumn` retains the original
 *     unscaled layout, so a scaled item overflows its slot and neighbours overlap or gap. That is
 *     the reported "sliced content overlaps or destabilises".
 *
 * The fix is ownership, not tuning: **one** transform for the whole document, applied by the
 * viewport, with the list retaining document-space layout underneath. Each item is then painted
 * once, at its true document position, and per-item geometry can never disagree with layout.
 *
 * [WebtoonDocumentZoom] is that single transform. It is pure so the focal invariant and the scroll
 * compensation are unit tested rather than only reachable through a live composition.
 */
data class WebtoonDocumentZoom(
    val scale: Float,
    val offsetX: Float,
) {
    init {
        require(scale.isFinite() && scale > 0f) { "scale must be finite and positive" }
        require(offsetX.isFinite()) { "offsetX must be finite" }
    }

    companion object {
        val IDENTITY: WebtoonDocumentZoom = WebtoonDocumentZoom(1f, 0f)
    }
}

/** Result of applying a gesture to the continuous document. */
data class WebtoonZoomResult(
    val zoom: WebtoonDocumentZoom,
    /**
     * Correction to the list's scroll offset, in document pixels, needed to keep the focal point
     * under the same screen position after the scale change.
     *
     * The list retains document-space geometry, so its own scroll offset is unaffected by the
     * painted scale. Without this correction a pinch would zoom *around the list origin* instead
     * of around the user's fingers, and the strip would jump even though the maths is right.
     */
    val scrollCorrection: Float,
)

/**
 * Pure zoom arithmetic for the continuous document.
 *
 * Unlike [PagerZoomPolicy] this does not own vertical panning: the list owns vertical scroll
 * because it must own item measurement to keep layout honest. What it owns is scale, horizontal
 * pan, and the scroll correction that makes the scale focal.
 */
class WebtoonZoomPolicy(
    private val minScale: Float = 1f,
    private val maxScale: Float = 4f,
) {
    init {
        require(minScale > 0f) { "minScale must be positive" }
        require(maxScale >= minScale) { "maxScale must be at least minScale" }
    }

    /**
     * Applies a gesture to [current].
     *
     * [focalX]/[focalY] are the screen position of the gesture centroid. The horizontal focal is
     * absorbed into [WebtoonDocumentZoom.offsetX]; the vertical focal cannot be, because the list
     * owns the vertical scroll. It is returned as [WebtoonZoomResult.scrollCorrection] instead.
     *
     * The transform origin is top-start on the scroll container, so a document y at list scroll S
     * paints at `(documentY - S) * scale`. Holding a focal document point fixed across a scale
     * change from `s` to `s'` therefore requires the list to move by `focalY * (1/s - 1/s')`.
     */
    fun apply(
        current: WebtoonDocumentZoom,
        zoomFactor: Float,
        panX: Float,
        focalX: Float,
        focalY: Float,
        viewportWidth: Float,
    ): WebtoonZoomResult {
        require(zoomFactor.isFinite() && zoomFactor > 0f) { "zoomFactor must be finite and positive" }
        require(panX.isFinite()) { "panX must be finite" }
        require(focalY.isFinite()) { "focalY must be finite" }
        require(viewportWidth > 0f) { "viewportWidth must be positive" }

        val nextScale = (current.scale * zoomFactor).coerceIn(minScale, maxScale)

        // Document x under the focal, in unscaled units, with the container centred at document 0.
        val documentUnderFocalX = (focalX - current.offsetX) / current.scale
        val rawOffsetX = focalX - documentUnderFocalX * nextScale + panX

        val bounds = maxOffsetX(nextScale, viewportWidth)
        val nextOffsetX = if (nextScale <= minScale) {
            0f
        } else {
            rawOffsetX.coerceIn(-bounds, bounds)
        }

        // A no-op scale change needs no list movement, and a zero focal is exactly the top of the
        // viewport, which must not move.
        val scrollCorrection = if (nextScale == current.scale) {
            0f
        } else {
            focalY * (1f / current.scale - 1f / nextScale)
        }

        return WebtoonZoomResult(
            zoom = WebtoonDocumentZoom(nextScale, nextOffsetX),
            scrollCorrection = scrollCorrection,
        )
    }

    /**
     * Horizontal pan half-extent for a scale.
     *
     * Zero at fit scale: the strip exactly fills the viewport, so there is nothing to pan to.
     * This is what stops a zoomed strip being dragged until it is off screen entirely.
     */
    fun maxOffsetX(scale: Float, viewportWidth: Float): Float =
        ((viewportWidth * (scale - 1f)) / 2f).coerceAtLeast(0f)

    /**
     * Document-space height of a strip when painted at [scale].
     *
     * The list still measures the strip at its unscaled height, so the visible document window
     * shrinks as the user zooms in. Exposed so a viewport can reserve the correct scroll extent
     * rather than guessing that the whole document is always visible.
     */
    fun visibleDocumentHeight(viewportHeight: Float, scale: Float): Float =
        viewportHeight / scale
}
