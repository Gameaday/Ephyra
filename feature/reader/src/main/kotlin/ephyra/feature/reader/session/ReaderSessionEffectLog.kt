package ephyra.feature.reader.session

/**
 * Records the effects a [ReaderSessionCoordinator] dispatches, in order, without executing them.
 *
 * **Why acknowledge rather than execute (A2 wiring step).** Every effect the session emits names
 * work the dispatching path in the view model already performs: the chapter load is in flight
 * when OpenChapter is dispatched, progress persists through updateChapterProgress and
 * updateHistory on the same seams, and page bytes are bounded by the chapter window ref/unref.
 * Re-issuing the effect would duplicate the load, the persist or the prefetch, so this handler
 * acknowledges each request and the log becomes the seam the replacement resource owners
 * delegate through when they land. An entry here is therefore a claim about ordering, not a
 * write.
 *
 * Synchronized because the view model dispatches from both the main and the IO dispatchers.
 */
class ReaderSessionEffectLog : ReaderSessionEffectHandler {
    private val lock = Any()

    /** The acknowledged effects, in dispatch order. */
    private val recorded = mutableListOf<String>()

    val effects: List<String> get() = synchronized(lock) { recorded.toList() }

    fun clear() = synchronized(lock) { recorded.clear() }

    override fun loadChapter(chapterId: ReaderChapterId, initialPageIndex: Int) {
        record("loadChapter:" + chapterId.value + ":" + initialPageIndex)
    }

    override fun loadPage(chapterId: ReaderChapterId, pageId: ReaderPageId) {
        record("loadPage:" + pageId.value)
    }

    override fun prefetchPages(chapterId: ReaderChapterId, pageIds: List<ReaderPageId>) {
        record("prefetch:" + pageIds.size)
    }

    override fun retryPage(chapterId: ReaderChapterId, pageId: ReaderPageId) {
        record("retryPage:" + pageId.value)
    }

    override fun navigateChapter(chapterId: ReaderChapterId, direction: ReaderNavigationDirection) {
        record("navigateChapter:" + direction)
    }

    override fun persistProgress(chapterId: ReaderChapterId, pageId: ReaderPageId, pageIndex: Int) {
        record("persistProgress:" + pageIndex)
    }

    override fun persistViewport(chapterId: ReaderChapterId, pageId: ReaderPageId, transform: ReaderTransform) {
        record("persistViewport")
    }

    override fun clearWorkingResources() {
        record("clearWorkingResources")
    }

    override fun showError(error: ReaderSessionError) {
        record("showError:" + error.code)
    }

    private fun record(entry: String) {
        synchronized(lock) { recorded += entry }
    }
}
