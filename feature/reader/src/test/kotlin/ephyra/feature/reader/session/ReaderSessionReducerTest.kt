package ephyra.feature.reader.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderSessionReducerTest {
    private val session = ReaderSessionId("session-1")
    private val otherSession = ReaderSessionId("session-2")
    private val chapter = ReaderChapterId("chapter-5")
    private val pages = listOf(ReaderPageId("page-1"), ReaderPageId("page-2"), ReaderPageId("page-3"))

    @Test
    fun `open chapter emits one load and starts in loading chapter`() {
        val result = reduce(
            ReaderSessionState(session),
            ReaderCommand.OpenChapter(session, chapter, initialPageIndex = 1),
        )
        assertEquals(ReaderSessionPhase.LoadingChapter, result.state.phase)
        assertEquals(chapter, result.state.chapterId)
        assertEquals(1, result.state.pageIndex)
        assertEquals(listOf(ReaderEffect.LoadChapter(chapter, 1)), result.effects)
    }

    @Test
    fun `chapter load deduplicates pages and loads the initial page`() {
        val opened = reduce(ReaderSessionState(session), ReaderCommand.OpenChapter(session, chapter))
        val result = reduce(
            opened.state,
            ReaderCommand.ChapterLoaded(session, chapter, pages + pages[0], initialPageIndex = 9),
        )
        assertEquals(2, result.state.pageIndex)
        assertEquals(pages[2], result.state.pageId)
        assertInstanceOf(ReaderSessionPhase.LoadingPage::class.java, result.state.phase)
        assertTrue(result.effects.contains(ReaderEffect.LoadPage(chapter, pages[2])))
        val prefetch = result.effects.first { it is ReaderEffect.PrefetchPages } as ReaderEffect.PrefetchPages
        assertEquals(listOf(pages[1]), prefetch.pageIds)
    }

    @Test
    fun `page load completes the explicit loading phase`() {
        val result = reduce(loadedState().state, ReaderCommand.PageLoaded(session, pages[0]))
        assertEquals(ReaderSessionPhase.Ready, result.state.phase)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `stale session commands do not mutate state`() {
        val ready = readyState()
        val result = ReaderSessionReducer.reduce(ready, ReaderCommand.SelectPage(otherSession, pages[1]))
        assertEquals(ready, result.state)
        assertEquals(ReaderErrorCode.STALE_COMMAND, (result.effects.single() as ReaderEffect.ShowError).error.code)
    }

    @Test
    fun `forward and backward page moves select adjacent pages`() {
        val ready = readyState()
        val forward = ReaderSessionReducer.reduce(ready, ReaderCommand.MoveForward(session))
        val backward = ReaderSessionReducer.reduce(ready, ReaderCommand.MoveBackward(session))
        assertEquals(pages[1], forward.state.pageId)
        assertEquals(ReaderNavigationDirection.FORWARD, forward.state.direction)
        assertEquals(pages[0], backward.state.pageId)
        assertEquals(ReaderNavigationDirection.BACKWARD, backward.state.direction)
    }

    @Test
    fun `page boundaries emit directional chapter navigation`() {
        val first = readyState(initialIndex = 0)
        val last = readyState(initialIndex = 2)
        val previous = ReaderSessionReducer.reduce(first, ReaderCommand.MoveBackward(session))
        val next = ReaderSessionReducer.reduce(last, ReaderCommand.MoveForward(session))
        val previousNavigation = previous.effects.single() as ReaderEffect.NavigateChapter
        val nextNavigation = next.effects.single() as ReaderEffect.NavigateChapter
        assertEquals(ReaderNavigationDirection.BACKWARD, previousNavigation.direction)
        assertEquals(ReaderNavigationDirection.FORWARD, nextNavigation.direction)
    }

    @Test
    fun `page failure can retry only the current page`() {
        val failed = ReaderSessionReducer.reduce(
            loadedState().state,
            ReaderCommand.PageFailed(
                session,
                pages[0],
                ReaderSessionError(ReaderErrorCode.PAGE_LOAD_FAILED),
            ),
        )
        val wrongPage = ReaderSessionReducer.reduce(failed.state, ReaderCommand.RetryPage(session, pages[1]))
        val retry = ReaderSessionReducer.reduce(failed.state, ReaderCommand.RetryPage(session, pages[0]))
        assertEquals(ReaderErrorCode.INVALID_COMMAND, (wrongPage.effects.single() as ReaderEffect.ShowError).error.code)
        assertEquals(ReaderSessionPhase.LoadingPage(pages[0]), retry.state.phase)
        assertEquals(ReaderEffect.RetryPage(chapter, pages[0]), retry.effects.single())
    }

    @Test
    fun `transform lifecycle commits revision and cancel restores committed transform`() {
        val ready = readyState()
        val begun = ReaderSessionReducer.reduce(ready, ReaderCommand.BeginTransform(session))
        val updated = ReaderSessionReducer.reduce(
            begun.state,
            ReaderCommand.UpdateTransform(session, ReaderTransform(scale = 2f, offsetX = 30f)),
        )
        val committed = ReaderSessionReducer.reduce(updated.state, ReaderCommand.CommitTransform(session))
        val cancelled = ReaderSessionReducer.reduce(begun.state, ReaderCommand.CancelTransform(session))
        assertEquals(2f, committed.state.transform.scale)
        assertEquals(1L, committed.state.transform.revision)
        assertEquals(
            ReaderEffect.PersistViewport(chapter, pages[0], committed.state.transform),
            committed.effects.single(),
        )
        assertEquals(ready.transform, cancelled.state.transform)
    }

    @Test
    fun `invalid transforms are rejected without changing committed state`() {
        val ready = readyState()
        val begun = ReaderSessionReducer.reduce(ready, ReaderCommand.BeginTransform(session))
        val result = ReaderSessionReducer.reduce(
            begun.state,
            ReaderCommand.UpdateTransform(session, ReaderTransform(scale = Float.NaN)),
        )
        assertEquals(ready.transform, result.state.transform)
        assertEquals(ReaderErrorCode.INVALID_COMMAND, (result.effects.single() as ReaderEffect.ShowError).error.code)
    }

    @Test
    fun `double tap commits a new transform revision`() {
        val result = ReaderSessionReducer.reduce(
            readyState(),
            ReaderCommand.DoubleTap(session, ReaderTransform(scale = 2.5f, offsetY = -10f)),
        )
        assertEquals(2.5f, result.state.transform.scale)
        assertEquals(1L, result.state.transform.revision)
        assertTrue(result.effects.single() is ReaderEffect.PersistViewport)
    }

    @Test
    fun `restore validates identity and reissues required work`() {
        val snapshot = readyState().copy(transform = ReaderTransform(scale = 1.5f))
        val result = ReaderSessionReducer.reduce(
            ReaderSessionState(session),
            ReaderCommand.RestoreState(session, snapshot),
        )
        val invalid = ReaderSessionReducer.reduce(
            ReaderSessionState(session),
            ReaderCommand.RestoreState(session, snapshot.copy(sessionId = otherSession)),
        )
        assertEquals(snapshot, result.state)
        assertTrue(result.effects.any { it is ReaderEffect.LoadPage })
        assertEquals(ReaderErrorCode.INVALID_COMMAND, (invalid.effects.single() as ReaderEffect.ShowError).error.code)
    }

    @Test
    fun `reader session owns reducer state and effects`() {
        val stateOwner = ReaderSession(ReaderSessionState(session))

        val transition = stateOwner.dispatch(ReaderCommand.OpenChapter(session, chapter))

        assertEquals(transition.state, stateOwner.state)
        assertEquals(ReaderSessionPhase.LoadingChapter, stateOwner.state.phase)
    }

    @Test
    fun `opening another chapter persists and clears the previous chapter first`() {
        val ready = readyState()
        val nextChapter = ReaderChapterId("chapter-6")
        val result = ReaderSessionReducer.reduce(ready, ReaderCommand.OpenChapter(session, nextChapter))
        assertEquals(
            listOf(
                ReaderEffect.PersistProgress(chapter, pages[0], 0),
                ReaderEffect.ClearWorkingResources,
                ReaderEffect.LoadChapter(nextChapter, 0),
            ),
            result.effects,
        )
    }

    @Test
    fun `reopening a failed chapter clears failed resources and retries loading`() {
        val opened = ReaderSessionReducer.reduce(
            ReaderSessionState(session),
            ReaderCommand.OpenChapter(session, chapter, initialPageIndex = 2),
        )
        val failed = ReaderSessionReducer.reduce(
            opened.state,
            ReaderCommand.ChapterFailed(
                session,
                chapter,
                ReaderSessionError(ReaderErrorCode.CHAPTER_LOAD_FAILED),
            ),
        )
        val reopened = ReaderSessionReducer.reduce(
            failed.state,
            ReaderCommand.OpenChapter(session, chapter, initialPageIndex = 2),
        )
        assertEquals(
            listOf(ReaderEffect.ClearWorkingResources, ReaderEffect.LoadChapter(chapter, 2)),
            reopened.effects,
        )
        assertEquals(ReaderSessionPhase.LoadingChapter, reopened.state.phase)
    }

    @Test
    fun `transform commands are rejected unless a page is ready`() {
        val closed = ReaderSessionState(session)
        val loading = loadedState().state
        val closedDoubleTap = ReaderSessionReducer.reduce(
            closed,
            ReaderCommand.DoubleTap(session, ReaderTransform(scale = 2f)),
        )
        val loadingTransform = ReaderSessionReducer.reduce(loading, ReaderCommand.BeginTransform(session))
        assertEquals(ReaderErrorCode.INVALID_COMMAND, errorCode(closedDoubleTap))
        assertEquals(ReaderErrorCode.INVALID_COMMAND, errorCode(loadingTransform))
        assertEquals(closed, closedDoubleTap.state)
        assertEquals(loading, loadingTransform.state)
    }

    @Test
    fun `retry is rejected unless the requested page is the failed page`() {
        val ready = readyState()
        val result = ReaderSessionReducer.reduce(ready, ReaderCommand.RetryPage(session, pages[0]))
        assertEquals(ReaderErrorCode.INVALID_COMMAND, errorCode(result))
        assertEquals(ready, result.state)
    }

    @Test
    fun `restore rejects incoherent page index and interrupted gesture drafts`() {
        val snapshot = readyState()
        val wrongIndex = ReaderSessionReducer.reduce(
            ReaderSessionState(session),
            ReaderCommand.RestoreState(session, snapshot.copy(pageIndex = 2)),
        )
        val draft = ReaderSessionReducer.reduce(
            ReaderSessionState(session),
            ReaderCommand.RestoreState(
                session,
                snapshot.copy(draftTransform = ReaderTransform(scale = 2f)),
            ),
        )
        assertEquals(ReaderErrorCode.INVALID_COMMAND, errorCode(wrongIndex))
        assertEquals(ReaderErrorCode.INVALID_COMMAND, errorCode(draft))
        assertEquals(ReaderSessionState(session), wrongIndex.state)
        assertEquals(ReaderSessionState(session), draft.state)
    }

    @Test
    fun `close persists progress and clears resources`() {
        val result = ReaderSessionReducer.reduce(readyState(), ReaderCommand.CloseChapter(session))
        assertEquals(ReaderSessionPhase.Closed, result.state.phase)
        assertTrue(result.effects.contains(ReaderEffect.PersistProgress(chapter, pages[0], 0)))
        assertTrue(result.effects.contains(ReaderEffect.ClearWorkingResources))
    }

    private fun errorCode(transition: ReaderTransition): ReaderErrorCode =
        (transition.effects.single() as ReaderEffect.ShowError).error.code

    private fun reduce(state: ReaderSessionState, command: ReaderCommand): ReaderTransition =
        ReaderSessionReducer.reduce(state, command)

    private fun loadedState(initialIndex: Int = 0): ReaderTransition {
        val opened = ReaderSessionReducer.reduce(
            ReaderSessionState(session),
            ReaderCommand.OpenChapter(session, chapter),
        )
        return ReaderSessionReducer.reduce(
            opened.state,
            ReaderCommand.ChapterLoaded(session, chapter, pages, initialIndex),
        )
    }

    private fun readyState(initialIndex: Int = 0): ReaderSessionState {
        val loading = loadedState(initialIndex).state
        val command = ReaderCommand.PageLoaded(session, pages[initialIndex])
        return ReaderSessionReducer.reduce(loading, command).state
    }
}
