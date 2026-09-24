package ephyra.feature.reader.viewer.webtoon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ephyra.feature.reader.viewer.zoom.ZoomPolicy

/**
 * Shared horizontal zoom for a webtoon chapter. The state is shared across strips for a seamless
 * reading position, while the compositor scales X only so LazyColumn item geometry remains stable.
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
 * double-tap-zoom toggle ([zoomEnabled]). The state is shared by the chapter, but it is
 * applied as a horizontal-only transform so LazyColumn item geometry never changes.
 */
@Composable
fun rememberWebtoonZoomState(
    chapterId: Long?,
    zoomEnabled: Boolean,
    max: Float = 4f,
): WebtoonZoomState {
    // The shared state is horizontal-only; a 1x floor keeps the viewport and list geometry stable.
    val min = 1f
    return remember(chapterId, zoomEnabled, min) {
        WebtoonZoomState(min = min, max = max)
    }
}
