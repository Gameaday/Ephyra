package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimationPolicyTest {

    @Test
    fun `a detected animated page is blocked as animated`() {
        assertEquals(
            SlicingBlocker.ANIMATED_CONTENT,
            AnimationPolicy.classify(PageImageFormat.WEBP, regionDecodable = true, detectedAnimated = true),
        )
    }

    @Test
    fun `a detected static page with a decodable format is sliceable`() {
        assertEquals(
            SlicingBlocker.NONE,
            AnimationPolicy.classify(PageImageFormat.JPEG, regionDecodable = true, detectedAnimated = false),
        )
    }

    @Test
    fun `an undecided answer blocks rather than assuming static`() {
        // Guessing static on an animated page silently drops every frame but the first.
        assertEquals(
            SlicingBlocker.ANIMATION_DETECTION_FAILED,
            AnimationPolicy.classify(PageImageFormat.WEBP, regionDecodable = true, detectedAnimated = null),
        )
    }

    @Test
    fun `a progressive jpeg is not reported as animation`() {
        // The defect: JXL and progressive JPEG were folded into the same flag as animation.
        assertEquals(
            SlicingBlocker.FORMAT_NOT_REGION_DECODABLE,
            AnimationPolicy.classify(PageImageFormat.JPEG, regionDecodable = false, detectedAnimated = false),
        )
    }

    @Test
    fun `jxl is a format problem not an animation problem`() {
        assertEquals(
            SlicingBlocker.FORMAT_NOT_REGION_DECODABLE,
            AnimationPolicy.classify(PageImageFormat.JXL, regionDecodable = false, detectedAnimated = false),
        )
    }

    @Test
    fun `an animation answer outranks a format problem`() {
        assertEquals(
            SlicingBlocker.ANIMATED_CONTENT,
            AnimationPolicy.classify(PageImageFormat.JXL, regionDecodable = false, detectedAnimated = true),
        )
    }

    @Test
    fun `only webp gif and unknown formats can hold animation`() {
        assertTrue(AnimationPolicy.canHoldAnimation(PageImageFormat.WEBP))
        assertTrue(AnimationPolicy.canHoldAnimation(PageImageFormat.GIF))
        assertTrue(AnimationPolicy.canHoldAnimation(PageImageFormat.ANIMATED))
        assertTrue(AnimationPolicy.canHoldAnimation(PageImageFormat.UNSUPPORTED))
    }

    @Test
    fun `jpeg png and jxl are statically known to be static`() {
        // This is what lets a PNG skip the detection pass entirely.
        assertFalse(AnimationPolicy.canHoldAnimation(PageImageFormat.JPEG))
        assertFalse(AnimationPolicy.canHoldAnimation(PageImageFormat.PNG))
        assertFalse(AnimationPolicy.canHoldAnimation(PageImageFormat.JXL))
    }

    @Test
    fun `a detection failure maps back to an indeterminate verdict`() {
        // So a retry re-detects instead of caching a permanent "not animated" answer.
        assertEquals(
            AnimationVerdict.Indeterminate,
            AnimationPolicy.verdictFor(
                SlicingBlocker.ANIMATION_DETECTION_FAILED,
                formatSupportsAnimation = true,
            ),
        )
    }

    @Test
    fun `an animated verdict round trips`() {
        assertEquals(
            AnimationVerdict.Detected(true),
            AnimationPolicy.verdictFor(SlicingBlocker.ANIMATED_CONTENT, formatSupportsAnimation = true),
        )
    }

    @Test
    fun `a non animation blocker on a static format resolves to static`() {
        assertEquals(
            AnimationVerdict.Detected(false),
            AnimationPolicy.verdictFor(
                SlicingBlocker.FORMAT_NOT_REGION_DECODABLE,
                formatSupportsAnimation = false,
            ),
        )
    }

    @Test
    fun `a non animation blocker on an animation capable format stays indeterminate`() {
        // WebP can animate; a format-based block does not prove it is static.
        assertEquals(
            AnimationVerdict.Indeterminate,
            AnimationPolicy.verdictFor(
                SlicingBlocker.FORMAT_NOT_REGION_DECODABLE,
                formatSupportsAnimation = true,
            ),
        )
    }

    @Test
    fun `classify and the render policy agree on every animated verdict`() {
        listOf(PageImageFormat.WEBP, PageImageFormat.GIF, PageImageFormat.ANIMATED).forEach { format ->
            val blocker = AnimationPolicy.classify(format, regionDecodable = true, detectedAnimated = true)
            assertEquals(SlicingBlocker.ANIMATED_CONTENT, blocker, "format=$format")
        }
    }
}
