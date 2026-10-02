package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the format axis beside [AnimationPolicy.canHoldAnimation].
 *
 * The two are deliberately independent, and this is the test that says so: a format can be static
 * and not region-decodable, and a format can hold animation and be region-decodable. The reader
 * needs both answers and must not infer one from the other -- the old inline guard did exactly that,
 * folding JXL's "not region decodable" into the same boolean as "animated".
 */
class PageImageFormatRegionDecodeTest {

    @Test
    fun `formats the platform can region-decode report support`() {
        assertTrue(PageImageFormat.JPEG.supportsRegionDecode())
        assertTrue(PageImageFormat.PNG.supportsRegionDecode())
        assertTrue(PageImageFormat.WEBP.supportsRegionDecode())
        assertTrue(PageImageFormat.GIF.supportsRegionDecode())
    }

    @Test
    fun `JXL has no region decoder`() {
        assertFalse(
            PageImageFormat.JXL.supportsRegionDecode(),
            "JXL goes through the project bridge, which exposes no region decode",
        )
    }

    /**
     * Conservative by default. An unrecognised format must take the whole-image path rather than a
     * region decode that silently returns only the first frame.
     */
    @Test
    fun `an unrecognised format is treated as not region-decodable`() {
        assertFalse(PageImageFormat.UNSUPPORTED.supportsRegionDecode())
    }

    /**
     * The independence claim, in both directions.
     *
     * Progressive JPEG is the motivating case for the first: static, and the decoder must walk the
     * scan stream, so a region request is refused outright. WebP is the case for the second: it can
     * hold animation, and holding animation is decided by the byte probe, not here.
     */
    @Test
    fun `region decodability and animation capacity are independent`() {
        val staticNotRegionDecodable = PageImageFormat.JPEG
        assertFalse(AnimationPolicy.canHoldAnimation(staticNotRegionDecodable))
        assertTrue(staticNotRegionDecodable.supportsRegionDecode())

        val animatedCapable = PageImageFormat.WEBP
        assertTrue(AnimationPolicy.canHoldAnimation(animatedCapable))
        assertTrue(animatedCapable.supportsRegionDecode())
    }

    /**
     * The mapping's whole purpose is that a PNG needs no byte inspection, so a normal static page
     * costs a format sniff and nothing more.
     */
    @Test
    fun `a format that cannot animate is not probed`() {
        // canHoldAnimation is the short-circuit the reader keys its probe off; if a static format
        // reported true here, every page would pay for a detection pass it cannot benefit from.
        assertFalse(AnimationPolicy.canHoldAnimation(PageImageFormat.PNG))
        assertFalse(AnimationPolicy.canHoldAnimation(PageImageFormat.JPEG))
        assertFalse(AnimationPolicy.canHoldAnimation(PageImageFormat.JXL))
    }

    @Test
    fun `a format that can animate is probed`() {
        assertTrue(AnimationPolicy.canHoldAnimation(PageImageFormat.WEBP))
        assertTrue(AnimationPolicy.canHoldAnimation(PageImageFormat.GIF))
    }
}
