package ephyra.feature.reader.model

import ephyra.domain.reader.navigation.ReaderChapterCandidate
import ephyra.domain.reader.navigation.ReaderChapterOrder
import ephyra.domain.reader.navigation.ReaderChapterWindowPolicy
import ephyra.domain.reader.navigation.ReaderChapterWindowResult
import ephyra.domain.reader.navigation.ReaderNavigationPolicy

/**
 * Selects the reader's prev/next chapter window through the domain
 * [ReaderChapterWindowPolicy] (RDR-002), replacing the feature-local sibling helpers deleted
 * in the same step.
 *
 * **What stays upstream, deliberately.** GetChapterList in the view model already applies the
 * series-screen filters (duplicate reduction, downloaded-only) before this runs, so the adapter
 * passes no filtering switches and the domain policy is not asked to re-run them;
 * skipReadForward is threaded through because it is a reader-navigation rule, not a display
 * filter. The skipFilteredForward switch stays off for the same reason the old code left it
 * off: the reader's navigation universe is independent of the series screen's view filters.
 *
 * **Ordering.** The caller's list order is the canonical reader order (it was sorted by
 * getChapterSort), so candidates are pinned positionally and the SOURCE order reproduces that
 * order exactly. A NUMBER re-sort would reorder unnumbered or scanlator-filled chapters and
 * change behaviour.
 *
 * **DB numbers.** An unknown chapter number (-1.0, non-finite) becomes a null candidate number
 * rather than a policy rejection, matching what the database actually means by it.
 */
object ReaderChapterWindowAdapter {

    /** The resolved neighbours of the current chapter, in reader entities. */
    data class ReaderChapterNeighbours(
        val previous: ReaderChapter?,
        val next: ReaderChapter?,
    )

    fun neighbours(
        chapters: List<ReaderChapter>,
        current: ReaderChapter,
        skipReadForward: Boolean,
    ): ReaderChapterNeighbours {
        val candidates = chapters.mapIndexed { index, chapter -> candidate(chapter, index) }
        val result = ReaderChapterWindowPolicy.createWindow(
            candidates = candidates,
            currentId = current.chapter.id.toString(),
            policy = ReaderNavigationPolicy(
                order = ReaderChapterOrder.SOURCE,
                skipReadForward = skipReadForward,
            ),
        )
        // A missing current has no window -- there is no head-of-list fallback. Duplicate ids
        // cannot occur within one chapter list, so both failure results collapse to empty.
        if (result !is ReaderChapterWindowResult.Ready) return ReaderChapterNeighbours(null, null)
        val byId = chapters.associateBy { it.chapter.id.toString() }
        return ReaderChapterNeighbours(
            previous = result.window.previous?.let { byId[it.id] },
            next = result.window.next?.let { byId[it.id] },
        )
    }

    private fun candidate(chapter: ReaderChapter, position: Int): ReaderChapterCandidate =
        ReaderChapterCandidate(
            id = chapter.chapter.id.toString(),
            number = chapter.chapter.chapterNumber.takeIf { it.isFinite() && it >= 0.0 },
            title = chapter.chapter.name,
            read = chapter.chapter.read,
            downloaded = false,
            sourceOrder = position.toLong(),
            uploadTimestamp = chapter.chapter.dateUpload,
            scanlator = chapter.chapter.scanlator,
        )
}
