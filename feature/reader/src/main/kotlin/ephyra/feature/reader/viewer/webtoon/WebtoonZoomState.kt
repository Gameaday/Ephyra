package ephyra.feature.reader.viewer.webtoon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ephyra.feature.reader.viewer.zoom.ZoomPolicy

/**
 * Shared visual-only zoom for a webtoon chapter: scale + horizontal offset applied through
 * `graphicsLayer`, so pinch/double-tap resize content to fit without ever changing
 * LazyColumn layout size or scroll position. Reset per chapter.
 *
 * Shared across all sections of the chapter by design: one pinch updates every strip so
 * moving 1→5 (or back) keeps a consistent scale instead of per-item jumps.
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

    fun applyZoom(newScale: Float, panX: Float) {
        scale = newScale.coerceIn(min, max)
        // Re-center when docking back to 1x; otherwise track the pan.
        offsetX = if (scale <= ZoomPolicy.FIT) 0f else offsetX + panX
    }

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
 * double-tap-zoom toggle ([zoomEnabled]) and zoom-out floor ([zoomOutDisabled]).
 */
@Composable
fun rememberWebtoonZoomState(
    chapterId: Long?,
    zoomEnabled: Boolean,
    zoomOutDisabled: Boolean,
    max: Float = 4f,
): WebtoonZoomState {
    val min = if (zoomOutDisabled) 1f else 0.5f
    return remember(chapterId, zoomEnabled, min) {
        WebtoonZoomState(min = min, max = max)
    }
}
