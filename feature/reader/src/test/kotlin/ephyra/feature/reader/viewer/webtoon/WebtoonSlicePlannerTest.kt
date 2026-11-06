package ephyra.feature.reader.viewer.webtoon

import ephyra.domain.reader.media.AnimationKind
import ephyra.domain.reader.media.DecodePath
import ephyra.domain.reader.media.DecodeReason
import ephyra.domain.reader.media.PageImageFormat
import ephyra.domain.reader.media.PageMetadata
import ephyra.domain.reader.media.PageRenderPath
import ephyra.domain.reader.media.PageSource
import ephyra.domain.reader.media.PixelRect
import ephyra.domain.reader.media.PixelSize
import ephyra.domain.reader.media.SampleSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * E2 evidence for A1: the media tuple's wiring into the slice renderer is decided here, at a
 * pure seam, rather than only through a real composition with real bytes.
 */
class WebtoonSlicePlannerTest {

    private val tall = PageMetadata(
        intrinsicSize = PixelSize(800, 12000),
        animation = AnimationKind.STATIC,
        format = PageImageFormat.JPEG,
    )

    private fun plan(
        metadata: PageMetadata = tall,
        source: PageSource = PageSource.InMemory(150_000),
        renderPath: PageRenderPath = PageRenderPath.SLICED,
        targetWidthPx: Int = 1080,
        viewportHeightPx: Int? = 2000,
        maxTextureSize: Int = 4096,
        previousSample: SampleSize? = null,
    ) = WebtoonSlicePlanner.plan(
        metadata = metadata,
        source = source,
        renderPath = renderPath,
        targetWidthPx = targetWidthPx,
        viewportHeightPx = viewportHeightPx,
        maxTextureSize = maxTextureSize,
        previousSample = previousSample,
    )

    @Test
    fun `a long static strip produces an exact partition`() {
        // 800x12000 at 1080w => 16200 display px; ceiling min(4096, 2000*1.5) => 6 slices.
        val slices = plan().slices ?: fail("a long static strip must slice")
        assertEquals(6, slices.size)
        assertEquals(0, slices.first().top)
        assertEquals(12000, slices.last().bottom)
        slices.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
        assertEquals(12000, slices.sumOf { it.srcHeight })
        val scale = 1080.0 / 800
        slices.forEach { assertTrue(it.srcHeight * scale <= 3000 + 1e-6) }
    }

    @Test
    fun `a whole image render path never yields slices`() {
        assertNull(plan(renderPath = PageRenderPath.WHOLE_IMAGE).slices)
        assertNull(plan(renderPath = PageRenderPath.MERGED_BITMAP).slices)
        // The page-level decode plan is still recorded: the decision data travels even when
        // the answer is "do not slice".
        assertEquals(
            DecodeReason.EXCEEDS_TEXTURE_LIMIT,
            plan(renderPath = PageRenderPath.WHOLE_IMAGE).pageDecodePlan.reason,
        )
    }

    @Test
    fun `a non resident source never slices`() {
        assertNull(plan(source = PageSource.Remote("https://example.invalid/page.jpg")).slices)
    }

    @Test
    fun `a short page takes the whole image path`() {
        assertNull(
            plan(
                metadata = PageMetadata(PixelSize(800, 1500), AnimationKind.STATIC, PageImageFormat.JPEG),
            ).slices,
        )
    }

    @Test
    fun `slices tile the content rect once it is measured`() {
        val intrinsic = PixelSize(1080, 8000)
        val crop = PixelRect(10, 20, 1070, 7980)
        val result = plan(
            metadata = PageMetadata(intrinsic, AnimationKind.STATIC, PageImageFormat.JPEG, crop),
        )
        // The rect slices are cut from is the content rect, not the whole image.
        assertEquals(crop, result.sliceRect)
        val slices = result.slices ?: fail("a cropped long strip must slice")
        assertEquals(0, slices.first().top)
        assertEquals(7960, slices.last().bottom)
        slices.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
        assertEquals(7960, slices.sumOf { it.srcHeight })
    }

    @Test
    fun `a content rect past the image edge never slices`() {
        // Otherwise the decoder would produce transparent slices that read as rendering gaps.
        val inconsistent = PixelRect(0, 0, 1080, 8001)
        assertNull(
            plan(
                metadata = PageMetadata(
                    PixelSize(1080, 8000),
                    AnimationKind.STATIC,
                    PageImageFormat.JPEG,
                    inconsistent,
                ),
            ).slices,
        )
    }

    @Test
    fun `the page decode plan names why slicing exists`() {
        // A long strip's whole-page decode is the one that fails: it exceeds the texture limit.
        assertEquals(DecodePath.SOFTWARE, plan().pageDecodePlan.path)
        assertEquals(DecodeReason.EXCEEDS_TEXTURE_LIMIT, plan().pageDecodePlan.reason)
        // A short page fits hardware at its intrinsic size; no slicing needed.
        val short = plan(
            metadata = PageMetadata(PixelSize(800, 1500), AnimationKind.STATIC, PageImageFormat.JPEG),
        )
        assertEquals(DecodePath.HARDWARE, short.pageDecodePlan.path)
        assertEquals(DecodeReason.FITS_HARDWARE_LIMIT, short.pageDecodePlan.reason)
    }

    @Test
    fun `animated content is recorded in the decode plan and never slices`() {
        val animated = PageMetadata(PixelSize(800, 12000), AnimationKind.ANIMATED, PageImageFormat.GIF)
        // RenderPathPolicy chooses WHOLE_IMAGE for animation upstream; the planner honours it.
        val result = plan(metadata = animated, renderPath = PageRenderPath.WHOLE_IMAGE)
        assertNull(result.slices)
        assertEquals(DecodePath.SOFTWARE, result.pageDecodePlan.path)
        assertEquals(DecodeReason.ANIMATED_CONTENT, result.pageDecodePlan.reason)
    }

    @Test
    fun `the sample fits the display width exactly`() {
        // 800 -> ONE (decoding at 1/2 would be 400px, below the 1080px demand).
        assertEquals(SampleSize.ONE, plan(metadata = tall).sampleSize)
        // 2160 -> TWO (1080 decoded, exactly the demand; 1/4 would be 540).
        val wide2160 = PageMetadata(PixelSize(2160, 20000), AnimationKind.STATIC, PageImageFormat.JPEG)
        assertEquals(SampleSize.TWO, plan(metadata = wide2160).sampleSize)
        // 5000 -> FOUR (1250 decoded, still above the demand; 1/8 would be 625).
        val wide5000 = PageMetadata(PixelSize(5000, 30000), AnimationKind.STATIC, PageImageFormat.JPEG)
        assertEquals(SampleSize.FOUR, plan(metadata = wide5000).sampleSize)
        // Never decodes narrower than the demand when the source is wide enough to allow it.
        listOf(tall, wide2160, wide5000).forEach {
            val sample = plan(metadata = it).sampleSize
            if (sample.inSampleSize > 1) {
                assertTrue(it.intrinsicSize.width / sample.inSampleSize >= 1080)
            }
        }
    }

    @Test
    fun `refinement never coarsens past the width fit`() {
        // 1500px source at a 1080px demand: the fit is ONE (1500/2 = 750 is below the demand).
        // SampleSizePolicy alone answers TWO here — its ideal 1.5 lies outside ONE's deadband,
        // so a single step coarsens — and decoding 750px for a 1080px screen is exactly the
        // blurry-strip defect the clamp exists to prevent. Falsification: no clamp, TWO.
        val metadata = PageMetadata(PixelSize(1500, 20000), AnimationKind.STATIC, PageImageFormat.JPEG)
        assertEquals(
            SampleSize.ONE,
            plan(metadata = metadata, previousSample = SampleSize.ONE).sampleSize,
        )
        assertEquals(
            SampleSize.ONE,
            plan(metadata = metadata, previousSample = SampleSize.FOUR).sampleSize,
        )
    }

    @Test
    fun `a decisive demand growth moves the sample finer`() {
        // 2160px source: at 1080 the fit is TWO. The demand grows to 1200 (a wider viewport):
        // the fit is ONE now, and the previous sample is coarser than the fit — holding TWO
        // would decode 1080px for a 1200px demand, so the planner must follow the fit down.
        val metadata = PageMetadata(PixelSize(2160, 20000), AnimationKind.STATIC, PageImageFormat.JPEG)
        assertEquals(SampleSize.TWO, plan(metadata = metadata, targetWidthPx = 1080).sampleSize)
        assertEquals(
            SampleSize.ONE,
            plan(metadata = metadata, targetWidthPx = 1200, previousSample = SampleSize.TWO).sampleSize,
        )
    }

    @Test
    fun `a small demand change keeps a still legal sample`() {
        // 2160px source at a 1050px demand: the fit stays TWO and the previous TWO sits inside
        // the deadband — the sample is held, not recomputed past it. The hold only becomes
        // observable per-frame once demand is continuous (A4's pinch zoom); here it must at
        // least never drift.
        val metadata = PageMetadata(PixelSize(2160, 20000), AnimationKind.STATIC, PageImageFormat.JPEG)
        assertEquals(
            SampleSize.TWO,
            plan(metadata = metadata, targetWidthPx = 1050, previousSample = SampleSize.TWO).sampleSize,
        )
    }

    @Test
    fun `sample granularity exhaustion falls back to the whole image`() {
        // A 20000px-wide source needs a 1/16 sample to fit a 1080px demand; SampleSize stops at
        // EIGHT. Rather than decoding at a non-enum granularity, slicing is declined and the
        // whole-image path renders the page.
        val absurd = PageMetadata(PixelSize(20000, 90000), AnimationKind.STATIC, PageImageFormat.JPEG)
        assertNull(plan(metadata = absurd).slices)
    }
}
