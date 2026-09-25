package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RenderPathPolicyTest {

    private val intrinsic = PixelSize(1080, 8000)
    private val crop = PixelRect(10, 20, 1070, 7980)

    private fun choose(
        verdict: AnimationVerdict = AnimationVerdict.Detected(false),
        regionDecodable: Boolean = true,
        cropRequested: Boolean = false,
        contentRect: PixelRect? = null,
        hasMergedBitmap: Boolean = false,
        hasBytes: Boolean = true,
        hasKnownSize: Boolean = true,
    ) = RenderPathPolicy.choose(
        verdict = verdict,
        regionDecodable = regionDecodable,
        cropRequested = cropRequested,
        contentRect = contentRect,
        hasMergedBitmap = hasMergedBitmap,
        hasBytes = hasBytes,
        hasKnownSize = hasKnownSize,
    )

    private fun blocker(
        verdict: AnimationVerdict = AnimationVerdict.Detected(false),
        regionDecodable: Boolean = true,
        cropRequested: Boolean = false,
        contentRect: PixelRect? = null,
        hasBytes: Boolean = true,
        hasKnownSize: Boolean = true,
    ) = RenderPathPolicy.blockerFor(
        verdict = verdict,
        regionDecodable = regionDecodable,
        cropRequested = cropRequested,
        contentRect = contentRect,
        hasBytes = hasBytes,
        hasKnownSize = hasKnownSize,
    )

    @Test
    fun `a plain static long strip is sliced`() {
        assertEquals(PageRenderPath.SLICED, choose())
        assertEquals(SlicingBlocker.NONE, blocker())
    }

    @Test
    fun `cropping does not disable slicing once the rect is known`() {
        // The defect: the old guard was `!cropBorders`, so turning crop on pushed a long strip
        // to a single full-height decode, which is the decode that exceeds the texture limit.
        assertEquals(
            PageRenderPath.SLICED,
            choose(cropRequested = true, contentRect = crop),
        )
    }

    @Test
    fun `slices are cut from the content rect not the whole image`() {
        val source = RenderPathPolicy.sliceSourceRect(intrinsic, crop)
        assertEquals(10, source.left)
        assertEquals(20, source.top)
        assertEquals(1070, source.right)
        assertEquals(7980, source.bottom)
    }

    @Test
    fun `an uncropped page slices from the full image`() {
        val source = RenderPathPolicy.sliceSourceRect(intrinsic, null)
        assertEquals(0, source.left)
        assertEquals(intrinsic.width, source.right)
        assertEquals(intrinsic.height, source.bottom)
    }

    @Test
    fun `cropped slices tile exactly the cropped region`() {
        // Slicing the content rect is what makes the slices cover the visible artwork with no
        // border strip and no gap.
        val source = RenderPathPolicy.sliceSourceRect(intrinsic, crop)
        val covered = (source.bottom - source.top).toFloat() / (intrinsic.height)
        assertEquals(7960f / 8000f, covered, 1e-4f)
    }

    @Test
    fun `crop requested but unmeasured blocks slicing until it is decided`() {
        assertEquals(
            PageRenderPath.WHOLE_IMAGE,
            choose(cropRequested = true, contentRect = null),
        )
        assertEquals(SlicingBlocker.CROP_UNDECIDED, blocker(cropRequested = true, contentRect = null))
    }

    @Test
    fun `crop not requested needs no rect`() {
        assertEquals(PageRenderPath.SLICED, choose(cropRequested = false, contentRect = null))
    }

    @Test
    fun `animated content is never sliced`() {
        assertEquals(
            PageRenderPath.WHOLE_IMAGE,
            choose(verdict = AnimationVerdict.Detected(true)),
        )
        assertEquals(
            SlicingBlocker.ANIMATED_CONTENT,
            blocker(verdict = AnimationVerdict.Detected(true)),
        )
    }

    @Test
    fun `animation outranks every other consideration`() {
        // Even with a known crop rect, a decodable format, and bytes in hand.
        assertEquals(
            SlicingBlocker.ANIMATED_CONTENT,
            blocker(
                verdict = AnimationVerdict.Detected(true),
                cropRequested = true,
                contentRect = crop,
            ),
        )
    }

    @Test
    fun `a non region decodable format is not reported as animation`() {
        // The old check folded JXL into the animated flag, so a static JXL page was blocked with
        // an animation reason.
        assertEquals(
            SlicingBlocker.FORMAT_NOT_REGION_DECODABLE,
            blocker(verdict = AnimationVerdict.Detected(false), regionDecodable = false),
        )
    }

    @Test
    fun `a format check outranks an indeterminate animation answer`() {
        assertEquals(
            SlicingBlocker.FORMAT_NOT_REGION_DECODABLE,
            blocker(verdict = AnimationVerdict.Indeterminate, regionDecodable = false),
        )
    }

    @Test
    fun `an indeterminate animation answer blocks slicing`() {
        // Guessing "static" would show only the first frame of an animated page.
        assertEquals(
            PageRenderPath.WHOLE_IMAGE,
            choose(verdict = AnimationVerdict.Indeterminate),
        )
        assertEquals(
            SlicingBlocker.ANIMATION_DETECTION_FAILED,
            blocker(verdict = AnimationVerdict.Indeterminate),
        )
    }

    @Test
    fun `a definitive static answer is not treated as indeterminate`() {
        assertEquals(PageRenderPath.SLICED, choose(verdict = AnimationVerdict.Detected(false)))
    }

    @Test
    fun `a merged bitmap short circuits to the merged path`() {
        assertEquals(
            PageRenderPath.MERGED_BITMAP,
            choose(hasMergedBitmap = true),
        )
    }

    @Test
    fun `a merged bitmap wins even for animated content`() {
        assertEquals(
            PageRenderPath.MERGED_BITMAP,
            choose(verdict = AnimationVerdict.Detected(true), hasMergedBitmap = true),
        )
    }

    @Test
    fun `missing bytes block slicing with a specific reason`() {
        assertEquals(PageRenderPath.WHOLE_IMAGE, choose(hasBytes = false))
        assertEquals(SlicingBlocker.NO_BYTES, blocker(hasBytes = false))
    }

    @Test
    fun `unknown size blocks slicing with a specific reason`() {
        assertEquals(PageRenderPath.WHOLE_IMAGE, choose(hasKnownSize = false))
        assertEquals(SlicingBlocker.UNKNOWN_SIZE, blocker(hasKnownSize = false))
    }

    @Test
    fun `every combination yields a render path`() {
        // Totality: no input combination may fall through to an undefined result.
        val verdicts = listOf(
            AnimationVerdict.Detected(true),
            AnimationVerdict.Detected(false),
            AnimationVerdict.Indeterminate,
        )
        verdicts.forEach { verdict ->
            listOf(true, false).forEach { decodable ->
                listOf(false, true).forEach { cropRequested ->
                    listOf(null, crop).forEach { rect ->
                        listOf(false, true).forEach { merged ->
                            listOf(false, true).forEach { bytes ->
                                listOf(false, true).forEach { size ->
                                    val path = choose(verdict, decodable, cropRequested, rect, merged, bytes, size)
                                    assertTrue(path in PageRenderPath.entries)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `no path ever slices animated content`() {
        val verdicts = listOf(AnimationVerdict.Detected(true), AnimationVerdict.Indeterminate)
        verdicts.forEach { verdict ->
            listOf(true, false).forEach { decodable ->
                listOf(null, crop).forEach { rect ->
                    val path = choose(verdict, decodable, cropRequested = true, contentRect = rect)
                    assertFalse(path == PageRenderPath.SLICED, "verdict=$verdict decodable=$decodable")
                }
            }
        }
    }

    @Test
    fun `a slice rect inside the image is valid`() {
        assertTrue(RenderPathPolicy.isSliceRectValid(intrinsic, PixelRect(0, 0, 1080, 2000)))
    }

    @Test
    fun `a slice rect past the image edge is invalid`() {
        // Otherwise the decoder produces a transparent slice that reads as a rendering gap.
        assertFalse(RenderPathPolicy.isSliceRectValid(intrinsic, PixelRect(0, 0, 1081, 2000)))
        assertFalse(RenderPathPolicy.isSliceRectValid(intrinsic, PixelRect(0, 0, 1080, 8001)))
    }
}
