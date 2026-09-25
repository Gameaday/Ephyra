package ephyra.domain.reader.viewport

import kotlin.math.abs

/**
 * The pager's zoom transform: scale plus the translation that keeps the focal point still.
 *
 * The pager previously maintained `Animatable<Float>` scale and `Animatable<Offset>` pan that
 * were updated on every gesture and then never applied to any modifier, so a pinch changed
 * internal state that nothing rendered. The transform is computed here instead, as a value, so
 * that "was it applied" is a question a test can answer rather than a code-reading question.
 *
 * The focal point is the reason this is not just a multiply. A pinch reports both a zoom factor
 * and the centroid between the fingers. Scaling about the centre of the view instead of the
 * centroid makes the content drift away from the user's fingers, which is the single most common
 * reason a pinch feels broken. [next] anchors the content under the focal point exactly.
 */
data class PagerZoomTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    init {
        require(scale.isFinite() && scale > 0f) { "scale must be finite and positive" }
        require(offsetX.isFinite() && offsetY.isFinite()) { "offset must be finite" }
    }

    companion object {
        /** Identity: the page is at fit and centred. */
        val IDENTITY: PagerZoomTransform = PagerZoomTransform(1f, 0f, 0f)
    }
}

/**
 * Pure zoom/pan arithmetic for the paged reader.
 *
 * Both readers share the interaction thresholds in
 * [ephyra.domain.reader.viewport.ZoomPolicy], but only the pager needs a focal transform: a
 * single page is displayed at a time, so panning it is meaningful in both axes. The continuous
 * reader instead zooms the whole document (see [DocumentViewport]) and must never scale an
 * individual item, because LazyColumn retains the unscaled geometry and the painted bounds stop
 * matching the layout.
 */
class PagerZoomPolicy(
    private val minScale: Float = 1f,
    private val maxScale: Float = 5f,
) {
    init {
        require(minScale > 0f) { "minScale must be positive" }
        require(maxScale >= minScale) { "maxScale must be at least minScale" }
    }

    /**
     * Applies a gesture to [current].
     *
     * [zoom] is the incremental zoom factor for this event and [pan] the incremental drag, both in
     * pixels. [focal] is the centroid the gesture reported, in the same coordinate space as
     * [viewportSize].
     *
     * The focal point is held fixed: after scaling, the same document point stays under the same
     * finger. Pan bounds are recomputed from the resulting scale so the page can never be dragged
     * off screen, and at or below fit scale the offset returns to zero so a partial zoom-out does
     * not leave the page stuck off-centre.
     */
    fun next(
        current: PagerZoomTransform,
        zoom: Float,
        pan: Float = 0f,
        panY: Float = 0f,
        focal: PagerTransformPoint,
        viewportSize: ViewportSize,
    ): PagerZoomTransform {
        require(zoom.isFinite() && zoom > 0f) { "zoom must be finite and positive" }
        require(pan.isFinite() && panY.isFinite()) { "pan must be finite" }
        require(viewportSize.width > 0f && viewportSize.height > 0f) { "viewport must be positive" }

        val nextScale = (current.scale * zoom).coerceIn(minScale, maxScale)

        // Hold the focal point still. The transform origin is the viewport centre (the
        // graphicsLayer default), so a document point maps to screen as:
        //     screen = centre + (doc - centre) * scale + offset
        // Inverting that for the focal point gives the document point currently under the user's
        // fingers; applying the new scale and solving for the offset that puts it back under the
        // same finger is what makes a pinch feel attached to the fingers. Scaling about the centre
        // instead, or ignoring the focal point entirely, makes content slide away from them.
        val centreX = viewportSize.width / 2f
        val centreY = viewportSize.height / 2f
        val docX = centreX + (focal.x - centreX - current.offsetX) / current.scale
        val docY = centreY + (focal.y - centreY - current.offsetY) / current.scale

        val nextXRaw = focal.x - centreX - (docX - centreX) * nextScale + pan
        val nextYRaw = focal.y - centreY - (docY - centreY) * nextScale + panY
        val (maxX, maxY) = panBounds(nextScale, viewportSize)

        val nextX: Float
        val nextY: Float
        if (nextScale <= minScale) {
            // At fit scale the page is fully visible, so any residual offset is by definition out
            // of bounds. Snapping here also makes a zoom-out to exactly 1.0 always re-centre.
            nextX = 0f
            nextY = 0f
        } else {
            nextX = nextXRaw.coerceIn(-maxX, maxX)
            nextY = nextYRaw.coerceIn(-maxY, maxY)
        }

        return PagerZoomTransform(nextScale, nextX, nextY)
    }

    /**
     * Half-extents the page may be dragged, derived from the scaled viewport.
     *
     * Using the scaled extent (not the unscaled one) is what stops the page being panned further
     * once it is zoomed in, which is how a page ends up draggable into empty space.
     */
    fun panBounds(scale: Float, viewportSize: ViewportSize): Pair<Float, Float> {
        val scaledWidth = viewportSize.width * scale
        val scaledHeight = viewportSize.height * scale
        return Pair(
            ((scaledWidth - viewportSize.width) / 2f).coerceAtLeast(0f),
            ((scaledHeight - viewportSize.height) / 2f).coerceAtLeast(0f),
        )
    }

    /** A double-tap zoom step, anchored on the tapped point and clamped like any other zoom. */
    fun doubleTap(
        current: PagerZoomTransform,
        tapped: PagerTransformPoint,
        viewportSize: ViewportSize,
        targetScale: Float,
    ): PagerZoomTransform = next(
        current = current,
        zoom = targetScale / current.scale,
        focal = tapped,
        viewportSize = viewportSize,
    )
}

/** A point in pager viewport coordinates, used as the zoom focal anchor. */
data class PagerTransformPoint(val x: Float, val y: Float)

/** True when two transforms are equal within a tolerance, for float-tolerant assertions. */
fun PagerZoomTransform.isCloseTo(other: PagerZoomTransform, epsilon: Float = 1e-3f): Boolean =
    abs(scale - other.scale) < epsilon &&
        abs(offsetX - other.offsetX) < epsilon &&
        abs(offsetY - other.offsetY) < epsilon
