package ephyra.domain.chapter.service

import ephyra.domain.chapter.model.Chapter

/**
 * Decides what a newly-seen chapter should inherit from a chapter that has disappeared upstream.
 *
 * When a source renumbers or reorders, a chapter it used to serve is removed and a new one
 * appears under the same number. Carrying the old chapter's read, bookmark and fetch-date state
 * across prevents the user's history from resetting on what is really the same chapter, and keeps
 * the Updates tab from reporting it as newly fetched.
 *
 * This exists as a separate object because the decision is subtle in a way the surrounding
 * interactor is not: the two sides of every comparison arrive by *different routes*. The removed
 * chapter's number comes from the source, the candidate's from the database, and a database value
 * that has been through a backup restore is `Float`-widened (`12.3` becomes
 * `12.300000190734863`). Comparing those as `Double`s is exact-bit and silently misses, which
 * leaves the removed chapter's state behind on a chapter that no longer exists. That is the
 * `DEF-012` defect class, and [ChapterNumber] is the single rule for it.
 *
 * Ordering is by the removed chapter's own fetch date, so the most recently fetched one wins when
 * several removed chapters share a number.
 */
object RemovedChapterState {

    /** The state a candidate chapter inherits, or `null` when nothing matches it. */
    data class Inherited(
        val read: Boolean = false,
        val bookmark: Boolean = false,
        val dateFetch: Long? = null,
    )

    fun from(removedChapters: List<Chapter>): Map<String, Inherited> {
        // Keyed by chapter number, so two removed chapters sharing a number collapse. The fetch
        // date is retained across the merge rather than taken from whichever happens to win the
        // slot, because that is the value the Updates tab sorts on.
        val byKey = LinkedHashMap<String, Chapter>()
        removedChapters.forEach { chapter ->
            val key = ChapterNumber.bucket(chapter.chapterNumber, chapter.url)
            val existing = byKey[key]
            if (existing == null || chapter.dateFetch > existing.dateFetch) {
                byKey[key] = chapter
            }
        }
        return byKey.mapValues { (_, chapter) ->
            Inherited(
                read = chapter.read,
                bookmark = chapter.bookmark,
                dateFetch = chapter.dateFetch,
            )
        }
    }

    /**
     * The state [candidate] inherits from [state], or `null` when it inherits nothing.
     *
     * Returns `null` rather than an empty [Inherited] for a chapter with no recognised number, so
     * the caller can leave it untouched. That mirrors `ChapterNumber.sameChapterNumber`, which
     * refuses to treat unrecognised numbers as equal: a source that reports no numbers must not
     * have its chapters silently adopted from unrelated ones.
     */
    fun inherit(candidate: Chapter, state: Map<String, Inherited>): Inherited? {
        if (!candidate.isRecognizedNumber) return null
        val key = ChapterNumber.bucket(candidate.chapterNumber, candidate.url)
        return state[key]
    }
}
