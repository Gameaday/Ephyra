package ephyra.feature.reader.viewer.pager

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.domain.reader.media.PageMetadata
import ephyra.domain.reader.media.PageRenderPath
import ephyra.domain.reader.media.PageSource
import ephyra.feature.reader.viewer.render.WebtoonRegionDecoder
import ephyra.feature.reader.viewer.render.WebtoonSlicePlanner

/** Sentinel: the region decode has not produced a result yet (distinct from a null failure). */
private object Decoding

/**
 * Renders a **tall paged page** as an exact partition of region-decoded slices.
 *
 * A paged page taller than the GPU texture ceiling cannot be drawn as one `AsyncImage`: the
 * renderer downsamples it to fit `GL_MAX_TEXTURE_SIZE`, which is the blurry-strip defect. This
 * decodes the page into slices under that ceiling at the display width and stacks them, so every
 * slice stays sharp.
 *
 * It takes the **same media tuple** as the webtoon `SlicedWebtoonImage`, because *how* to slice is
 * not reader-specific. `WebtoonSlicePlanner` owns that decision: the slice rect (crop-aware, via
 * `RenderPathPolicy.sliceSourceRect`), the exact partition, the region-decode sample with its
 * hysteresis, and the `DecodePlan` recording why the strip is sliced. This composable only
 * executes the plan — it no longer re-derives one, which is what let the two readers disagree.
 *
 * Slices are sized by each slice's own source height (never `weight`) so the stack composes inside
 * the pager's `verticalScroll` and keeps one-page-one-item sizing.
 *
 * [fallback] is drawn when the plan yields no slices (short page, animation, non-resident source,
 * a sample coarser than `SampleSize.EIGHT`) or when region decoding is unsupported/fails. While
 * decoding, a spinner is shown rather than the fallback — the fallback's single oversized bitmap
 * is exactly what this avoids.
 *
 * This composable renders **no zoom or gestures of its own**: the pager's `pagerZoomLayer` wraps
 * it, so the whole stack scales as one unit (the same rule `SlicedWebtoonImage` follows).
 */
@Composable
fun PagedSliceColumn(
    bytes: ByteArray,
    metadata: PageMetadata,
    source: PageSource,
    renderPath: PageRenderPath,
    targetWidthPx: Int,
    viewportHeightPx: Int?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    // The real decision path, shared with the webtoon host. Keyed on every input: a rotation or a
    // viewport change must re-plan, and the planner's sample hysteresis then decides whether that
    // means a re-decode or a cheap hold of the previous legal sample.
    val plan = remember(bytes, metadata, source, renderPath, targetWidthPx, viewportHeightPx) {
        WebtoonSlicePlanner.plan(
            metadata = metadata,
            source = source,
            renderPath = renderPath,
            targetWidthPx = targetWidthPx,
            viewportHeightPx = viewportHeightPx,
            maxTextureSize = ImageUtil.hardwareBitmapThreshold,
        )
    }
    val slices = plan.slices
    if (slices.isNullOrEmpty()) {
        fallback()
        return
    }

    // `bytes` and `plan` are keys: a new page (new array instance) with the same dimensions must
    // re-decode, or the previous page's slices would be shown under the new page.
    val decoded by produceState<Any?>(Decoding, bytes, plan) {
        value = withIOContext {
            WebtoonRegionDecoder.decodeSlices(
                bytes = bytes,
                sliceRect = plan.sliceRect,
                slices = slices,
                sampleSize = plan.sampleSize,
            )
        }
    }
    when (val result = decoded) {
        Decoding -> CircularProgressIndicator(modifier = Modifier.size(48.dp))
        null -> fallback()
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            val bitmaps = result as List<Bitmap?>
            if (bitmaps.size != slices.size || bitmaps.any { it == null || it.isRecycled }) {
                fallback()
                return
            }
            val density = LocalDensity.current
            // Scale from the *planned* slice rect, not the intrinsic size: on a cropped page the
            // artwork is narrower than the source, and using the source width here would stretch
            // every slice horizontally.
            val scale = targetWidthPx.toFloat() / plan.sliceRect.width
            Column(modifier = modifier.fillMaxWidth()) {
                slices.forEachIndexed { index, slice ->
                    Image(
                        bitmap = bitmaps[index]!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { (slice.srcHeight * scale).toDp() }),
                    )
                }
            }
        }
    }
}
