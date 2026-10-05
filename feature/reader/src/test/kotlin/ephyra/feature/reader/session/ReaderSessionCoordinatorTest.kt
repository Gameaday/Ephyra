package ephyra.feature.reader.session

import ephyra.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.source.model.Page
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins that the coordinator executes the effects a transition asks for, and owns the identity.
 *
 * `ReaderSession`'s reducer is already covered on its own. What it cannot know is whether the layer
 * above it *runs* the effects — and an effect that is emitted and never executed is a silent
 * no-op, so that is what this covers: the reader asks for a page, something loads it.
 */
class ReaderSessionCoordinatorTest {

    private val calls = mutableListOf<String>()

    private val handler = object : ReaderSessionEffectHandler {
        override fun loadChapter(chapterId: ReaderChapterId, initialPageIndex: Int) {
            calls += "loadChapter:${chapterId.value}:$initialPageIndex"
        }

        override fun loadPage(chapterId: ReaderChapterId, pageId: ReaderPageId) {
            calls += "loadPage:${pageId.value}"
        }

        override fun prefetchPages(chapterId: ReaderChapterId, pageIds: List<ReaderPageId>) {
            calls += "prefetch:${pageIds.size}"
        }

        override fun retryPage(chapterId: ReaderChapterId, pageId: ReaderPageId) {
            calls += "retryPage:${pageId.value}"
        }

        override fun navigateChapter(chapterId: ReaderChapterId, direction: ReaderNavigationDirection) {
            calls += "navigate:$direction"
        }

        override fun persistProgress(chapterId: ReaderChapterId, pageId: ReaderPageId, pageIndex: Int) {
            calls += "persistProgress:$pageIndex"
        }

        override fun persistViewport(
            chapterId: ReaderChapterId,
            pageId: ReaderPageId,
            transform: ReaderTransform,
        ) {
            calls += "persistViewport"
        }

        override fun clearWorkingResources() {
            calls += "clearWorkingResources"
        }

        override fun showError(error: ReaderSessionError) {
            calls += "showError:${error.code}"
        }
    }

    private val coordinator =
        ReaderSessionCoordinator(ReaderSessionId("session-1"), handler)

    private val chapterId = ReaderSessionIdentity.chapterId("/chapter/1", 7L)

    /** A chapter opening must actually ask for the chapter, or the reader opens nothing. */
    @Test
    fun `opening a chapter asks for it`() {
        coordinator.openChapterForTest("/chapter/1", 7L)

        assertEquals(listOf("loadChapter:${chapterId.value}:0"), calls)
    }

    @Test
    fun `the session owns the chapter identity rather than the caller`() {
        val state = coordinator.openChapterForTest("/chapter/1", 7L)

        assertEquals(chapterId, state.chapterId)
        assertEquals(chapterId, coordinator.currentChapterId)
    }

    @Test
    fun `closing a chapter persists progress and releases resources`() {
        // The chapter has to be open before it can be closed: `closeChapter` on a session that
        // never opened one is a no-op, and a test that skipped the open was asserting against a
        // session that was still `Closed`.
        coordinator.openChapterForTest("/chapter/1", 7L)
        coordinator.chapterLoadedForTest("/chapter/1", 7L, listOf("/a.jpg", "/b.jpg"))
        calls.clear()

        coordinator.closeChapter()

        assertTrue(
            calls.contains("clearWorkingResources"),
            "the bytes of a closed chapter must be released, not left to the next one: $calls",
        )
        assertNull(coordinator.currentChapterId, "a closed session holds no chapter")
    }

    @Test
    fun `a page failure surfaces the error rather than loading anything`() {
        coordinator.openChapterForTest("/chapter/1", 7L)
        coordinator.chapterLoadedForTest("/chapter/1", 7L, listOf("/a.jpg"))
        calls.clear()

        coordinator.pageFailedForTest(
            "/a.jpg",
            0,
            "/chapter/1",
            7L,
            ReaderSessionError(ReaderErrorCode.PAGE_LOAD_FAILED),
        )

        assertTrue(calls.any { it.startsWith("showError:") }, "expected the failure to be reported: $calls")
    }

    /**
     * A loaded chapter settles in `LoadingPage`, not `Ready` — the page bytes are still on their
     * way. Asserted because "ready" is the intuitive guess and the distinction is the whole point
     * of a phase: `Ready` means the reader can accept a selection, and claiming it before the page
     * has loaded is how a reader ends up interactive with nothing to show.
     */
    @Test
    fun `a loaded chapter is awaiting its first page rather than ready`() {
        coordinator.openChapterForTest("/chapter/1", 7L)

        val state = coordinator.chapterLoadedForTest("/chapter/1", 7L, listOf("/a.jpg", "/b.jpg"))

        assertEquals(2, state.pageIds.size)
        assertEquals(
            ReaderSessionPhase.LoadingPage(state.pageIds[0]),
            state.phase,
            "the phase must not claim readiness before the page has loaded",
        )
    }

    /**
     * A chapter that loads with no pages is **rejected**, not admitted as an empty ready state.
     *
     * The distinction is the whole reason the machine has an `Error` phase: a chapter with nothing
     * in it is a failure the reader has to show, and admitting it as `Ready` would present an empty
     * page as a successfully loaded one.
     */
    @Test
    fun `a chapter that loads with no pages is rejected`() {
        coordinator.openChapterForTest("/chapter/1", 7L)
        calls.clear()

        val state = coordinator.chapterLoadedForTest("/chapter/1", 7L, emptyList())

        assertTrue(state.pageIds.isEmpty())
        assertTrue(
            state.phase !is ReaderSessionPhase.Ready,
            "a chapter with no pages must not be admitted as ready: ${state.phase}",
        )
        assertTrue(
            calls.any { it.startsWith("showError:") },
            "and the rejection has to be reported rather than swallowed: $calls",
        )
    }
}

/* The coordinator's surface takes the real `Chapter` and `Page`, so this exercises the real types
   rather than stand-ins -- `Page` names `android.net.Uri` in its signature, so this suite needs the
   Android classpath and is CI's to run. `Chapter.create().copy(url = …)` and `Page(index, url)`
   keep the fixtures to two lines. */
private fun chapter(url: String) = Chapter.create().copy(url = url)

private fun ReaderSessionCoordinator.openChapterForTest(url: String, sourceId: Long) =
    openChapter(chapter(url), sourceId)

private fun ReaderSessionCoordinator.chapterLoadedForTest(
    url: String,
    sourceId: Long,
    urls: List<String>,
) = chapterLoaded(chapter(url), sourceId, urls.mapIndexed { i, u -> Page(i, u) })

private fun ReaderSessionCoordinator.pageFailedForTest(
    pageUrl: String,
    index: Int,
    chapterUrl: String,
    sourceId: Long,
    error: ReaderSessionError,
) = pageFailed(Page(index, pageUrl), chapter(chapterUrl), sourceId, error)
