package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the three-way answer a byte probe can produce.
 *
 * The distinction that matters is between *"nobody checked and it could have been animated"* and
 * *"nobody checked because it cannot be"*. Collapsing the second into the first makes every static
 * PNG unsliceable; collapsing the first into the second slices an animated page down to its first
 * frame. Both were live in the webtoon reader before `verdictFromProbe` existed.
 */
class AnimationVerdictFromProbeTest {

    @Test
    fun `a probe that answered is the verdict`() {
        assertEquals(
            AnimationVerdict.Detected(true),
            AnimationPolicy.verdictFromProbe(true, PageImageFormat.WEBP),
        )
        assertEquals(
            AnimationVerdict.Detected(false),
            AnimationPolicy.verdictFromProbe(false, PageImageFormat.WEBP),
        )
    }

    /**
     * The case that a naive `detected?.let { Detected(it) } ?: Indeterminate` gets wrong: a
     * format that cannot animate is *known* static without a probe, and calling that unknown would
     * block slicing for every JPEG and PNG the reader ever shows.
     */
    @Test
    fun `a format that cannot animate is statically static, not unknown`() {
        for (format in listOf(PageImageFormat.JPEG, PageImageFormat.PNG, PageImageFormat.JXL)) {
            assertEquals(
                AnimationVerdict.Detected(false),
                AnimationPolicy.verdictFromProbe(null, format),
                "$format cannot hold animation, so an absent probe is not an absence of knowledge",
            )
        }
    }

    /**
     * The dangerous one. A format that *could* hold animation and whose probe did not answer is the
     * only genuinely unknown case, and treating it as static is what produced a strip showing one
     * frame of an animated page.
     */
    @Test
    fun `an unanswered probe on an animatable format is indeterminate`() {
        for (format in listOf(PageImageFormat.WEBP, PageImageFormat.GIF, PageImageFormat.ANIMATED)) {
            assertEquals(
                AnimationVerdict.Indeterminate,
                AnimationPolicy.verdictFromProbe(null, format),
                "$format could hold animation, so an absent probe must block rather than assume",
            )
        }
    }

    /**
     * The defect this whole exercise exists for: a static JXL page. JXL cannot be region-decoded,
     * which is a *format* fact, so it must be blocked for that reason and reported as static --
     * not as animated, which is what folding the two checks into one boolean did.
     */
    @Test
    fun `a static JXL page is static and blocked for its format, not for animation`() {
        val verdict = AnimationPolicy.verdictFromProbe(null, PageImageFormat.JXL)

        assertEquals(AnimationVerdict.Detected(false), verdict)
        assertEquals(
            SlicingBlocker.FORMAT_NOT_REGION_DECODABLE,
            RenderPathPolicy.blockerFor(
                verdict = verdict,
                regionDecodable = PageImageFormat.JXL.supportsRegionDecode(),
                cropRequested = false,
                contentRect = null,
                hasBytes = true,
                hasKnownSize = true,
            ),
            "the reason reported must be the format, so a static page is not labelled animated",
        )
    }

    @Test
    fun `an animated page is blocked for animation whatever the format can do`() {
        val blocker = RenderPathPolicy.blockerFor(
            verdict = AnimationVerdict.Detected(true),
            regionDecodable = PageImageFormat.WEBP.supportsRegionDecode(),
            cropRequested = false,
            contentRect = null,
            hasBytes = true,
            hasKnownSize = true,
        )
        assertEquals(SlicingBlocker.ANIMATED_CONTENT, blocker)
    }

    @Test
    fun `an unknown verdict blocks slicing`() {
        val blocker = RenderPathPolicy.blockerFor(
            verdict = AnimationVerdict.Indeterminate,
            regionDecodable = true,
            cropRequested = false,
            contentRect = null,
            hasBytes = true,
            hasKnownSize = true,
        )
        assertEquals(SlicingBlocker.ANIMATION_DETECTION_FAILED, blocker)
    }
}
