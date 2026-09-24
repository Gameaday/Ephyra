package ephyra.feature.reader.viewer.webtoon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ephyra.feature.reader.viewer.zoom.ZoomPolicy

/**
 * Shared proportional zoom for a webtoon chapter. Each page item reports a scaled layout
 * footprint while its bitmap is painted through [androidx.compose.ui.graphics.graphicsLayer], so
 * adjacent strips remain contiguous and the reader can preserve its vertical document flow. Reset
 * per chapter.
 *
 * Shared across all sections of the chapter by design: one pinch updates every strip so moving
 * 1→5 (or back) keeps a consistent scale instead of per-item jumps.
 */
class WebtoonZoomState(
    val min: Float,
    val max: Float,
    initialScale: Float = ZoomPolicy.FIT,
    initialOffsetX: Float = 0f,
) {
    var scale by mutableFloatStateOf(initialScale)
        private set

    var offsetX by mutableFloatStateOf(initialOffsetX)
        private set

    private var viewportWidth = 0f

    fun setViewportWidth(width: Float) {
        viewportWidth = width.coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-maxOffsetX(), maxOffsetX())
    }

    fun applyZoom(newScale: Float, panX: Float) {
        scale = newScale.coerceIn(min, max)
        if (scale <= ZoomPolicy.FIT) {
            offsetX = 0f
        } else {
            val nextOffset = offsetX + panX
            offsetX = nextOffset.coerceIn(-maxOffsetX(), maxOffsetX())
        }
    }

    private fun maxOffsetX(): Float = ((viewportWidth * (scale - 1f)) / 2f).coerceAtLeast(0f)

    fun toggleFit() {
        if (scale > ZoomPolicy.ZOOM_GATE) {
            reset()
        } else {
            applyZoom(2f, 0f)
        }
    }

    fun reset() {
        scale = ZoomPolicy.FIT
        offsetX = 0f
    }
}

/**
 * Remembers a [WebtoonZoomState] scoped to the current chapter. Honors the reader's
 * double-tap-zoom toggle ([zoomEnabled]).
 */
@Composable
fun rememberWebtoonZoomState(
    chapterId: Long?,
    zoomEnabled: Boolean,
    max: Float = 4f,
): WebtoonZoomState {
    // Continuous vertical reading keeps the document's vertical layout authoritative. The layout
    // wrapper scales each page footprint proportionally, so a floor of 1x prevents negative gaps.
    val min = 1f
    return remember(chapterId, zoomEnabled, min) {
        WebtoonZoomState(min = min, max = max)
    }
}
