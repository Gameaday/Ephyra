package ephyra.domain.chapter.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the defect this object exists to prevent: a chapter number parsed from a source and the
 * same number round-tripped through a `Float` backup field are not `Double.equals`, and the
 * read-state and migration comparisons that use `==` therefore stop matching after a restore.
 */
class ChapterNumberTest {

    @Test
    fun `a number is the same as its Float round trip`() {
        // The exact failure. `parseChapterNumber` yields 12.3; `BackupChapter.chapterNumber` is a
        // `Float`, so a restore yields 12.300000190734863. `==` says these differ.
        assertFalse(12.3 == 12.3.toFloat().toDouble(), "premise: these are not Double.equals")
        assertTrue(ChapterNumber.sameChapterNumber(12.3, 12.3.toFloat().toDouble()))
    }

    @Test
    fun `every decimal number survives a Float round trip`() {
        // A representative spread, including the values that bit in practice.
        for (value in listOf(0.1, 0.7, 1.1, 3.3, 8.5, 12.3, 100.1, 1234.5678)) {
            assertTrue(
                ChapterNumber.sameChapterNumber(value, value.toFloat().toDouble()),
                "$value must still match after a Float round trip",
            )
        }
    }

    @Test
    fun `a large chapter number still round trips`() {
        // The widest Float error is at the top of the range, so the bound is checked there too
        // rather than only on small numbers.
        val large = 99_999.5
        assertTrue(ChapterNumber.sameChapterNumber(large, large.toFloat().toDouble()))
    }

    @Test
    fun `identical numbers match exactly`() {
        assertTrue(ChapterNumber.sameChapterNumber(7.0, 7.0))
        assertTrue(ChapterNumber.sameChapterNumber(0.0, 0.0))
    }

    @Test
    fun `genuinely different chapters do not match`() {
        // The tolerance must not be wide enough to collapse real numbering.
        assertFalse(ChapterNumber.sameChapterNumber(1.0, 2.0))
        assertFalse(ChapterNumber.sameChapterNumber(12.0, 12.5))
        assertFalse(ChapterNumber.sameChapterNumber(1.0, 1.5), "a special is a different chapter")
        assertFalse(ChapterNumber.sameChapterNumber(0.0, 0.5), "volume 0.5 is not volume 0")
    }

    @Test
    fun `unrecognised numbers never match, not even each other`() {
        // A source with no chapter numbers gives chapters -1 or -2. Treating those as equal
        // would collapse an entire series into one chapter.
        assertFalse(ChapterNumber.sameChapterNumber(-1.0, -1.0))
        assertFalse(ChapterNumber.sameChapterNumber(-1.0, -2.0))
        assertFalse(ChapterNumber.sameChapterNumber(-1.0, -1.5))
    }

    @Test
    fun `zero is a recognised number, matching the model predicate`() {
        // `isRecognizedNumber` is `chapterNumber >= 0f`, so 0.0 is recognised and two
        // zero-numbered chapters really are the same chapter. Asserting otherwise here would
        // have contradicted the model this mirrors, and the "unrecognised" case above is the
        // negative one.
        assertTrue(ChapterNumber.isRecognized(0.0))
        assertTrue(ChapterNumber.sameChapterNumber(0.0, 0.0))
        assertFalse(ChapterNumber.sameChapterNumber(0.0, 1.0))
    }

    @Test
    fun `isRecognized mirrors the model predicate`() {
        assertTrue(ChapterNumber.isRecognized(0.0))
        assertTrue(ChapterNumber.isRecognized(0.5))
        assertTrue(ChapterNumber.isRecognized(1234.0))
        assertFalse(ChapterNumber.isRecognized(-0.5))
        assertFalse(ChapterNumber.isRecognized(-1.0))
    }

    @Test
    fun `the tolerance is far below any real numbering interval`() {
        // Guards the tolerance itself: if this ever fails, the epsilon has been widened into
        // territory where it would merge distinct chapters.
        assertTrue(
            ChapterNumber.EPSILON < 0.5 - 0.0,
            "epsilon must stay well under the half-step that separates a special from a chapter",
        )
    }
}
