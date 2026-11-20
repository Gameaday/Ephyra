package ephyra.domain.chapter.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Chapter numbers are `Double` end to end, so every comparison is exact. */
class ChapterNumberTest {

    @Test
    fun `identical numbers match`() {
        assertTrue(ChapterNumber.sameChapterNumber(12.3, 12.3))
        assertTrue(ChapterNumber.sameChapterNumber(0.0, 0.0))
        assertTrue(ChapterNumber.sameChapterNumber(1234.5678, 1234.5678))
    }

    @Test
    fun `genuinely different chapters do not match`() {
        assertFalse(ChapterNumber.sameChapterNumber(12.3, 12.4))
        assertFalse(ChapterNumber.sameChapterNumber(12.0, 13.0))
        assertFalse(ChapterNumber.sameChapterNumber(0.0, 1.0))
    }

    @Test
    fun `a decimal number survives the whole pipeline exactly`() {
        // The property the old tolerance existed to approximate. Backup stores Double, and the one
        // upstream Float (SChapter.chapter_number) is widened losslessly at the extension bridge.
        for (value in listOf(0.1, 0.7, 1.1, 3.3, 8.5, 12.3, 100.1, 1234.5678, 131_072.1)) {
            assertTrue(ChapterNumber.sameChapterNumber(value, value), "$value must match itself")
            assertEquals(ChapterNumber.bucket(value, "/c"), ChapterNumber.bucket(value, "/c"))
        }
    }

    @Test
    fun `unrecognised numbers never match, not even each other`() {
        // Collapsing every unnumbered chapter into one would be a data-loss bug. Negative numbers
        // are the unrecognised ones; `0.0` is recognised (specials are numbered from zero).
        assertFalse(ChapterNumber.sameChapterNumber(-1.0, -1.0))
        assertFalse(ChapterNumber.sameChapterNumber(-1.0, 1.0))
        assertTrue(ChapterNumber.sameChapterNumber(0.0, 0.0))
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
    fun `reached orders against the read marker`() {
        assertTrue(ChapterNumber.hasReached(12.0, 12.0), "the marker chapter itself is reached")
        assertTrue(ChapterNumber.hasReached(11.0, 12.0))
        assertFalse(ChapterNumber.hasReached(12.5, 12.0), "a special past the marker is not reached")
        assertFalse(ChapterNumber.hasReached(13.0, 12.0))
    }

    @Test
    fun `reached refuses unrecognised numbers on either side`() {
        assertFalse(ChapterNumber.hasReached(-1.0, 12.0))
        assertFalse(ChapterNumber.hasReached(12.0, -1.0))
        assertFalse(ChapterNumber.hasReached(-1.0, -1.0))
    }

    @Test
    fun `bucket separates genuinely different numbers`() {
        assertNotEquals(
            ChapterNumber.bucket(12.3, "/chapter/a"),
            ChapterNumber.bucket(12.4, "/chapter/a"),
        )
    }

    @Test
    fun `bucket keeps unrecognised numbers apart`() {
        assertNotEquals(
            ChapterNumber.bucket(-1.0, "/chapter/a"),
            ChapterNumber.bucket(-1.0, "/chapter/b"),
        )
        assertNotEquals(
            ChapterNumber.bucket(-1.0, "/chapter/a"),
            ChapterNumber.bucket(1.0, "/chapter/a"),
        )
    }
}
