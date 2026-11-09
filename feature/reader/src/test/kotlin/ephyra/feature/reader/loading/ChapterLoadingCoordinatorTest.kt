package ephyra.feature.reader.loading

import ephyra.domain.chapter.model.Chapter
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the ordering and the decision tables of the chapter-loading coordinator (R-A, Stage 3
 * seam 1 of the reader modular redesign, 2026-11-08).
 *
 * The handler records call names, so the bodies under test are which effects run, in what
 * order, under which inputs - the view model's implementations are the moved production code
 * and are exercised by the app suite; what must not drift is the sequencing and the two gates
 * (eligibility, buffer health) that preload decisions pass through. The forward-buffer count
 * previously existed twice (preload() and tryPreloadNextChapterImages()); both consumers now
 * read this coordinator, and the tables here are what keeps the single rule honest.
 *
 * runBlocking rather than runTest: the coordinator launches nothing - it sequences suspend
 * effects the caller already scopes - so virtual-time control would buy nothing.
 */
class ChapterLoadingCoordinatorTest {

    private val calls = mutableListOf<String>()

    private var loadPagesResult = true

    private var failWith: Throwable? = null

    private val handler = object : ChapterLoadingEffectHandler {
        override suspend fun openSessionForChapter(chapter: ReaderChapter) {
            calls += "openSession"
        }

        override suspend fun loadChapterPages(chapter: ReaderChapter, isPreloadOnly: Boolean): Boolean {
            calls += "loadPages:$isPreloadOnly"
            failWith?.let { throw it }
            return loadPagesResult
        }

        override fun wireFilteredPageReload(chapter: ReaderChapter) {
            calls += "wireFilter"
        }

        override fun queueBackgroundChapterPreload(chapter: ReaderChapter) {
            calls += "queueBackground"
        }

        override suspend fun installViewerChapters(chapter: ReaderChapter) {
            calls += "install"
        }

        override suspend fun recordOpenedChapterHistory(chapter: ReaderChapter) {
            calls += "history"
        }

        override fun restartReadTimer() {
            calls += "restartTimer"
        }

        override fun reportChapterLoadedToSession(chapter: ReaderChapter) {
            calls += "reportLoaded"
        }

        override suspend fun promoteDownloadedPreload(chapter: ReaderChapter): Boolean {
            calls += "promote"
            return true
        }

        override fun startAdjacentImagePreload(chapter: ReaderChapter, pages: Int) {
            calls += "startImages:$pages"
        }

        override fun requestViewerReload() {
            calls += "reloadViewer"
        }
    }

    private val coordinator = ChapterLoadingCoordinator(handler, preloadAheadPages = 4)

    @BeforeEach
    fun reset() {
        calls.clear()
        loadPagesResult = true
        failWith = null
    }

    private fun chapter(state: ReaderChapter.State): ReaderChapter =
        ReaderChapter(Chapter.create()).apply { this.state = state }

    private fun page(status: Page.State): ReaderPage =
        ReaderPage(0, "/a.jpg").apply { this.status = status }

    @Test
    fun `opening a chapter sequences the open path in the loadChapter order`() = runBlocking {
        coordinator.openChapter(chapter(ReaderChapter.State.Wait))

        assertEquals(
            listOf(
                "openSession",
                "loadPages:false",
                "wireFilter",
                "queueBackground",
                "install",
                "history",
                "restartTimer",
                "reportLoaded",
            ),
            calls,
        )
        Unit
    }

    @Test
    fun `opening stops without a loader instead of running the tail`() = runBlocking {
        loadPagesResult = false

        coordinator.openChapter(chapter(ReaderChapter.State.Wait))

        assertEquals(listOf("openSession", "loadPages:false"), calls)
        Unit
    }

    @Test
    fun `a loaded or loading chapter never preloads`() = runBlocking {
        coordinator.preloadChapter(chapter(ReaderChapter.State.Loaded(emptyList())), null, 0)
        coordinator.preloadChapter(chapter(ReaderChapter.State.Loading), null, 0)

        assertTrue(calls.isEmpty(), "Loaded/Loading chapters are past the preload gate before any effect runs")
        Unit
    }

    @Test
    fun `an error chapter still attempts its preload`() = runBlocking {
        coordinator.preloadChapter(chapter(ReaderChapter.State.Error(IllegalStateException())), null, 0)

        assertEquals(listOf("loadPages:true", "startImages:4", "reloadViewer"), calls)
        Unit
    }

    @Test
    fun `a healthy forward buffer lets the next chapter's images start`() = runBlocking {
        val pages = listOf(
            page(Page.State.Ready),
            page(Page.State.Queue),
            page(Page.State.Ready),
            page(Page.State.Ready),
        )

        coordinator.preloadChapter(chapter(ReaderChapter.State.Wait), pages, 0)

        assertEquals(listOf("loadPages:true", "startImages:4", "reloadViewer"), calls)
        Unit
    }

    @Test
    fun `a thin forward buffer defers the image preload but still asks for a reload`() = runBlocking {
        val pages = listOf(
            page(Page.State.Ready),
            page(Page.State.Queue),
            page(Page.State.Queue),
            page(Page.State.Queue),
            page(Page.State.Queue),
        )

        coordinator.preloadChapter(chapter(ReaderChapter.State.Wait), pages, 0)

        assertEquals(listOf("loadPages:true", "reloadViewer"), calls)
        Unit
    }

    @Test
    fun `cancellation during the preload propagates instead of being swallowed`() {
        failWith = CancellationException("cancelled")

        var caught: Throwable? = null
        runBlocking {
            try {
                coordinator.preloadChapter(chapter(ReaderChapter.State.Wait), null, 0)
            } catch (e: CancellationException) {
                caught = e
            }
        }

        assertTrue(caught != null, "cancellation must reach the caller; the swallow rule is for ordinary failures")
    }

    @Test
    fun `an ordinary failure is swallowed and never asks for a reload`() {
        failWith = IllegalStateException("boom")

        runBlocking {
            coordinator.preloadChapter(chapter(ReaderChapter.State.Wait), null, 0)
        }

        assertEquals(listOf("loadPages:true"), calls)
    }

    @Test
    fun `next-chapter image preload asks the same buffer-health rule`() {
        val healthy = listOf(Page.State.Ready, Page.State.Ready)
        val thin = listOf(
            Page.State.Queue,
            Page.State.Queue,
            Page.State.Queue,
            Page.State.Queue,
            Page.State.Queue,
        )
        val loaded = chapter(ReaderChapter.State.Loaded(emptyList()))

        assertFalse(coordinator.shouldPreloadNextChapterImages(null, healthy, 0))
        assertFalse(coordinator.shouldPreloadNextChapterImages(chapter(ReaderChapter.State.Wait), healthy, 0))
        assertTrue(coordinator.shouldPreloadNextChapterImages(loaded, healthy, 0))
        assertFalse(coordinator.shouldPreloadNextChapterImages(loaded, thin, 0))
    }

    @Test
    fun `pending-ahead counts the unfinished window ahead of the reading position`() {
        val statuses = listOf(
            Page.State.Ready,
            Page.State.Queue,
            Page.State.Ready,
            Page.State.LoadPage,
            Page.State.DownloadImage,
            Page.State.Ready,
        )

        assertEquals(0, coordinator.pendingAheadOf(statuses, 5))
        assertEquals(3, coordinator.pendingAheadOf(statuses, 0))
        assertEquals(0, coordinator.pendingAheadOf(emptyList(), 0))
    }

    @Test
    fun `an adjacent start is direction-aware and falls back when pages are unknown`() {
        val fromBeginning = coordinator.adjacentStartPlan(startFromEnd = false, knownPagesLastIndex = 9)
        assertEquals(ChapterLoadingCoordinator.AdjacentChapterStart(true, false, 0), fromBeginning)

        val fromEnd = coordinator.adjacentStartPlan(startFromEnd = true, knownPagesLastIndex = 9)
        assertEquals(ChapterLoadingCoordinator.AdjacentChapterStart(false, true, 9), fromEnd)

        val fromEndUnknown = coordinator.adjacentStartPlan(startFromEnd = true, knownPagesLastIndex = null)
        assertEquals(ChapterLoadingCoordinator.AdjacentChapterStart(false, true, Int.MAX_VALUE), fromEndUnknown)
    }
}
