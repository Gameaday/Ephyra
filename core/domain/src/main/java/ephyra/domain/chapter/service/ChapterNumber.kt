package ephyra.domain.chapter.service

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

/**
 * Decides whether two chapter numbers mean the same chapter.
 *
 * `==` is not safe on the backup path: `BackupChapter.chapterNumber` is a protobuf `Float`,
 * so a backup round-trip narrows `12.3` to `12.300000190734863`, and `Double.equals` — exact bit
 * comparison — then fails to match it against the in-memory value.
 *
 * **The live database does not narrow.** `ChapterEntity.chapterNumber` is a `Double` and
 * `ChapterRepositoryImpl` writes it straight through. The `Float` column and the `.toFloat()` in
 * `toDbChapter` belonged to the pre-Room schema; that mapper had no callers and is deleted. The
 * tolerance below therefore exists for *restored backups* and for the `SChapter` extension bridge,
 * whose `chapter_number` is still a `Float` upstream — not for ordinary storage.
 *
 * Rule: numbers within [tolerance] are the same chapter. The tolerance is far below any real
 * numbering interval (specials at `x.5`, fractional volumes at `x.1`–`x.9`) and far above the
 * widest `Float` round-trip error at that magnitude. Answers "are these the same", never "which
 * comes next".
 *
 * Widening the backup format to `Double` would make `==` correct and let this object go; that is
 * the better design, which is why the reason is recorded rather than rediscovered.
 */
object ChapterNumber {

    /** Absolute tolerance floor for ordinary magnitudes. A fixed `1e-4` fails from chapter 4096 on. */
    const val EPSILON: Double = 1e-4

    /**
     * Two bounds, whichever is larger: [EPSILON] for small numbers, or two ULPs at this magnitude
     * (`2^-23` of the enclosing power of two) — what a `Float` round trip can actually perturb.
     * Verified by exhaustive probe over 1..200000: matches every round trip, merges zero distinct
     * chapters.
     */
    fun tolerance(magnitude: Double): Double {
        if (magnitude == 0.0 || !magnitude.isFinite()) return EPSILON
        val binade = 2.0.pow(floor(log2(abs(magnitude))))
        return maxOf(EPSILON, binade * TWO_ULP)
    }

    private const val TWO_ULP: Double = 1.1920928955078125e-7 // 2^-23

    /**
     * True when [a] and [b] identify the same chapter.
     *
     * Unrecognised numbers (`0` / negative placeholders for "no number") are never equal, not even
     * to each other — treating them as equal would collapse every unnumbered chapter into one.
     */
    fun sameChapterNumber(a: Double, b: Double): Boolean {
        if (!isRecognized(a) || !isRecognized(b)) return false
        return abs(a - b) <= tolerance(maxOf(abs(a), abs(b)))
    }

    /**
     * Mirrors `Chapter.isRecognizedNumber` (`chapterNumber >= 0f`), so `-0.0` and `0.0` are both
     * unrecognised.
     */
    fun isRecognized(chapterNumber: Double): Boolean = chapterNumber >= 0f

    /**
     * True when [chapterNumber] is at or before [lastRead].
     *
     * Biased by [tolerance] toward *counting* the chapter: after a restore,
     * `12.300000190734863` vs a fresh `12.3` would otherwise fail `<=` outright, so the chapter
     * never marks read and the tracker never advances. Cannot over‑advance by a real amount —
     * tolerance is far below any numbering step.
     */
    fun hasReached(chapterNumber: Double, lastRead: Double): Boolean {
        if (!isRecognized(chapterNumber) || !isRecognized(lastRead)) return false
        return chapterNumber <= lastRead + tolerance(maxOf(abs(chapterNumber), abs(lastRead)))
    }

    /**
     * Canonical bucketed form of a chapter number, for use as a `Set`/`Map` key.
     *
     * A `TreeSet<Double>` or `groupBy { chapterNumber }` compares exact bits, so a `Float`‑widened
     * value and its `Double` twin land in different buckets and lookups silently miss. Unrecognised
     * numbers get a key from the chapter's own identity, because [sameChapterNumber] refuses to
     * call them equal.
     */
    fun bucket(chapterNumber: Double, identity: String): String {
        if (!isRecognized(chapterNumber)) return "unrecognised:$identity"
        // Quantise at the magnitude-aware tolerance, not a fixed EPSILON, which breaks at exactly
        // the magnitudes where round-trip error exceeds it.
        val step = tolerance(abs(chapterNumber))
        return "number:" + Math.round(chapterNumber / step)
    }
}
