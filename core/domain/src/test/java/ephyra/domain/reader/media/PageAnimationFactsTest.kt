package ephyra.domain.reader.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins the two facts the production reader used to fold into one boolean.
 *
 * Every case here is a behaviour the webtoon reader had, or would have had if the classifier were
 * not used. They are the regression tests for a real defect rather than for the new code: the
 * boolean it replaced reported a static JXL page as animated, and reported a failed detection as
 * static.
 */
class PageAnimationFactsTest {

    private fun classify(
        animated: Boolean = false,
        regionDecodable: Boolean = true,
    ) = PageAnimationClassifier.classify(
        isAnimatedAndSupported = { animated },
        regionDecodable = { regionDecodable },
    )

    @Test
    fun `a static region-decodable page is sliceable`() {
        assertTrue(classify().sliceable)
    }

    /**
     * The case the folded boolean got wrong. A JXL page is not region-decodable, but it is not
     * *animated* either, and the two reasons have different fixes.
     */
    @Test
    fun `a non-region-decodable format is blocked for its own reason, not as animation`() {
        val facts = classify(animated = false, regionDecodable = false)

        assertEquals(AnimationVerdict.Detected(false), facts.verdict, "the format says nothing about animation")
        assertFalse(facts.sliceable)
        assertFalse(facts.regionDecodable)
    }

    @Test
    fun `an animated page is not sliceable even when its format supports it`() {
        val facts = classify(animated = true, regionDecodable = true)

        assertEquals(AnimationVerdict.Detected(true), facts.verdict)
        assertFalse(facts.sliceable)
    }

    /**
     * The dangerous one. The replaced expression was `runCatching { ... }.getOrDefault(false)`, so a
     * detection that threw became "static" and the page was sliced -- displaying only its first
     * frame, which reads as a loading bug rather than a classification one.
     */
    @Test
    fun `a detection that throws is indeterminate and blocks slicing`() {
        val facts = PageAnimationClassifier.classify(
            isAnimatedAndSupported = { throw IllegalStateException("decoder unavailable") },
            regionDecodable = { true },
        )

        assertEquals(AnimationVerdict.Indeterminate, facts.verdict)
        assertFalse(facts.sliceable, "an unknown answer must never be coerced to static")
    }

    @Test
    fun `a format probe that throws is also indeterminate`() {
        val facts = PageAnimationClassifier.classify(
            isAnimatedAndSupported = { false },
            regionDecodable = { throw IllegalStateException("unreadable") },
        )

        assertEquals(AnimationVerdict.Indeterminate, facts.verdict)
        assertFalse(facts.sliceable)
    }

    /**
     * An indeterminate answer leaves the *format* unblocked, so the reason reported is the one the
     * caller can act on. Blocking both would report a format problem for a detection problem.
     */
    @Test
    fun `an indeterminate verdict does not also claim the format is at fault`() {
        assertTrue(PageAnimationFacts.indeterminate().regionDecodable)
    }

    @Test
    fun `the two facts are independent`() {
        val allStatic = classify(animated = false, regionDecodable = true)
        val allBlocked = classify(animated = true, regionDecodable = false)

        assertTrue(allStatic.sliceable)
        assertFalse(allBlocked.sliceable)
        assertEquals(AnimationVerdict.Detected(false), allStatic.verdict)
        assertEquals(AnimationVerdict.Detected(true), allBlocked.verdict)
    }

    @Test
    fun `the convenience constructors agree with the classifier`() {
        assertEquals(classify(), PageAnimationFacts.staticRegionDecodable())
        assertFalse(PageAnimationFacts.indeterminate().sliceable)
    }

    @Test
    fun `a probe throwing a checked exception is still indeterminate`() {
        // The reader's probes can throw IOException as well as a decoder error, and the classifier
        // must not care which.
        val facts = PageAnimationClassifier.classify(
            isAnimatedAndSupported = { throw java.io.IOException("truncated") },
            regionDecodable = { true },
        )
        assertEquals(AnimationVerdict.Indeterminate, facts.verdict)
    }
}
