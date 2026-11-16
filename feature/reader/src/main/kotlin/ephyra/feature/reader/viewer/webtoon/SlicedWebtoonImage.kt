package ephyra.feature.reader.viewer.webtoon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.ImageUtil
import ephyra.domain.reader.media.PageMetadata
import ephyra.domain.reader.media.PageRenderPath
import ephyra.domain.reader.media.PageSource
import ephyra.domain.reader.media.PixelRect
import ephyra.domain.reader.media.SampleSize
import ephyra.feature.reader.model.ReaderPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Renders a long webtoon strip as a pixel-exact column of full-resolution slices.
 *
 * A1 — the render decision arrives as the media tuple: [metadata] and [source] describe the
 * page, and [renderPath] is the decision RenderPathPolicy already made upstream. This composable
 * no longer re-derives a crop/animation guard of its own: [WebtoonSlicePlanner] turns the tuple
 * into concrete geometry (an exact partition of the slice rect) and a region decode sample,
 * against BitmapRegionDecoder. The composable renders the plan it was handed, so it cannot
 * disagree with the call site about what is sliceable.
 *
 * Each slice is region-decoded from [bytes] at screen width, so no global downscale is needed
 * and every slice stays under the GPU texture ceiling. Slices form an exact partition
 * ([WebtoonSlicer.computeSlices]): every source row is decoded and displayed exactly once.
 *
 * Calls [fallback] (the regular single-image path) when the plan yields no slices: short pages,
 * animated images, a non-resident source, or when region decoding fails / would need more than
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
    metadata: PageMetadata,
    source: PageSource,
    renderPath: PageRenderPath,
    targetWidthPx: Float,
    viewportHeightPx: Float?,
    fallback: @Composable () -> Unit,
) {
    val srcWidth = metadata.intrinsicSize.width
    val srcHeight = metadata.intrinsicSize.height
    val targetWidth = targetWidthPx.toInt().coerceAtLeast(1)
    val viewportHeight = viewportHeightPx?.toInt()

    // Stable per-page identity. Keyed on the bytes *instance* (referential compare, no
    // main-thread hashing of multi-MB strips): a fresh decode/retry yields a new array and
    // recomputes, while scroll revisits with the same cached instance stay stable instead of
    // reusing the previous page's rects/bitmaps via a recycled composition. The content rect
    // is part of the identity because it selects which pixels are decoded.
    val pageKey = remember(page, bytes, metadata.contentRect) {
        val crop = metadata.contentRect
            ?.let { "${it.left},${it.top},${it.right},${it.bottom}" }
            ?: "full"
        "${page.chapter.chapter.id}:${page.index}:${page.number}:${bytes.size}:$srcWidth:$srcHeight:$crop"
    }

    // Sample continuity across re-plans (rotation, viewport change): SampleSizePolicy's deadband
    // keeps the previous region sample while it is still legal for the new demand, so a small
    // width change does not re-decode every slice of the strip. Null until the first plan.
    var previousSample by remember(pageKey) { mutableStateOf<SampleSize?>(null) }

    val plan = remember(pageKey, targetWidth, viewportHeight, renderPath, source) {
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
    LaunchedEffect(plan.sampleSize) {
        previousSample = plan.sampleSize
    }

    val slices = plan.slices
    if (slices == null) {
        fallback()
        return
    }

    // Describe the exact tiling (not bytes) so a hash collision can never reuse geometry.
    val sliceKey = remember(slices) {
        slices.joinToString(";") { "${it.top}-${it.bottom}" }
    }

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
    // Seam-layout precondition: every slice decodes from the same rect width at the same
    // sample, so all bitmaps share one width and one horizontal scale. A foreign width means
    // a stale bitmap got in — single-image fallback rather than a mis-scaled strip.
    if (bitmaps.map { it!!.width }.distinct().size != 1) {
        fallback()
        return
    }

    // No spacing, padding, or background of our own: exact tiles must sit edge-to-edge so
    // the joints blend into whatever reader canvas sits behind (including the automatic
    // edge-sampled theme), rather than flashing a fixed surface bar if a GPU leaves a
    // sub-pixel seam. The strip fills the reserved item box exactly (see [SliceColumn]) so
    // the column height always equals the item's aspect box and siblings never jump.
    SliceColumn(pageNumber = page.number, slices = slices, bitmaps = bitmaps)
}

/**
 * Decode: all slices via one shared region decoder (one header parse), cooperatively
 * cancellable so flinging between sections abandons stale decodes on key change.
 *
 * The sample and the rect to slice come from the plan ([WebtoonSlicePlanner]), not from the
 * decoder: sample choice is a policy decision (width fit plus hysteresis), and the decoder
 * only executes it.
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
    sampleSize: SampleSize,
    bytes: ByteArray,
    sliceRect: PixelRect,
    slices: List<WebtoonSlicer.SliceRect>,
): List<android.graphics.Bitmap?>? {
    val decoded by produceState<List<android.graphics.Bitmap?>?>(
        initialValue = null,
        key1 = pageKey,
        key2 = "$targetWidth:$sliceKey:${sampleSize.inSampleSize}",
    ) {
        value = withIOContext {
            // produceState's block runs in a coroutine: capture its context once for the
            // cooperative isActive check (cannot call suspend currentCoroutineContext
            // from inside the non-suspend isActive lambda).
            val hostContext = currentCoroutineContext()
            WebtoonRegionDecoder.decodeSlices(bytes, sliceRect, slices, sampleSize) {
                hostContext.isActive
            }
        }
    }
    return decoded
}

/**
 * Pixel-exact seam layout: one canvas, one continuous scale, slices drawn at their
 * cumulative decoded-bitmap offsets ([WebtoonSeamLayout]).
 *
 * The previous renderer gave each slice a Compose `weight` share of the item box and drew
 * its bitmap with `ContentScale.FillWidth`: the height a slice was allocated (a rounded
 * share of the box, from SOURCE slice heights) and the height its bitmap needed at display
 * width (from the DECODED bitmap) were two independent roundings that could disagree by a
 * device pixel at every joint — the reported ~1px gap, worse when zoomed or on densities
 * where the roundings diverge. Here adjacent slices share ONE exact boundary (slice i's
 * bottom edge is slice i+1's top edge, the same number), so no rounding can open a gap,
 * duplicate a displayed row, or drop one:
 *
 *  - Zoom: the owning item's zoom transform scales this canvas uniformly; boundaries stay
 *    coincident at every zoom factor.
 *  - Resolution/density: layout depends only on the decoded bitmap heights and the resolved
 *    canvas size — no per-slice rounding against the box.
 *  - Format: placement consumes the bitmaps' ACTUAL decoded heights (inSampleSize rounding
 *    included), never the planned source heights.
 *
 * The vertical scale maps the decoded bitmaps' total height onto the reserved item box, so
 * the strip still fills the box exactly — the bottom edge lands on the box edge, and sibling
 * items never jump. Callers guarantee non-null, same-width bitmaps (checked upstream with a
 * whole-image fallback), so the `!!` here cannot fire.
 */
@Composable
private fun SliceColumn(
    pageNumber: Int,
    slices: List<WebtoonSlicer.SliceRect>,
    bitmaps: List<android.graphics.Bitmap?>,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Page $pageNumber, rendered in ${slices.size} parts"
            },
    ) {
        // displayHeight is only known inside the draw scope, so placement is computed here,
        // per draw: O(slices) arithmetic, deterministic for a given (bitmaps, size) pair, so
        // the seams stay put across redraws and zoom never re-rounds a boundary.
        val placements = WebtoonSeamLayout.place(
            bitmaps.map { it!!.height },
            size.height.toDouble(),
        )
        val bitmapWidth = bitmaps.first()!!.width
        val totalBitmapHeight = placements.sumOf { it.bitmapHeight }
        val sx = size.width / bitmapWidth
        val sy = size.height / totalBitmapHeight
        scale(sx, sy, pivot = Offset.Zero) {
            placements.forEach { placement ->
                drawImage(
                    image = bitmaps[placement.index]!!.asImageBitmap(),
                    dstOffset = IntOffset(0, placement.bitmapTop),
                )
            }
        }
    }
}
