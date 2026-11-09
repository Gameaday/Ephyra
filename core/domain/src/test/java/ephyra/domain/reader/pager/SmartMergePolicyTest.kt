package ephyra.domain.reader.pager

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the 2026-11-09 owner directive — a 1 px filler strip is a stub and merges — and the whole
 * decision surface that makes it safe.
 *
 * The defect: the stub floor used to be 2 % of the reference height, so a strip shorter than that
 * was classified as a real page and published alone; the reader showed a blank 1 px "page"
 * between merged pairs. The floor is now 0 %. The bounds also used to live in three places
 * (preference defaults, `ImageUtil` constants, `Downloader` literals); these tests pin the one
 * owner so a future edit cannot reintroduce a private copy of the numbers.
 */
class SmartMergePolicyTest {

    @Test
    fun `a 1px tall strip of the same width is a stub`() {
        // The reported defect: a 10000x1 filler was its own page under the old 2 % floor.
        assertTrue(
            SmartMergePolicy.isStub(
                candidateWidth = 10000,
                candidateHeight = 1,
                referenceWidth = 10000,
                referenceHeight = 3000,
            ),
            "a 1px same-width strip must be classified as a stub and merged",
        )
    }

    @Test
    fun `a 1 percent height strip is a stub`() {
        // 30px of a 3000px page is 1 % — above zero but below the old 2 % floor, so the old rule
        // called it a real page. The new floor is inclusive at 0.
        assertTrue(SmartMergePolicy.isStub(10000, 30, 10000, 3000))
    }

    @Test
    fun `a full page is not a stub`() {
        assertFalse(SmartMergePolicy.isStub(10000, 3000, 10000, 3000))
    }

    @Test
    fun `the maximum height fraction is exclusive`() {
        // Exactly 30 % of the reference: more likely a real short page than a strip, so the rule
        // must refuse to merge.
        assertFalse(SmartMergePolicy.isStub(10000, 900, 10000, 3000))
        // A hair under the ceiling: still a strip.
        assertTrue(SmartMergePolicy.isStub(10000, 899, 10000, 3000))
    }

    @Test
    fun `the width tolerance is inclusive at 5 percent`() {
        // A re-encoded strip shaves exactly 5 % off the width; still "same width".
        assertTrue(SmartMergePolicy.isStub(9500, 30, 10000, 3000))
        // Beyond the tolerance is a different page (e.g. half-width), not a strip.
        assertFalse(SmartMergePolicy.isStub(9400, 30, 10000, 3000))
    }

    @Test
    fun `per-series bounds are honored`() {
        // A series tuned to absorb only large slivers: floor 50 %, ceiling 90 %.
        assertTrue(
            SmartMergePolicy.isStub(10000, 6000, 10000, 10000, minHeightFraction = 0.5f, maxHeightFraction = 0.9f),
            "a 60 % strip inside the series bounds must merge",
        )
        assertFalse(
            SmartMergePolicy.isStub(10000, 2000, 10000, 10000, minHeightFraction = 0.5f, maxHeightFraction = 0.9f),
            "a 20 % strip below the series floor must not merge",
        )
    }

    @Test
    fun `inverted bounds fail closed`() {
        // A corrupted per-series preference must disable merging, not invert the rule.
        assertFalse(SmartMergePolicy.isStub(10000, 30, 10000, 3000, minHeightFraction = 0.9f, maxHeightFraction = 0.5f))
    }

    @Test
    fun `unreadable or non-positive dimensions fail closed`() {
        // Header probes leave dimensions at 0 (or negative) when they cannot parse the image; a
        // page of unknown size is never absorbed.
        assertFalse(SmartMergePolicy.isStub(0, 30, 10000, 3000))
        assertFalse(SmartMergePolicy.isStub(10000, 0, 10000, 3000))
        assertFalse(SmartMergePolicy.isStub(10000, 30, 0, 3000))
        assertFalse(SmartMergePolicy.isStub(10000, 30, 10000, 0))
        assertFalse(SmartMergePolicy.isStub(-1, 30, 10000, 3000))
        assertFalse(SmartMergePolicy.isStub(10000, 30, 10000, -3))
    }

    @Test
    fun `the defaults pin the owner directive`() {
        // The floor is exactly 0 % (the 1px fix) and the ceiling exactly 30 %, exclusive. If either
        // number moves, this is the test that must be read before the move is trusted.
        assertTrue(
            SmartMergePolicy.DEFAULT_MIN_HEIGHT_FRACTION == 0f,
            "the stub floor must stay 0 %; the 1px directive depends on it",
        )
        assertTrue(
            SmartMergePolicy.DEFAULT_MAX_HEIGHT_FRACTION == 0.30f,
            "the stub ceiling must stay an exclusive 30 %",
        )
    }
}
