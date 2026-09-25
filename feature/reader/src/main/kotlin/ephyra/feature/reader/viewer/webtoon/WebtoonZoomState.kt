package ephyra.feature.reader.viewer.webtoon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ephyra.domain.reader.viewport.ViewportSize
import ephyra.domain.reader.viewport.WebtoonDocumentZoom
import ephyra.domain.reader.viewport.WebtoonZoomPolicy
import ephyra.domain.reader.viewport.WebtoonZoomResult
import ephyra.feature.reader.viewer.zoom.ZoomPolicy

/**
 * Document-space zoom for a webtoon chapter, shared across the chapter's strips.
 *
 * The transform is owned by the chapter, not by an individual page item. When each item applied
 * its own `graphicsLayer`, a scaled item's painted bounds no longer matched the `LazyColumn` slot
 * it occupied, so neighbouring strips overlapped or gapped whenever the scale was not 1 -- the
 * reported "sliced content overlaps or destabilises". One transform for the whole document cannot
 * disagree with layout, because there is only one of it.
 *
 * Scale is isotropic: it applies to both axes, so a pinch zooms the strip rather than only
 * widening it. The previous X-only form is why webtoon pinch "primarily widened content".
 */
@Stable
class WebtoonZoomState(
    val min: Float,
    val max: Float,
    initialScale: Float = ZoomPolicy.FIT,
    initialOffsetX: Float = 0f,
) {
    private val policy = WebtoonZoomPolicy(minScale = min, maxScale = max)

    var scale by mutableFloatStateOf(initialScale)
        private set

    var offsetX by mutableFloatStateOf(initialOffsetX)
        private set

    private var viewportWidth = 0f

    /** Current document-space transform, for applying to the scroll container. */
    val documentZoom: WebtoonDocumentZoom
        get() = WebtoonDocumentZoom(scale, offsetX)

    fun setViewportWidth(width: Float) {
        viewportWidth = width.coerceAtLeast(0f)
        if (viewportWidth > 0f) {
            offsetX = offsetX.coerceIn(
                -policy.maxOffsetX(scale, viewportWidth),
                policy.maxOffsetX(scale, viewportWidth),
            )
        }
    }

    /**
     * Applies a gesture, returning the scroll correction the list needs to keep the focal point
     * under the same screen position. The caller owns the list, so it owns the correction.
     *
     * [requestedScale] is the absolute scale the gesture detector already clamped; it is converted
     * back to a factor so the focal maths happens in one place.
     */
    fun applyZoom(
        requestedScale: Float,
        panX: Float,
        focalX: Float,
        focalY: Float,
    ): WebtoonZoomResult {
        val current = documentZoom
        val factor = if (current.scale > 0f) requestedScale / current.scale else 1f
        val result = policy.apply(
            current = current,
            zoomFactor = factor,
            panX = panX,
            focalX = if (viewportWidth > 0f) focalX else (viewportWidth / 2f),
            focalY = focalY,
            viewportWidth = if (viewportWidth > 0f) viewportWidth else 1f,
        )
        scale = result.zoom.scale
        offsetX = result.zoom.offsetX
        return result
    }

    fun toggleFit() {
        if (scale > ZoomPolicy.ZOOM_GATE) {
            reset()
        } else {
            scale = 2f.coerceIn(min, max)
        }
    }

    fun reset() {
        scale = ZoomPolicy.FIT.coerceIn(min, max)
        offsetX = 0f
    }

    fun viewportSize(height: Float): ViewportSize = ViewportSize(viewportWidth, height)
}

/**
 * Remembers a [WebtoonZoomState] scoped to the current chapter. Honors the reader's
 * double-tap-zoom toggle ([zoomEnabled]). The state is shared by the chapter so the zoom survives
 * scrolling between strips.
 */
@Composable
fun rememberWebtoonZoomState(
    chapterId: Long?,
    zoomEnabled: Boolean,
    max: Float = 4f,
): WebtoonZoomState {
    val min = 1f
    return remember(chapterId, zoomEnabled, min) {
        WebtoonZoomState(min = min, max = max)
    }
}
