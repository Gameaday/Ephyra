package ephyra.feature.reader.viewer

/**
 * One-shot "position this chapter" tracker shared by [PagerViewer], [WebtoonViewer], and the
 * Compose webtoon reader so the reset/claim logic is written once instead of three times.
 *
 * Semantics match the previous per-viewer fields exactly:
 * - The active chapter id resets the marker whenever the chapter changes (revisits re-position).
 * - [claimPosition] returns true at most once per chapter id and only when [canPosition] is
 *   true (pages resolved / not already viewing), so late page arrivals can't re-yank scroll.
 */
class ChapterPositionTracker {

    private var activeChapterId: Long? = null
    private var positionedChapterId: Long? = null

    /**
     * Resets the marker if [chapterId] differs from the active chapter, then returns true
     * exactly once for each chapter id when [canPosition] is true.
     */
    fun claimPosition(chapterId: Long, canPosition: Boolean = true): Boolean {
        if (activeChapterId != chapterId) {
            activeChapterId = chapterId
            positionedChapterId = null
        }
        if (!canPosition || positionedChapterId == chapterId) return false
        positionedChapterId = chapterId
        return true
    }
}
