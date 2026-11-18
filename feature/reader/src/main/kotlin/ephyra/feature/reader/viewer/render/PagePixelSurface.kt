package ephyra.feature.reader.viewer.render

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import ephyra.domain.reader.media.PixelRect
import ephyra.domain.reader.media.SampleSize
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * How a slice column fills its parent. The one genuine difference between the reader modes.
 *
 * - [FILL_PARENT] — the caller has already reserved a box at the page's aspect ratio (the webtoon
 *   item), so slices take proportional `weight` and the column never changes the item's height.
 *   Using intrinsic heights here would resize the LazyColumn item and jump its siblings.
 * - [INTRINSIC] — the slices sit inside a vertical scroll with no reserved box (the pager page), so
 *   each takes its own height derived from its source rows and the display scale.
 */
enum class SliceSizing { FILL_PARENT, INTRINSIC }

/**
 * Renders a page as an exact partition of region-decoded slices — the one renderer behind every
 * reader mode.
 *
 * A page taller than the GPU texture ceiling cannot be drawn as a single `AsyncImage`: the renderer
 * downsamples it to fit `GL_MAX_TEXTURE_SIZE`, which is the blurry-strip defect. This region-decodes
 * the page into slices under that ceiling at the display width and stacks them, so every slice stays
 * sharp and no single oversized bitmap is ever allocated.
 *
 * It owns the whole decision and renders bare [Image]s with no zoom or gestures of its own, so a
 * host can wrap it in whatever transform it likes (the webtoon `WebtoonZoomSurface`, the pager's
 * `pagerZoomLayer`) and the stack scales as one unit.
 *
 * The decision itself is [WebtoonSlicePlanner]'s: the slice rect (crop-aware), the exact partition,
 * the region-decode sample with its hysteresis, and the `DecodePlan` that records why the strip is
 * sliced. This composable only executes that plan.
 *
 * [fallback] is drawn when the plan yields no slices (short page, animation, non-resident source, a
 * sample coarser than `SampleSize.EIGHT`) or when region decoding is unsupported/fails.
 * [decodingPlaceholder], when supplied, is drawn instead while the first decode is in flight — the
 * pager passes a spinner, because the fallback's single oversized bitmap is exactly what this avoids.
 *
 * [pageKey] must be stable per page (chapter + index): it scopes the sample‑continuity state, so a
 * recycled composition for a *different* page cannot inherit the previous page's sample.
 */
@Composable
fun PagePixelSurface(
    pageKey: String,
    pageNumber: Int,
    bytes: ByteArray,
    metadata: PageMetadata,
    source: PageSource,
    renderPath: PageRenderPath,
    targetWidthPx: Int,
    viewportHeightPx: Int?,
    sizing: SliceSizing,
    modifier: Modifier = Modifier,
    decodingPlaceholder: (@Composable () -> Unit)? = null,
    fallback: @Composable () -> Unit,
) {
    val targetWidth = targetWidthPx.coerceAtLeast(1)
    val viewportHeight = viewportHeightPx?.coerceAtLeast(1)

    // Sample continuity across re-plans (rotation, viewport change): the planner's deadband keeps
    // the previous region sample while it is still legal for the new demand, so a small width change
    // does not re-decode every slice. Null until the first plan. Deliberately not a key of the plan
    // below: it is an *input* the next plan reads, and making it a key would re-plan twice.
    var previousSample by remember(pageKey) { mutableStateOf<SampleSize?>(null) }

    val plan = remember(pageKey, metadata, source, renderPath, targetWidth, viewportHeight) {
        WebtoonSlicePlanner.plan(
            metadata = metadata,
            source = source,
            renderPath = renderPath,
            targetWidthPx = targetWidth,
            viewportHeightPx = viewportHeight,
            maxTextureSize = ImageUtil.hardwareBitmapThreshold,
            previousSample = previousSample,
        )
    }
    LaunchedEffect(plan.sampleSize) { previousSample = plan.sampleSize }

    val slices = plan.slices
    if (slices.isNullOrEmpty()) {
        fallback()
        return
    }

    // Describe the exact tiling (not bytes) so a hash collision can never reuse geometry.
    val sliceKey = remember(slices) { slices.joinToString(";") { "${it.top}-${it.bottom}" } }

    val decoded = rememberDecodedSlices(
        pageKey = pageKey,
        targetWidth = targetWidth,
        sliceKey = sliceKey,
        sampleSize = plan.sampleSize,
        bytes = bytes,
        sliceRect = plan.sliceRect,
        slices = slices,
    )

    if (decoded == null) {
        // Still decoding. The pager shows a spinner; the webtoon item shows its fallback, which
        // carries its own placeholder and keeps the item's reserved box filled.
        val placeholder = decodingPlaceholder
        if (placeholder != null) placeholder() else fallback()
        return
    }
    if (decoded.size != slices.size || decoded.any { it == null || it.isRecycled }) {
        // Region decode unsupported/failed, or a stale bitmap survived a revisit: the single-image
        // fallback rather than a partial or duplicated strip.
        fallback()
        return
    }

    val density = LocalDensity.current
    // Scale from the *planned* slice rect, not the intrinsic size: on a cropped page the artwork is
    // narrower than the source, and using the source width here would stretch every slice.
    val scale = targetWidth.toFloat() / plan.sliceRect.width
    Column(
        modifier = when (sizing) {
            SliceSizing.FILL_PARENT -> modifier.fillMaxSize()
            SliceSizing.INTRINSIC -> modifier.fillMaxWidth()
        },
    ) {
        slices.forEachIndexed { index, slice ->
            val bitmap = decoded[index] ?: return@forEachIndexed
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page $pageNumber part ${index + 1}/${slices.size}",
                contentScale = ContentScale.FillWidth,
                modifier = when (sizing) {
                    SliceSizing.FILL_PARENT ->
                        Modifier
                            .fillMaxWidth()
                            .weight(slice.srcHeight.coerceAtLeast(1).toFloat(), fill = true)
                    SliceSizing.INTRINSIC ->
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { (slice.srcHeight * scale).toDp() })
                },
            )
        }
    }
}

/**
 * Decode: all slices via one shared region decoder (one header parse), cooperatively cancellable so
 * flinging between sections abandons stale decodes on key change.
 *
 * The sample and the rect to slice come from the plan ([WebtoonSlicePlanner]), not from the decoder:
 * sample choice is a policy decision (width fit plus hysteresis), and the decoder only executes it.
 *
 * No manual `recycle()`: Compose may still hold an `asImageBitmap()` snapshot when the caller
 * disposes during a fast fling — recycling then crashes with "trying to use a recycled bitmap". ART
 * reclaims unreachable bitmaps, and slices are ceiling‑bounded so peak native use stays small.
 */
@Composable
private fun rememberDecodedSlices(
    pageKey: String,
    targetWidth: Int,
    sliceKey: String,
    sampleSize: SampleSize,
    bytes: ByteArray,
    sliceRect: PixelRect,
    slices: List<WebtoonSlicer.SliceRect>,
): List<Bitmap?>? {
    val decoded by produceState<List<Bitmap?>?>(
        initialValue = null,
        key1 = pageKey,
        key2 = "$targetWidth:$sliceKey:${sampleSize.inSampleSize}",
    ) {
        value = withIOContext {
            // produceState's block runs in a coroutine: capture its context once for the
            // cooperative isActive check (cannot call suspend currentCoroutineContext from inside
            // the non-suspend isActive lambda).
            val hostContext = currentCoroutineContext()
            WebtoonRegionDecoder.decodeSlices(bytes, sliceRect, slices, sampleSize) {
                hostContext.isActive
            }
        }
    }
    return decoded
}
