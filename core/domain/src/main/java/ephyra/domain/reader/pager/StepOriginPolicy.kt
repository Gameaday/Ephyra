package ephyra.domain.reader.pager

/**
 * Decides the index a next/previous step should move from, given the pager's settled index and a
 * possibly-stale pending target.
 *
 * **Why this is a pure policy rather than inline logic.** `PagerViewer` tracked "where am I" in four
 * places: `PagerState.currentPage` (Compose), `currentPage` (an object), `pendingTargetIndex` (an
 * `Int?`), and `ReaderViewModel.State.currentPage` (1-based display index). `pendingTargetIndex` was
 * cleared only when `onPageSelected` happened to settle on exactly that index, or on a chapter
 * change. A `moveToNext` that never landed — dropped because the pager was still settling — left a
 * value nothing would clear, and every subsequent step advanced from that phantom index. **The pager
 * then drifted permanently out of sync with the visible page**, and only a chapter change recovered
 * it. That was reported as "next page sometimes skips one".
 *
 * The recovery rule is the interesting part, and it is a judgement rather than an arithmetic: a
 * pending index at or *behind* the settled index describes a request that either landed or was
 * superseded, so it is discarded. A pending index *ahead* is still in flight and is obeyed. This is
 * what makes the state self-correcting instead of merely reset-on-success.
 */
object StepOriginPolicy {

    /**
     * @param settledIndex the pager's actual current index.
     * @param pendingIndex the outstanding request, if any.
     * @param itemCount number of items currently in the pager.
     * @return the index to step from, and whether [pendingIndex] should be cleared.
     */
    fun resolve(settledIndex: Int, pendingIndex: Int?, itemCount: Int): Result {
        // A pending index outside the current items cannot describe anything real: the chapter was
        // likely replaced. Trusting it would step past the end of a shorter list.
        if (pendingIndex != null && (pendingIndex < 0 || pendingIndex >= itemCount)) {
            return Result(settledIndex.coerceIn(0, maxOf(0, itemCount - 1)), clearPending = true)
        }
        if (pendingIndex == null) {
            return Result(settledIndex, clearPending = false)
        }
        // Already at or past the target: the request landed or was superseded. Obeying it would move
        // the user backwards onto a page they had already passed.
        if (pendingIndex <= settledIndex) {
            return Result(settledIndex, clearPending = true)
        }
        return Result(pendingIndex, clearPending = false)
    }

    data class Result(val origin: Int, val clearPending: Boolean)
}
