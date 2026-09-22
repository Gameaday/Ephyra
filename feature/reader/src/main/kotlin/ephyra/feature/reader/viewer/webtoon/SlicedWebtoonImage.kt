package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.feature.reader.model.ReaderPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Renders a long webtoon strip as a [Column] of full-resolution slices.
 *
 * Each slice is region-decoded from [bytes] at screen width, so no global downscale is needed
 * and every slice stays under the GPU texture ceiling. Slices form an exact partition
 * ([WebtoonSlicer.computeSlices]): every source row is decoded and displayed exactly once.
 *
 * Calls [fallback] (the regular single-image path) when slicing does not apply: short pages,
 * animated images, border-cropped pages, or when region decoding fails / would need more than
 * [WebtoonSlicer.MAX_SLICES] slices.
 *
 * Zoom and long-press gestures are handled by the owning page item — slices render bare
 * [Image]s with no pointerInput of their own so nested gesture detectors can never fight
 * the item-level pinch/double-tap handling (double-applied scale, stolen scroll).
 */
@Composable
fun SlicedWebtoonImage(
    page: ReaderPage,
    bytes: ByteArray,
    srcWidth: Int,
    srcHeight: Int,
    targetWidthPx: Float,
    viewportHeightPx: Float?,
    cropBorders: Boolean,
    isAnimated: Boolean,
    fallback: @Composable () -> Unit,
) {
    // Stable per-page identity. Keyed on the bytes *instance* (referential compare, no
    // main-thread hashing of multi-MB strips): a fresh decode/retry yields a new array and
    // recomputes, while scroll revisits with the same cached instance stay stable instead of
    // reusing the previous page's rects/bitmaps via a recycled composition.
    val pageKey = remember(page, bytes) {
        "${page.chapter.chapter.id}:${page.index}:${page.number}:${bytes.size}:$srcWidth:$srcHeight"
    }
    val targetWidth = targetWidthPx.toInt().coerceAtLeast(1)
    val viewportHeight = viewportHeightPx?.toInt()
    val slicingEnabled = !cropBorders && !isAnimated &&
        bytes.isNotEmpty() && srcWidth > 0 && srcHeight > 0 && targetWidth > 0

    val slices = rememberSlices(pageKey, srcWidth, srcHeight, targetWidth, viewportHeight, slicingEnabled)

    if (slices == null) {
        fallback()
        return
    }

    // Describe the exact tiling (not bytes) so a hash collision can never reuse geometry.
    val sliceKey = remember(slices) {
        slices.joinToString(";") { "${it.top}-${it.bottom}" }
    }

    val decoded = rememberDecodedSlices(pageKey, targetWidth, sliceKey, bytes, srcWidth, slices)

    if (decoded == null) {
        // Still decoding: render the fallback (single AsyncImage path with its own
        // placeholder) rather than a blank gap.
        fallback()
        return
    }
    val bitmaps = decoded!!
    if (bitmaps.size != slices.size || bitmaps.any { it == null || it.isRecycled }) {
        // Region decode unsupported/failed, or a stale bitmap survived a revisit:
        // single-image fallback rather than a partial/duplicated strip.
        fallback()
        return
    }

    // No spacing, padding, or background of our own: exact tiles must sit edge-to-edge so
    // the joints blend into whatever reader canvas sits behind (including the automatic
    // edge-sampled theme), rather than flashing a fixed surface bar if a GPU leaves a
    // sub-pixel seam. Slices fill the reserved item box proportionally (weight by source
    // height) so the column height always equals the item's aspect box — never wrapContent,
    // which would resize the LazyColumn item and jump siblings when moving between sections.
    SliceColumn(pageNumber = page.number, slices = slices, bitmaps = bitmaps)
}

/**
 * Geometry: exact tiling for a page, or `null` when slicing does not apply (caller renders
 * its single-image fallback). Pure remember wrapper so the layout stays dumb.
 */
@Composable
private fun rememberSlices(
    pageKey: String,
    srcWidth: Int,
    srcHeight: Int,
    targetWidth: Int,
    viewportHeight: Int?,
    slicingEnabled: Boolean,
): List<WebtoonSlicer.SliceRect>? {
    return remember(pageKey, targetWidth, viewportHeight, slicingEnabled) {
        if (!slicingEnabled) {
            null
        } else {
            val textureLimit = ImageUtil.hardwareBitmapThreshold
            if (!WebtoonSlicer.needsSlicing(srcWidth, srcHeight, targetWidth, textureLimit)) {
                null
            } else {
                val maxSlice = WebtoonSlicer.maxSliceDisplayPx(
                    textureLimitPx = textureLimit,
                    viewportHeightPx = viewportHeight,
                )
                WebtoonSlicer.computeSlices(srcWidth, srcHeight, targetWidth, maxSlice)
                    .ifEmpty { null }
            }
        }
    }
}

/**
 * Decode: all slices via one shared region decoder (one header parse), cooperatively
 * cancellable so flinging between sections abandons stale decodes on key change.
 *
 * No manual recycle(): Compose may still hold an asImageBitmap() snapshot when the caller
 * disposes during a fast fling — recycling then crashes with "trying to use a recycled
 * bitmap". ART reclaims unreachable bitmaps; slices are ceiling-bounded so peak native
 * use stays small.
 */
@Composable
private fun rememberDecodedSlices(
    pageKey: String,
    targetWidth: Int,
    sliceKey: String,
    bytes: ByteArray,
    srcWidth: Int,
    slices: List<WebtoonSlicer.SliceRect>,
): List<android.graphics.Bitmap?>? {
    val decoded by produceState<List<android.graphics.Bitmap?>?>(
        initialValue = null,
        key1 = pageKey,
        key2 = "$targetWidth:$sliceKey",
    ) {
        value = withIOContext {
            // produceState's block runs in a coroutine: capture its context once for the
            // cooperative isActive check (cannot call suspend currentCoroutineContext
            // from inside the non-suspend isActive lambda).
            val hostContext = currentCoroutineContext()
            WebtoonRegionDecoder.decodeSlices(bytes, srcWidth, slices, targetWidth) {
                hostContext.isActive
            }
        }
    }
    return decoded
}

/** Dumb layout: proportional weights fill the reserved item box edge-to-edge. */
@Composable
private fun SliceColumn(
    pageNumber: Int,
    slices: List<WebtoonSlicer.SliceRect>,
    bitmaps: List<android.graphics.Bitmap?>,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        slices.forEachIndexed { index, slice ->
            val bitmap = bitmaps[index] ?: return@forEachIndexed
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page $pageNumber part ${index + 1}/${slices.size}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(
                        slice.srcHeight.coerceAtLeast(1).toFloat(),
                        fill = true,
                    ),
            )
        }
    }
}
