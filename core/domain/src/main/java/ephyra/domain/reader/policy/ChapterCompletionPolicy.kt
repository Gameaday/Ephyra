package ephyra.domain.reader.policy

/**
 * Decides when a chapter counts as finished.
 *
 * Previously written twice, inline in `ReaderViewModel` — once on the page-progress path and once
 * in `checkChapterCompletion`. Two copies drifted in their null-handling, and neither had a test
 * while deciding whether a user's reading history is correct.
 *
 * The rule itself is sound: a merged chapter absorbs its siblings' pages, those are hidden, and once
 * the reader passes the last *visible* page nothing is left to show. The defects were in the edges:
 *
 *  - **Vacuous tail.** `[].all { it.isHidden }` is `true`, so a `pageIndex` at or past the end
 *    satisfied "no visible pages follow" on an *empty* range and completed the chapter without
 *    the reader reaching any content. [isChapterComplete] rejects an out-of-range index.
 *  - **Unresolved chapters.** The page list populates before the chapter finishes loading, so a
 *    decision from the list alone can be taken while pages are still being fetched.
 *  - **Direction.** Moving backward never marks a chapter read; swiping back across a boundary is
 *    not completion.
 *
 * Pure and total — no I/O, no Android types, no dependence on the reader's page model — so the rule
 * is stated once and tested exhaustively.
 */
object ChapterCompletionPolicy {

    /**
     * @param pageIndex index of the page the reader has just reached.
     * @param pageCount total pages in the chapter, including hidden ones.
     * @param visiblePagesAfter how many pages *after* [pageIndex] are visible to the reader. A
     *   merged chapter has absorbed pages counted here as zero, because they are not shown.
     * @param chapterResolved whether the chapter has finished loading. A chapter that has not
     *   resolved cannot be judged complete: its remaining pages are not yet known.
     * @param movingForward whether the reader arrived here by moving forward. Backward movement
     *   never completes a chapter.
     */
    fun isChapterComplete(
        pageIndex: Int,
        pageCount: Int,
        visiblePagesAfter: Int,
        chapterResolved: Boolean,
        movingForward: Boolean,
    ): Boolean {
        // Backward movement is navigation, not completion. Checked first because it is the
        // cheapest and the least often true of the non-content conditions.
        if (!movingForward) return false

        // A chapter that is still loading has an unknown tail, so "nothing visible follows" is not
        // evidence of completion — it is evidence of not knowing yet.
        if (!chapterResolved) return false

        // Guards the vacuous case directly. An index outside the page list means the caller's
        // view of the chapter disagrees with the policy's, and the safe answer is "not complete"
        // rather than a chapter silently marked read.
        if (pageCount <= 0 || pageIndex < 0 || pageIndex >= pageCount) return false

        // The reader is on the literally last page, or every page after it is absorbed or blocked
        // and therefore never shown. Either way there is no further visible content.
        return pageIndex == pageCount - 1 || visiblePagesAfter == 0
    }

    /**
     * Counts the pages after [pageIndex] that the reader would actually be shown.
     *
     * Lives here rather than at the call sites so the tail is measured once. The previous inline
     * copies each recomputed it, and disagreed about null handling.
     *
     * @param isHidden whether the page at [index] is hidden (absorbed by a merge, or blocked by
     *   a filter). Must only be consulted for indices inside the list.
     */
    fun countVisiblePagesAfter(
        pageIndex: Int,
        pageCount: Int,
        isHidden: (index: Int) -> Boolean,
    ): Int {
        if (pageIndex < 0 || pageIndex >= pageCount) return 0
        var visible = 0
        for (index in (pageIndex + 1) until pageCount) {
            if (!isHidden(index)) visible++
        }
        return visible
    }
}
