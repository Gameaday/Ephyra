package ephyra.feature.reader.loading

import ephyra.core.common.util.system.logcat
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CancellationException

/**
 * The platform work one chapter load asks for. The view model implements this; the coordinator
 * drives it. Same shape as the reader session's effect handler: an effect names an intent, and
 * one type implements the translation once.
 */
interface ChapterLoadingEffectHandler {
    /** Opens the reader session for [chapter] (the RDR-001 chapter-open transition). */
    suspend fun openSessionForChapter(chapter: ReaderChapter)

    /**
     * Loads [chapter]'s pages. Returns false when no loader exists - the open path's callers
     * guarantee one, and the preload path stops.
     */
    suspend fun loadChapterPages(chapter: ReaderChapter, isPreloadOnly: Boolean): Boolean

    /** Wires the filtered-page callback that asks the viewer to rebuild its item list. */
    fun wireFilteredPageReload(chapter: ReaderChapter)

    /**
     * Queues the whole chapter for background processing (the B-010 call site, audit F3). The
     * policy - every page at the lowest background priority - is described at the
     * implementation; the coordinator owns the decision to call it.
     */
    fun queueBackgroundChapterPreload(chapter: ReaderChapter)

    /** Selects the prev/next window and installs the chapter as current. */
    suspend fun installViewerChapters(chapter: ReaderChapter)

    /** Upserts the opened-chapter history row (skipped in incognito mode). */
    suspend fun recordOpenedChapterHistory(chapter: ReaderChapter)

    /** Restarts the per-chapter read timer. */
    fun restartReadTimer()

    /** Reports the loaded pages to the reader session. */
    fun reportChapterLoadedToSession(chapter: ReaderChapter)

    /**
     * For an online-preload candidate: waits for the download cache and promotes the chapter
     * to [ReaderChapter.State.Wait] when its download exists. Returns false when the manga is
     * gone and the preload must stop.
     */
    suspend fun promoteDownloadedPreload(chapter: ReaderChapter): Boolean

    /** Starts the first [pages] images of the adjacent chapter. */
    fun startAdjacentImagePreload(chapter: ReaderChapter, pages: Int)

    /** Asks the UI to rebuild the viewer (a preload changed visible state). */
    fun requestViewerReload()
}

/**
 * Owns the chapter-loading seam of the reader (R-A, Stage 3 seam 1 of the reader modular
 * redesign, 2026-11-08): the open/preload ordering and the buffer-health decisions the view
 * model previously inlined - and, for the buffer count, duplicated between preload() and
 * tryPreloadNextChapterImages(), the exact drift the redesign audit's F1/F3 point at.
 *
 * The view model drives; this coordinator decides; the effect handler (the view model)
 * executes. Not pure like the session coordinator - it sequences suspend effects and catches
 * cancellation - but every decision inside it is a table-tested rule:
 *
 *  - open ordering: session open -> page load -> filter wiring -> background queue -> window
 *    install -> history -> read timer -> session report (the order loadChapter already had);
 *  - preload eligibility: Loaded/Loading never preload; a downloaded online chapter is
 *    promoted to Wait; only Wait/Error attempt;
 *  - opportunistic prefetch: the next chapter's images start only while fewer than
 *    [preloadAheadPages] pages remain pending ahead of the reading position - one rule where
 *    there were two copies;
 *  - adjacent start: next starts at the beginning, previous at the end, with the clamp-me
 *    fallback index when pages are not yet known.
 *
 * **No behaviour change:** every rule is the code that already ran, moved, not rethought.
 */
class ChapterLoadingCoordinator(
    private val effects: ChapterLoadingEffectHandler,
    val preloadAheadPages: Int,
) {
    /** The open path: the sequence the view model's loadChapter used to inline. */
    suspend fun openChapter(chapter: ReaderChapter) {
        effects.openSessionForChapter(chapter)
        if (!effects.loadChapterPages(chapter, isPreloadOnly = false)) return
        effects.wireFilteredPageReload(chapter)
        effects.queueBackgroundChapterPreload(chapter)
        effects.installViewerChapters(chapter)
        effects.recordOpenedChapterHistory(chapter)
        effects.restartReadTimer()
        effects.reportChapterLoadedToSession(chapter)
    }

    /**
     * The preload path: eligibility, the downloaded-chapter promotion, the single-worker load,
     * and the opportunistic next-chapter image prefetch. [currentPages]/[currentPageIndex]
     * describe the reading position the buffer-health gate reads.
     */
    suspend fun preloadChapter(
        chapter: ReaderChapter,
        currentPages: List<ReaderPage>?,
        currentPageIndex: Int,
    ) {
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) {
            return
        }

        if (chapter.pageLoader?.isLocal == false) {
            if (!effects.promoteDownloadedPreload(chapter)) return
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        val loaded = try {
            logcat { "Preloading ${chapter.chapter.url}" }
            // Load with isPreloadOnly=true so the underlying HttpPageLoader spawns only a single
            // background worker. This prevents the speculative preload from competing with the
            // active chapter's downloads for network bandwidth.
            effects.loadChapterPages(chapter, isPreloadOnly = true)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            return
        }
        if (!loaded) return

        // Only proactively start downloading the first few images of the preloaded chapter
        // if the current chapter's buffer is healthy. If the user is outpacing the current
        // buffer (i.e. there are still many unloaded pages ahead of the reading position),
        // skip image preloading so all available bandwidth stays with the active chapter.
        // This makes cross-chapter image prefetch purely opportunistic.
        val pendingAhead = pendingAheadOf(currentPages.orEmpty().map { it.status }, currentPageIndex)
        if (pendingAhead < preloadAheadPages) {
            logcat { "Current buffer healthy ($pendingAhead pending ahead); starting next-chapter image preload" }
            effects.startAdjacentImagePreload(chapter, preloadAheadPages)
        } else {
            logcat {
                "Buffer thin ($pendingAhead pending ahead >= $preloadAheadPages threshold); " +
                    "deferring next-chapter image preload"
            }
        }

        effects.requestViewerReload()
    }

    /**
     * Whether the next chapter's images may start preloading now that another page was
     * consumed: only a loaded next chapter with a healthy forward buffer qualifies.
     */
    fun shouldPreloadNextChapterImages(
        nextChapter: ReaderChapter?,
        currentPageStatuses: List<Page.State>,
        currentPageIndex: Int,
    ): Boolean {
        if (nextChapter == null) return false
        if (nextChapter.state !is ReaderChapter.State.Loaded) return false
        return pendingAheadOf(currentPageStatuses, currentPageIndex) < preloadAheadPages
    }

    /**
     * Pages still pending in the forward window (reading position -> [preloadAheadPages] ahead).
     * The one implementation of the count preload() and tryPreloadNextChapterImages()
     * previously each carried.
     */
    fun pendingAheadOf(pageStatuses: List<Page.State>, currentPageIndex: Int): Int =
        pageStatuses
            .asSequence()
            .drop(currentPageIndex + 1)
            .take(preloadAheadPages)
            .count {
                it == Page.State.Queue || it == Page.State.LoadPage || it == Page.State.DownloadImage
            }

    /** Direction-aware start for a toolbar transition: next begins, previous ends. */
    fun adjacentStartPlan(startFromEnd: Boolean, knownPagesLastIndex: Int?): AdjacentChapterStart =
        AdjacentChapterStart(
            startingAtBeginning = !startFromEnd,
            startFromEnd = startFromEnd,
            // Best effort explicit end target when pages are already known (e.g., preloaded).
            // Otherwise use a large index which is clamped to lastIndex by the viewers.
            requestedPage = if (startFromEnd) {
                knownPagesLastIndex ?: FALLBACK_LAST_PAGE_INDEX
            } else {
                0
            },
        )

    /** The resolved start of an adjacent chapter transition. */
    data class AdjacentChapterStart(
        val startingAtBeginning: Boolean,
        val startFromEnd: Boolean,
        val requestedPage: Int,
    )

    private companion object {
        /** Fallback end target when the previous chapter's pages are not yet known. */
        const val FALLBACK_LAST_PAGE_INDEX = Int.MAX_VALUE
    }
}
