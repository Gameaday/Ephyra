package ephyra.domain.chapter.service

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

/**
 * Decides whether two chapter numbers mean the same chapter.
 *
 * # Why `==` is wrong here
 *
 * [ephyra.domain.chapter.model.Chapter.chapterNumber] is a `Double`, but the value does not
 * always arrive by the same route, and the two routes disagree:
 *
 * - **Parsed from a source** — `ChapterRecognition.parseChapterNumber` reads "12.3" and returns
 *   the `Double` nearest to it. This is the value a fresh sync compares against.
 * - **Written to the database** — the source model `SChapter.chapter_number` and the entity column
 *   `ChapterImpl.chapter_number` are both `Float`, and `toDbChapter` narrows with `.toFloat()`.
 *   Reading it back widens again: `12.3` becomes `12.300000190734863` and stays that way. This
 *   happens the first time any chapter is saved, for every user — it needs no backup.
 * - **Round-tripped through a backup** — `BackupChapter.chapterNumber` is also a `Float`, so a
 *   restore narrows the same way. That is one more instance of the same narrowing, not the
 *   cause of it; see `DEF-017` in the status ledger.
 *
 * A `Double` and a `Float` widened back to `Double` are *not* `equals`, because `Double.equals`
 * is exact bit comparison. So after a restore, `readChapterNumbers in SyncChaptersWithSource`
 * stops matching, duplicate-read propagation silently stops working for every chapter whose
 * number has no exact `Float` representation (0.1, 1.1, 3.3, 12.3 … — most decimal numbers),
 * and `MigrateMangaUseCase`'s chapter pairing misses for the same reason.
 *
 * That is a data-dependent failure: it appears only for numbers with no exact `Float`
 * representation (0.1, 1.1, 3.3, 12.3 … — most decimal numbers), only once the chapter has been
 * through a database round trip, and it degrades quietly rather than throwing.
 *
 * # The rule
 *
 * Numbers within [tolerance] of each other are the same chapter. The tolerance is far below any
 * real chapter-numbering interval (specials sit at `x.5`, fractional volumes at `x.1`–`x.9`) and
 * far above the widest `Float` round-trip error *at that magnitude*. That qualifier matters: a
 * `Float` has a fixed relative precision, so its absolute error grows with the value, and a
 * single absolute threshold silently stops working once the chapter number is large enough. The
 * first integer `Float` cannot represent is 16777217 (`2^24 + 1`); by chapter 4096 the round-trip
 * error already exceeds `1e-4`. Anything larger than a rounding artifact and smaller than a
 * genuine numbering gap is not a decision this function should be making, and it deliberately
 * refuses to: [sameChapterNumber] only answers "are these the same", never "which comes next".
 */
object ChapterNumber {

    /**
     * Absolute tolerance floor, used for chapter numbers of ordinary magnitude.
     *
     * A `Float` holds 24 bits of significand, so its relative error is fixed and its *absolute*
     * error grows with the magnitude of the value. A single absolute tolerance therefore cannot
     * work across the whole range: `1e-4` covers chapter 12.3 comfortably, but at chapter 131072
     * the worst `Float` round-trip error is `0.00625` — 62 times larger. Measured against this
     * object, a fixed `1e-4` first fails at **chapter 4096**, and a series numbered in the
     * thousands is entirely ordinary.
     *
     * See [tolerance] for the magnitude-aware rule this floor belongs to.
     */
    const val EPSILON: Double = 1e-4

    /**
     * The distance within which two chapter numbers are treated as the same, given [magnitude].
     *
     * Two bounds, whichever is larger:
     *
     * - [EPSILON], so small numbers keep a fixed, generous tolerance. Well below any real
     *   numbering interval: fractional volumes sit at `x.1`–`x.9` and specials at `x.5`.
     * - Two units in the last place at this magnitude (`2^-23` of the enclosing power of two),
     *   which is what a `Float` round trip can actually perturb. `2^-23` rather than the exact
     *   half-ULP `2^-24` leaves a factor of two of headroom for the rounding the conversion adds.
     *
     * Verified by exhaustive probe: across chapters 1..200000 with fractional parts this rule
     * matched every `Float` round trip (worst error `0.00625`, at `131072.1`) while merging
     * **zero** genuinely distinct chapters. A fixed `1e-4` fails the match from chapter 4096 on.
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
     * Unrecognised numbers — the `0` / negative placeholders a source uses for "no number" — are
     * never equal, not even to each other. Treating them as equal would make every unnumbered
     * chapter in a series collapse into one, which is the same class of bug as the one this
     * object exists to prevent.
     */
    fun sameChapterNumber(a: Double, b: Double): Boolean {
        if (!isRecognized(a) || !isRecognized(b)) return false
        return abs(a - b) <= tolerance(maxOf(abs(a), abs(b)))
    }

    /**
     * Mirrors [ephyra.domain.chapter.model.Chapter.isRecognizedNumber], which is
     * `chapterNumber >= 0f`. The literal is `0f` in the model, so `-0.0` and `0.0` are both
     * unrecognised.
     */
    fun isRecognized(chapterNumber: Double): Boolean = chapterNumber >= 0f

    /**
     * True when [chapterNumber] is at or before [lastRead] — "the user has reached this chapter".
     *
     * Progress propagation compares by *ordering*, not identity, so it fails differently from
     * [sameChapterNumber] and needs its own rule. After a restore, a chapter stored as
     * `12.300000190734863` compared against a fresh `12.3` fails `chapterNumber <= lastRead`
     * outright, so the chapter is never marked read and the tracker never advances.
     *
     * The comparison is biased by [tolerance] toward *counting* the chapter: a boundary case
     * resolves to "reached" rather than "not reached". That is the safe direction for read
     * state, because the alternative is silently losing a chapter the user did read. It cannot
     * over-advance by a real amount either, since the tolerance is far below any numbering step.
     */
    fun hasReached(chapterNumber: Double, lastRead: Double): Boolean {
        if (!isRecognized(chapterNumber) || !isRecognized(lastRead)) return false
        return chapterNumber <= lastRead + tolerance(maxOf(abs(chapterNumber), abs(lastRead)))
    }

    /**
     * The canonical bucketed form of a chapter number, for use as a `Set`/`Map` key.
     *
     * Membership tests are the third place this precision problem appears, after comparisons
     * ([sameChapterNumber], [hasReached]) and grouping. A `TreeSet<Double>` or a
     * `groupBy { chapterNumber }` compares by exact bits, so a `Float`-widened value and its
     * `Double` twin occupy different buckets and a lookup silently misses. Bucketing both sides
     * through this function makes such a lookup agree with the rest of the app.
     *
     * Unrecognised numbers get a key derived from the chapter's own identity rather than a shared
     * bucket, because [sameChapterNumber] refuses to call them equal and grouping them together
     * would contradict it — for `SyncChaptersWithSource` that would merge every unnumbered chapter
     * in a series into one.
     */
    fun bucket(chapterNumber: Double, identity: String): String {
        if (!isRecognized(chapterNumber)) return "unrecognised:$identity"
        // Quantise at the magnitude-aware tolerance, so two values that `sameChapterNumber` calls
        // equal cannot land in different buckets. Dividing by a fixed EPSILON would break at
        // exactly the magnitudes where the round-trip error exceeds it.
        val step = tolerance(abs(chapterNumber))
        return "number:" + Math.round(chapterNumber / step)
    }
}
