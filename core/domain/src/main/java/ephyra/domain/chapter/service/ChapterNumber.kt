package ephyra.domain.chapter.service

/**
 * Chapter numbers are `Double` end to end, so `==` is exact.
 *
 * The one upstream `Float` is `SChapter.chapter_number`; widening it once at the extension
 * bridge is lossless. Backups store `Double`, so a round trip no longer perturbs.
 */
object ChapterNumber {

    /** True when [a] and [b] identify the same chapter. */
    fun sameChapterNumber(a: Double, b: Double): Boolean {
        if (!isRecognized(a) || !isRecognized(b)) return false
        return a == b
    }

    /** True when [chapterNumber] is at or before [lastRead]. */
    fun hasReached(chapterNumber: Double, lastRead: Double): Boolean {
        if (!isRecognized(chapterNumber) || !isRecognized(lastRead)) return false
        return chapterNumber <= lastRead
    }

    /** Mirrors `Chapter.isRecognizedNumber`, so `-0.0` and `0.0` are both unrecognised. */
    fun isRecognized(chapterNumber: Double): Boolean = chapterNumber >= 0

    /** Key for a Set/Map. Unrecognised numbers key off identity, not the number. */
    fun bucket(chapterNumber: Double, identity: String): String =
        if (isRecognized(chapterNumber)) "number:$chapterNumber" else "unrecognised:$identity"
}
