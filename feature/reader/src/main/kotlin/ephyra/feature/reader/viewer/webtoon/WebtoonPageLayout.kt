package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import ephyra.feature.reader.model.ReaderPage

/**
 * Single layout contract shared by every webtoon item branch (merged / sliced / single /
 * loading): when dimensions are known the item reserves the final full-strip box up front,
 * and every branch renders *inside* that box. Content swaps can therefore never resize the
 * LazyColumn item or shift siblings when moving between sections.
 */
fun Modifier.webtoonItemBox(aspectRatio: Float?): Modifier {
    return if (aspectRatio != null) {
        this
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    } else {
        this.fillMaxWidth()
    }
}

/** Derives the item aspect from intrinsic dims, or `null` when still unknown. */
fun webtoonAspectRatio(dimensions: Pair<Int, Int>?): Float? {
    val (width, height) = dimensions ?: return null
    return if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null
}

/**
 * Write-once page dimensions: the first decoded dims win and later decodes (Coil success,
 * slice path, revisit) must not overwrite the size LazyColumn already laid out with —
 * that overwrite was the "item suddenly becomes huge / jumps" bug between sections.
 * Source dims never change on rotation; only the target width does.
 */
fun ReaderPage.recordDimensionsOnce(width: Int, height: Int) {
    if (width <= 0 || height <= 0) return
    if (this.width <= 0 || this.height <= 0) {
        this.width = width
        this.height = height
    }
}
