package ephyra.domain.chapter.service

import kotlin.math.abs

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
 * - **Round-tripped through backup** — `BackupChapter.chapterNumber` is declared `Float`, so
 *   `toChapterImpl()` widens a narrowed value: `12.3` becomes `12.300000190734863`. A restored
 *   chapter keeps that value in the database from then on.
 *
 * A `Double` and a `Float` widened back to `Double` are *not* `equals`, because `Double.equals`
 * is exact bit comparison. So after a restore, `readChapterNumbers in SyncChaptersWithSource`
 * stops matching, duplicate-read propagation silently stops working for every chapter whose
 * number has no exact `Float` representation (0.1, 1.1, 3.3, 12.3 … — most decimal numbers),
 * and `MigrateMangaUseCase`'s chapter pairing misses for the same reason.
 *
 * That is a data-dependent failure: it appears only after a backup restore, only for decimal
 * chapter numbers, and it degrades quietly rather than throwing.
 *
 * # The rule
 *
 * Numbers within [EPSILON] of each other are the same chapter. The tolerance is far below any
 * real chapter-numbering interval (specials sit at `x.5`, fractional volumes at `x.1`–`x.9`)
 * and far above the widest `Float` round-trip error, which is about `1.2e-5` at chapter 10,000.
 * Anything larger than a rounding artifact and smaller than a genuine numbering gap is not a
 * decision this function should be making, and it deliberately refuses to: [sameChapterNumber]
 * only answers "are these the same", never "which chapter comes next".
 */
object ChapterNumber {

    /** Maximum absolute difference still attributable to `Float` round-tripping. */
    const val EPSILON: Double = 1e-4

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
        return abs(a - b) <= EPSILON
    }

    /**
     * Mirrors [ephyra.domain.chapter.model.Chapter.isRecognizedNumber], which is
     * `chapterNumber >= 0f`. The literal is `0f` in the model, so `-0.0` and `0.0` are both
     * unrecognised.
     */
    fun isRecognized(chapterNumber: Double): Boolean = chapterNumber >= 0f
}
