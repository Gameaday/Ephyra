package ephyra.domain.chapter.service

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

/**
 * Decides whether two chapter numbers mean the same chapter.
 *
 * `==` is not safe. `SChapter.chapter_number` is a `Float` in the extension ABI, so a source
 * that reports 12.3 reaches us as `12.300000190734863`, while `ChapterRecognition` parses the
 * same chapter from its name as `12.3`. Those are one chapter and must compare equal. Widening
 * the Float at the bridge is lossless in one direction only: it cannot recover the `12.3`.
 *
 * Backups are not the reason. `BackupChapter.chapterNumber` is a `Double`, so a restore is exact.
 */
object ChapterNumber {

    /**
     * Two bounds, whichever is larger: [EPSILON] for small numbers, or two ULPs at this magnitude
     * (`2^-23` of the enclosing power of two) — what a `Float` round trip can actually perturb.
     */
    fun tolerance(magnitude: Double): Double {
        if (magnitude == 0.0 || !magnitude.isFinite()) return EPSILON
        val binade = 2.0.pow(floor(log2(abs(magnitude))))
        return maxOf(EPSILON, binade * TWO_ULP)
    }

    /** True when [a] and [b] identify the same chapter. */
    fun sameChapterNumber(a: Double, b: Double): Boolean {
        if (!isRecognized(a) || !isRecognized(b)) return false
        return abs(a - b) <= tolerance(maxOf(abs(a), abs(b)))
    }

    /**
     * True when [chapterNumber] is at or before [lastRead], counting a chapter that is only
     * past the marker by Float error. Without it a restored chapter never marks read.
     */
    fun hasReached(chapterNumber: Double, lastRead: Double): Boolean {
        if (!isRecognized(chapterNumber) || !isRecognized(lastRead)) return false
        return chapterNumber <= lastRead + tolerance(maxOf(abs(chapterNumber), abs(lastRead)))
    }

    /** Mirrors `Chapter.isRecognizedNumber`, so `-0.0` and `0.0` are both unrecognised. */
    fun isRecognized(chapterNumber: Double): Boolean = chapterNumber >= 0

    /** Key for a Set/Map. Unrecognised numbers key off identity, not the number. */
    fun bucket(chapterNumber: Double, identity: String): String {
        if (!isRecognized(chapterNumber)) return "unrecognised:$identity"
        return "number:" + Math.round(chapterNumber / tolerance(abs(chapterNumber)))
    }

    /** Floor for ordinary magnitudes; a fixed `1e-4` fails from chapter 4096 on. */
    const val EPSILON: Double = 1e-4

    private const val TWO_ULP: Double = 1.1920928955078125e-7 // 2^-23
}
