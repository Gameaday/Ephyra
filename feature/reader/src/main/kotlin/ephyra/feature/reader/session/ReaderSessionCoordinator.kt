package ephyra.feature.reader.session

import ephyra.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.source.model.Page

/**
 * Executes the effects a [ReaderTransition] asks for.
 *
 * **Why this is an interface and not a lambda bag.** An effect names an *intent* — load this page,
 * persist this progress, clear these bytes — and only the caller knows what that means concretely.
 * Keeping the translation in one type means the session's effect set is a closed list the caller
 * implements once, rather than a `when` scattered across every call site that dispatches a command.
 */
interface ReaderSessionEffectHandler {
    fun loadChapter(chapterId: ReaderChapterId, initialPageIndex: Int)
    fun loadPage(chapterId: ReaderChapterId, pageId: ReaderPageId)
    fun prefetchPages(chapterId: ReaderChapterId, pageIds: List<ReaderPageId>)
    fun retryPage(chapterId: ReaderChapterId, pageId: ReaderPageId)
    fun navigateChapter(chapterId: ReaderChapterId, direction: ReaderNavigationDirection)
    fun persistProgress(chapterId: ReaderChapterId, pageId: ReaderPageId, pageIndex: Int)
    fun persistViewport(chapterId: ReaderChapterId, pageId: ReaderPageId, transform: ReaderTransform)
    fun clearWorkingResources()
    fun showError(error: ReaderSessionError)
}

/**
 * Owns one reader session and turns reader-level intent into [ReaderSessionCommand]s.
 *
 * **What this is for.** `ReaderSession` is a complete, deterministic state machine with fifteen
 * commands and eight effects, and nothing dispatched one. The `ReaderViewModel` in its place kept
 * chapter identity, page index and arrival direction in loose fields that had to be kept consistent
 * by hand — the shape that produced the completion defects `DEF-011` and `DEF-015`. This is the
 * seam that makes the machine reachable: the ViewModel says *what happened* and the coordinator owns
 * the identity mapping, the state, and the effect execution.
 *
 * **Deliberately not a wrapper.** It adds the two things the session cannot do alone — translate
 * entities to identities, and run the effects the transition returns — and nothing else. The rules
 * stay in [ReaderSessionReducer] where they are already tested; if a rule ever moves here, the
 * machine stops being verifiable on its own.
 *
 * Pure: no Android, no coroutines, no clock. That is what makes the whole session path testable
 * without a device, and it is why this file is worth having separately from the view model.
 */
class ReaderSessionCoordinator(
    val sessionId: ReaderSessionId,
    private val effects: ReaderSessionEffectHandler,
) {
    private val session = ReaderSession(ReaderSessionState(sessionId = sessionId))

    /** The authoritative session state. Nothing else should hold chapter or page identity. */
    val state: ReaderSessionState get() = session.state

    /** Opens [chapter], moving to [ReaderSessionPhase.LoadingChapter]. */
    fun openChapter(
        chapter: Chapter,
        sourceId: Long,
        initialPageIndex: Int = 0,
        viewportMode: ReaderViewportMode = ReaderViewportMode.PAGED,
    ): ReaderSessionState = dispatch(
        ReaderCommand.OpenChapter(
            sessionId = sessionId,
            chapterId = ReaderSessionIdentity.chapterId(chapter.url, sourceId),
            initialPageIndex = initialPageIndex,
            viewportMode = viewportMode,
        ),
    )

    /** Reports [pages] loaded for [chapter], moving to [ReaderSessionPhase.Ready]. */
    fun chapterLoaded(
        chapter: Chapter,
        sourceId: Long,
        pages: List<Page>,
        initialPageIndex: Int = 0,
    ): ReaderSessionState {
        val chapterId = ReaderSessionIdentity.chapterId(chapter.url, sourceId)
        return dispatch(
            ReaderCommand.ChapterLoaded(
                sessionId = sessionId,
                chapterId = chapterId,
                pageIds = ReaderSessionIdentity.pageIds(pages.map { it.url }, chapterId),
                initialPageIndex = initialPageIndex,
            ),
        )
    }

    fun chapterFailed(chapter: Chapter, sourceId: Long, error: ReaderSessionError): ReaderSessionState =
        dispatch(
            ReaderCommand.ChapterFailed(
                sessionId = sessionId,
                chapterId = ReaderSessionIdentity.chapterId(chapter.url, sourceId),
                error = error,
            ),
        )

    /** Records that the reader landed on [page], and in which direction it arrived. */
    fun selectPage(
        page: Page,
        chapter: Chapter,
        sourceId: Long,
        direction: ReaderNavigationDirection,
    ): ReaderSessionState {
        val chapterId = ReaderSessionIdentity.chapterId(chapter.url, sourceId)
        val pageId = ReaderSessionIdentity.pageId(page.url, page.index, chapterId)
        // The session tracks arrival direction as state rather than as an argument, so the first
        // move after opening a chapter has to be stated or the machine cannot tell a forward arrival
        // from a backward one -- which is exactly the distinction `checkChapterCompletion` gates on.
        if (state.direction != direction) {
            dispatch(ReaderCommand.SelectPage(sessionId, pageId))
        }
        return dispatch(
            when (direction) {
                ReaderNavigationDirection.FORWARD -> ReaderCommand.MoveForward(sessionId)
                ReaderNavigationDirection.BACKWARD -> ReaderCommand.MoveBackward(sessionId)
                ReaderNavigationDirection.NONE -> ReaderCommand.SelectPage(sessionId, pageId)
            },
        )
    }

    fun pageLoaded(page: Page, chapter: Chapter, sourceId: Long): ReaderSessionState {
        val chapterId = ReaderSessionIdentity.chapterId(chapter.url, sourceId)
        val pageId = ReaderSessionIdentity.pageId(page.url, page.index, chapterId)
        return dispatch(ReaderCommand.PageLoaded(sessionId, pageId))
    }

    fun pageFailed(
        page: Page,
        chapter: Chapter,
        sourceId: Long,
        error: ReaderSessionError,
    ): ReaderSessionState {
        val chapterId = ReaderSessionIdentity.chapterId(chapter.url, sourceId)
        val pageId = ReaderSessionIdentity.pageId(page.url, page.index, chapterId)
        return dispatch(ReaderCommand.PageFailed(sessionId, pageId, error))
    }

    fun retryPage(page: Page, chapter: Chapter, sourceId: Long): ReaderSessionState {
        val chapterId = ReaderSessionIdentity.chapterId(chapter.url, sourceId)
        val pageId = ReaderSessionIdentity.pageId(page.url, page.index, chapterId)
        return dispatch(ReaderCommand.RetryPage(sessionId, pageId))
    }

    fun toggleMenu(visible: Boolean? = null): ReaderSessionState =
        dispatch(ReaderCommand.ToggleMenu(sessionId, visible))

    /** Closes the chapter, emitting the persist-and-release effects the reader owes on exit. */
    fun closeChapter(): ReaderSessionState = dispatch(ReaderCommand.CloseChapter(sessionId))

    /** The chapter currently open, or null. */
    val currentChapterId: ReaderChapterId? get() = state.chapterId

    private fun dispatch(command: ReaderCommand): ReaderSessionState {
        val transition = session.dispatch(command)
        transition.effects.forEach { execute(it) }
        return transition.state
    }

    private fun execute(effect: ReaderEffect) {
        when (effect) {
            is ReaderEffect.LoadChapter -> effects.loadChapter(effect.chapterId, effect.initialPageIndex)
            is ReaderEffect.LoadPage -> effects.loadPage(effect.chapterId, effect.pageId)
            is ReaderEffect.PrefetchPages -> effects.prefetchPages(effect.chapterId, effect.pageIds)
            is ReaderEffect.RetryPage -> effects.retryPage(effect.chapterId, effect.pageId)
            is ReaderEffect.NavigateChapter -> effects.navigateChapter(effect.chapterId, effect.direction)
            is ReaderEffect.PersistProgress -> effects.persistProgress(
                effect.chapterId,
                effect.pageId,
                effect.pageIndex,
            )
            is ReaderEffect.PersistViewport -> effects.persistViewport(
                effect.chapterId,
                effect.pageId,
                effect.transform,
            )
            ReaderEffect.ClearWorkingResources -> effects.clearWorkingResources()
            is ReaderEffect.ShowError -> effects.showError(effect.error)
        }
    }
}
