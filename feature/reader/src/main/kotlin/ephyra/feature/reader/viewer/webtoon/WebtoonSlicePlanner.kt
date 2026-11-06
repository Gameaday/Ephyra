package ephyra.feature.reader.viewer.webtoon

import ephyra.domain.reader.media.DecodePlan
import ephyra.domain.reader.media.DecodePlanner
import ephyra.domain.reader.media.PageMetadata
import ephyra.domain.reader.media.PageRenderPath
import ephyra.domain.reader.media.PageSource
import ephyra.domain.reader.media.PixelRect
import ephyra.domain.reader.media.RenderPathPolicy
import ephyra.domain.reader.media.SampleSize
import ephyra.domain.reader.media.SampleSizePolicy

/**
 * The A1 seam between the media tuple and the slice renderer.
 *
 * ComposeWebtoonReader decides *whether* a page is sliceable with RenderPathPolicy (total and
 * tested, no inline conjunctions). This planner decides *how* a sliceable page is rendered: which
 * rectangle the slices tile, the exact partition of it (WebtoonSlicer), and the region decode
 * sample (SampleSizePolicy hysteresis over the width-fit rule). Everything here is pure — the
 * composable calls it inside remember, and the same decision is reachable from JVM tests.
 *
 * Each tuple member earns its place:
 *  - PageMetadata supplies the intrinsic size, animation and format; its contentRect (when one
 *    is measured) selects the rectangle slices are cut from via RenderPathPolicy.sliceSourceRect,
 *    which is what makes slicing crop-aware rather than crop-forbidden.
 *  - PageSource must be resident bytes: the slice decoder consumes the page's buffered bytes and
 *    never fetches. A non-resident source is a wiring error, not a fallback case.
 *  - PageRenderPath is the decision made upstream; a non-SLICED path yields no slices, so the
 *    composable renders its whole-image fallback without re-deriving anything.
 *  - DecodePlanner records the whole-page decode plan. For a long strip it names
 *    EXCEEDS_TEXTURE_LIMIT — the exact failure slicing exists to avoid — so the reason a strip
 *    is sliced travels with the slices instead of living in a reader's memory.
 *  - SampleSizePolicy owns the sample's hysteresis across re-plans: when the display demand
 *    changes (rotation, viewport change) the deadband keeps the previous sample while it is
 *    still legal, instead of re-decoding every slice on a boundary flicker.
 */
data class WebtoonSlicePlan(
    val metadata: PageMetadata,
    val source: PageSource,
    val pageDecodePlan: DecodePlan,
    /** Intrinsic-coordinate rectangle the slices tile; the content rect when cropping is known. */
    val sliceRect: PixelRect,
    /** Exact partition of [sliceRect], or null when the whole-image path must be taken. */
    val slices: List<WebtoonSlicer.SliceRect>?,
    /** Region decode sample for the current display demand. */
    val sampleSize: SampleSize,
)

object WebtoonSlicePlanner {

    private val samplePolicy = SampleSizePolicy()

    /**
     * Turns the tuple into one concrete slice-render decision.
     *
     * [previousSample] is the sample this page last decoded with (null on the first plan); it
     * seeds the hysteresis so a demand change does not churn the decode. It is never allowed to
     * override the width fit — see [refineSample].
     */
    fun plan(
        metadata: PageMetadata,
        source: PageSource,
        renderPath: PageRenderPath,
        targetWidthPx: Int,
        viewportHeightPx: Int?,
        maxTextureSize: Int,
        previousSample: SampleSize? = null,
    ): WebtoonSlicePlan {
        require(targetWidthPx > 0) { "targetWidthPx must be positive" }
        require(maxTextureSize > 0) { "maxTextureSize must be positive" }

        val pageDecodePlan = DecodePlanner.plan(metadata, maxTextureSize)
        val sliceRect = RenderPathPolicy.sliceSourceRect(metadata.intrinsicSize, metadata.contentRect)
        val fit = WebtoonRegionDecoder.sampleSizeForWidth(sliceRect.width, targetWidthPx)

        val slices = when {
            renderPath != PageRenderPath.SLICED -> null
            !source.isResident -> null
            !RenderPathPolicy.isSliceRectValid(metadata.intrinsicSize, sliceRect) -> null
            // SampleSize stops at EIGHT; a page needing a coarser sample to fit the demand is
            // outside the enum's granularity, and slicing is declined rather than decoding at
            // an arbitrary non-enum step.
            fit > SampleSize.EIGHT.inSampleSize -> null
            !WebtoonSlicer.needsSlicing(
                srcWidth = sliceRect.width,
                srcHeight = sliceRect.height,
                targetWidthPx = targetWidthPx,
                textureLimitPx = maxTextureSize,
            ) -> null
            else -> WebtoonSlicer.computeSlices(
                srcWidth = sliceRect.width,
                srcHeight = sliceRect.height,
                targetWidthPx = targetWidthPx,
                maxSliceDisplayPx = WebtoonSlicer.maxSliceDisplayPx(
                    textureLimitPx = maxTextureSize,
                    viewportHeightPx = viewportHeightPx,
                ),
            ).ifEmpty { null }
        }

        return WebtoonSlicePlan(
            metadata = metadata,
            source = source,
            pageDecodePlan = pageDecodePlan,
            sliceRect = sliceRect,
            slices = slices,
            sampleSize = refineSample(sliceRect, targetWidthPx, previousSample, fit),
        )
    }

    /**
     * The region decode sample for [sliceRect] displayed at [targetWidthPx].
     *
     * The width-fit rule (never decode narrower than the display width) is a hard one-sided
     * constraint: BitmapRegionDecoder only accepts powers of two, and a sample coarser than the
     * fit decodes a strip narrower than the screen and upscales it — the blurry-strip defect.
     * SampleSizePolicy hysteresis therefore refines *within* the fit: a deadband hold is kept
     * when it is still legal, and anything the policy would coarsen past the fit is clamped
     * back. Without the clamp the policy alone answers over-coarse for widths just above a
     * power-of-two boundary (a 1500px source at a 1080px demand: policy says TWO, fit says ONE).
     */
    private fun refineSample(
        sliceRect: PixelRect,
        targetWidthPx: Int,
        previousSample: SampleSize?,
        fit: Int,
    ): SampleSize {
        val fitSample = SampleSize.entries.lastOrNull { it.inSampleSize <= fit } ?: SampleSize.ONE
        if (previousSample == null) return fitSample
        val devicePixelsPerSourcePixel = targetWidthPx.toFloat() / sliceRect.width
        val refined = samplePolicy.sampleFor(previousSample, devicePixelsPerSourcePixel)
        return if (refined.inSampleSize > fitSample.inSampleSize) fitSample else refined
    }
}
