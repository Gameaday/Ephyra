package ephyra.domain.chapter.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
        // A `Float` has a fixed *relative* precision, so its absolute error grows with magnitude.
        // A single absolute tolerance therefore stops working partway up the range: the fixed
        // `1e-4` this object used to carry first failed at chapter 4096. These are the values that
        // exposed it, measured rather than assumed. The widest error is at the top of the range.
        for (value in listOf(4096.1, 99_999.5, 100_000.5, 131_072.1)) {
            assertTrue(ChapterNumber.sameChapterNumber(value, value.toFloat().toDouble()))
        }
    }

    @Test
    fun `the database round trip alone is enough to trigger this`() {
        // `DEF-017`. The narrowing is not a backup artefact: `SChapter.chapter_number` and
        // `ChapterImpl.chapter_number` are both `Float`, and `toDbChapter` narrows on every write.
        // This is the exact chain, with no backup anywhere in it -- so the affected population is
        // every user with a decimal chapter number, not only people who restored a backup.
        val fromSource = 12.3
        val afterDatabase = fromSource.toFloat().toDouble()

        assertFalse(fromSource == afterDatabase, "premise: the round trip is lossy")
        assertTrue(ChapterNumber.sameChapterNumber(fromSource, afterDatabase))
    }

    @Test
    fun `the magnitude scaled tolerance matches every Float round trip`() {
        // Exhaustive over the range where the error is largest, and over the fractional parts that
        // a source actually reports. Every one of these fails under a fixed 1e-4.
        for (n in 1..2000) {
            for (frac in listOf(0.1, 0.2, 0.3, 0.5, 0.7, 0.9)) {
                val value = n + frac
                assertTrue(
                    ChapterNumber.sameChapterNumber(value, value.toFloat().toDouble()),
                    "$value did not match its Float round trip",
                )
            }
        }
    }

    @Test
    fun `the scaled tolerance never merges distinct chapters`() {
        // The other half of the bound. A tolerance wide enough to cover the round trip must still
        // be narrower than the gap between real chapters, or it would merge neighbours.
        for (n in 1..2000) {
            assertFalse(
                ChapterNumber.sameChapterNumber(n + 0.1, n + 0.2),
                "$n.1 and $n.2 were treated as the same chapter",
            )
            assertFalse(
                ChapterNumber.sameChapterNumber(n + 0.5, n + 1.0),
                "a special and the following chapter were treated as the same",
            )
        }
    }

    @Test
    fun `bucket agrees with sameChapterNumber at large magnitudes`() {
        // A set key and a comparator must not disagree. Dividing by a fixed EPSILON would put a
        // widened 131072.1 in a different bucket from its twin.
        for (value in listOf(12.3, 4096.1, 131072.1, 100000.5)) {
            val widened = value.toFloat().toDouble()
            assertTrue(ChapterNumber.sameChapterNumber(value, widened))
            assertEquals(
                ChapterNumber.bucket(value, "/chapter/a"),
                ChapterNumber.bucket(widened, "/chapter/a"),
                "$value and its round trip landed in different buckets",
            )
        }
    }

    @Test
    fun `hasReached agrees at large magnitudes`() {
        assertTrue(ChapterNumber.hasReached(131072.1, 131072.1.toFloat().toDouble()))
        assertFalse(ChapterNumber.hasReached(131072.2, 131072.1.toFloat().toDouble()))
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

    @Test
    fun `reached survives a Float round trip on either side`() {
        // The tracking failure. A restored chapter sits at 12.300000190734863 while a fresh
        // lastRead is 12.3; the restored value is *greater*, so a plain `<=` says not reached
        // and the chapter silently never gets marked read.
        val restored = 12.3.toFloat().toDouble()
        assertFalse(restored <= 12.3, "premise: plain ordering misses the restored value")
        assertTrue(ChapterNumber.hasReached(restored, 12.3))
        assertTrue(ChapterNumber.hasReached(12.3, restored))
    }

    @Test
    fun `reached is false for a chapter genuinely beyond the marker`() {
        assertFalse(ChapterNumber.hasReached(13.0, 12.0))
        assertFalse(ChapterNumber.hasReached(12.5, 12.0), "a special past the marker is not reached")
        assertTrue(ChapterNumber.hasReached(12.0, 12.0), "the marker chapter itself is reached")
        assertTrue(ChapterNumber.hasReached(11.0, 12.0))
    }

    @Test
    fun `reached refuses unrecognised numbers on either side`() {
        assertFalse(ChapterNumber.hasReached(-1.0, 12.0))
        assertFalse(ChapterNumber.hasReached(12.0, -1.0))
        assertFalse(ChapterNumber.hasReached(-1.0, -1.0))
    }

    @Test
    fun `reached never advances by a real numbering step`() {
        // Bias is toward counting, but only within the tolerance.
        assertFalse(ChapterNumber.hasReached(12.5, 12.0))
        assertFalse(ChapterNumber.hasReached(13.0, 12.0))
    }

    @Test
    fun `bucket puts a Float round trip in the same bucket as its source`() {
        // Membership is the third place this precision problem appears. A `TreeSet<Double>` or a
        // `groupBy { chapterNumber }` compares exact bits, so these two land in different buckets
        // and a lookup silently misses.
        assertFalse(12.3 == 12.3.toFloat().toDouble(), "premise: these are not Double.equals")
        assertEquals(
            ChapterNumber.bucket(12.3, "/chapter/a"),
            ChapterNumber.bucket(12.3.toFloat().toDouble(), "/chapter/a"),
            "a restored chapter must fall in the same bucket as its live twin",
        )
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
        // Mirrors `sameChapterNumber`, which refuses to call unrecognised numbers equal. If these
        // shared a bucket, every unnumbered chapter in a series would collapse into one.
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
