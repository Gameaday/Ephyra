package ephyra.feature.reader.session

/** Stable target-reader identities; these do not use legacy numeric source IDs. */
data class ReaderSessionId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

data class ReaderChapterId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

data class ReaderPageId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

enum class ReaderViewportMode { PAGED, CONTINUOUS }

enum class ReaderNavigationDirection { NONE, FORWARD, BACKWARD }

enum class ReaderErrorCode { INVALID_COMMAND, STALE_COMMAND, CHAPTER_LOAD_FAILED, PAGE_LOAD_FAILED }

data class ReaderSessionError(
    val code: ReaderErrorCode,
    val message: String? = null,
    val retryable: Boolean = false,
)

data class ReaderTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val revision: Long = 0L,
) {
    fun isValid(): Boolean =
        scale.isFinite() && scale > 0f && offsetX.isFinite() && offsetY.isFinite() && revision >= 0L
}

sealed interface ReaderSessionPhase {
    data object Closed : ReaderSessionPhase
    data object LoadingChapter : ReaderSessionPhase
    data object Ready : ReaderSessionPhase
    data class LoadingPage(val pageId: ReaderPageId) : ReaderSessionPhase
    data class Error(val error: ReaderSessionError, val pageId: ReaderPageId?) : ReaderSessionPhase
}

data class ReaderSessionState(
    val sessionId: ReaderSessionId,
    val chapterId: ReaderChapterId? = null,
    val pageIds: List<ReaderPageId> = emptyList(),
    val pageId: ReaderPageId? = null,
    val pageIndex: Int = 0,
    val direction: ReaderNavigationDirection = ReaderNavigationDirection.NONE,
    val viewportMode: ReaderViewportMode = ReaderViewportMode.PAGED,
    val transform: ReaderTransform = ReaderTransform(),
    val draftTransform: ReaderTransform? = null,
    val menuVisible: Boolean = false,
    val phase: ReaderSessionPhase = ReaderSessionPhase.Closed,
) {
    fun canAcceptPageSelection(): Boolean =
        phase is ReaderSessionPhase.Ready || phase is ReaderSessionPhase.Error
}

internal fun ReaderSessionState.isRestorable(expectedSessionId: ReaderSessionId): Boolean {
    if (sessionId != expectedSessionId || !transform.isValid() || draftTransform != null) return false
    val indexedPage = pageIds.getOrNull(pageIndex)
    return when (phase) {
        ReaderSessionPhase.Closed ->
            chapterId == null && pageIds.isEmpty() && pageId == null && pageIndex == 0 &&
                direction == ReaderNavigationDirection.NONE && !menuVisible
        ReaderSessionPhase.LoadingChapter ->
            chapterId != null && pageIds.isEmpty() && pageId == null && pageIndex >= 0
        ReaderSessionPhase.Ready ->
            chapterId != null && indexedPage != null && pageId == indexedPage
        is ReaderSessionPhase.LoadingPage ->
            chapterId != null && indexedPage != null && pageId == indexedPage && phase.pageId == pageId
        is ReaderSessionPhase.Error ->
            chapterId != null && when (val errorPageId = phase.pageId) {
                null -> pageIds.isEmpty() && pageId == null && pageIndex >= 0
                else -> indexedPage != null && pageId == errorPageId && pageId == indexedPage
            }
    }
}

sealed interface ReaderEffect {
    data class LoadChapter(val chapterId: ReaderChapterId, val initialPageIndex: Int) : ReaderEffect
    data class LoadPage(val chapterId: ReaderChapterId, val pageId: ReaderPageId) : ReaderEffect
    data class PrefetchPages(val chapterId: ReaderChapterId, val pageIds: List<ReaderPageId>) : ReaderEffect
    data class RetryPage(val chapterId: ReaderChapterId, val pageId: ReaderPageId) : ReaderEffect
    data class NavigateChapter(
        val chapterId: ReaderChapterId,
        val direction: ReaderNavigationDirection,
    ) : ReaderEffect

    data class PersistProgress(
        val chapterId: ReaderChapterId,
        val pageId: ReaderPageId,
        val pageIndex: Int,
    ) : ReaderEffect

    data class PersistViewport(
        val chapterId: ReaderChapterId,
        val pageId: ReaderPageId,
        val transform: ReaderTransform,
    ) : ReaderEffect
    data object ClearWorkingResources : ReaderEffect
    data class ShowError(val error: ReaderSessionError) : ReaderEffect
}

data class ReaderTransition(
    val state: ReaderSessionState,
    val effects: List<ReaderEffect> = emptyList(),
)

/** Owns one deterministic reader state machine. Effect execution remains outside this class. */
class ReaderSession(initialState: ReaderSessionState) {
    init {
        require(initialState.phase == ReaderSessionPhase.Closed)
        require(initialState.isRestorable(initialState.sessionId))
    }

    var state: ReaderSessionState = initialState
        private set

    fun dispatch(command: ReaderCommand): ReaderTransition {
        val transition = ReaderSessionReducer.reduce(state, command)
        state = transition.state
        return transition
    }
}

sealed interface ReaderCommand {
    val sessionId: ReaderSessionId

    data class OpenChapter(
        override val sessionId: ReaderSessionId,
        val chapterId: ReaderChapterId,
        val initialPageIndex: Int = 0,
        val viewportMode: ReaderViewportMode = ReaderViewportMode.PAGED,
    ) : ReaderCommand

    data class ChapterLoaded(
        override val sessionId: ReaderSessionId,
        val chapterId: ReaderChapterId,
        val pageIds: List<ReaderPageId>,
        val initialPageIndex: Int = 0,
    ) : ReaderCommand

    data class ChapterFailed(
        override val sessionId: ReaderSessionId,
        val chapterId: ReaderChapterId,
        val error: ReaderSessionError,
    ) : ReaderCommand

    data class SelectPage(override val sessionId: ReaderSessionId, val pageId: ReaderPageId) : ReaderCommand
    data class MoveForward(override val sessionId: ReaderSessionId) : ReaderCommand
    data class MoveBackward(override val sessionId: ReaderSessionId) : ReaderCommand
    data class PageLoaded(override val sessionId: ReaderSessionId, val pageId: ReaderPageId) : ReaderCommand
    data class PageFailed(
        override val sessionId: ReaderSessionId,
        val pageId: ReaderPageId,
        val error: ReaderSessionError,
    ) : ReaderCommand

    data class RetryPage(override val sessionId: ReaderSessionId, val pageId: ReaderPageId) : ReaderCommand
    data class BeginTransform(override val sessionId: ReaderSessionId) : ReaderCommand
    data class UpdateTransform(override val sessionId: ReaderSessionId, val transform: ReaderTransform) : ReaderCommand
    data class CommitTransform(override val sessionId: ReaderSessionId) : ReaderCommand
    data class CancelTransform(override val sessionId: ReaderSessionId) : ReaderCommand
    data class DoubleTap(override val sessionId: ReaderSessionId, val transform: ReaderTransform) : ReaderCommand
    data class ToggleMenu(override val sessionId: ReaderSessionId, val visible: Boolean? = null) : ReaderCommand
    data class RestoreState(override val sessionId: ReaderSessionId, val snapshot: ReaderSessionState) : ReaderCommand
    data class CloseChapter(override val sessionId: ReaderSessionId) : ReaderCommand
}

object ReaderSessionReducer {
    fun reduce(state: ReaderSessionState, command: ReaderCommand): ReaderTransition {
        if (command.sessionId != state.sessionId) return reject(state, ReaderErrorCode.STALE_COMMAND)
        return when (command) {
            is ReaderCommand.OpenChapter -> openChapter(state, command)
            is ReaderCommand.ChapterLoaded -> chapterLoaded(state, command)
            is ReaderCommand.ChapterFailed -> chapterFailed(state, command)
            is ReaderCommand.SelectPage -> selectPage(state, command)
            is ReaderCommand.MoveForward -> move(state, 1, ReaderNavigationDirection.FORWARD)
            is ReaderCommand.MoveBackward -> move(state, -1, ReaderNavigationDirection.BACKWARD)
            is ReaderCommand.PageLoaded -> pageLoaded(state, command)
            is ReaderCommand.PageFailed -> pageFailed(state, command)
            is ReaderCommand.RetryPage -> retryPage(state, command)
            is ReaderCommand.BeginTransform -> beginTransform(state)
            is ReaderCommand.UpdateTransform -> updateTransform(state, command)
            is ReaderCommand.CommitTransform -> commitTransform(state)
            is ReaderCommand.CancelTransform -> cancelTransform(state)
            is ReaderCommand.DoubleTap -> doubleTap(state, command)
            is ReaderCommand.ToggleMenu -> toggleMenu(state, command)
            is ReaderCommand.RestoreState -> restoreState(state, command)
            is ReaderCommand.CloseChapter -> closeChapter(state)
        }
    }

    private fun openChapter(state: ReaderSessionState, command: ReaderCommand.OpenChapter): ReaderTransition {
        if (state.chapterId == command.chapterId &&
            state.phase !is ReaderSessionPhase.Error &&
            state.phase != ReaderSessionPhase.Closed
        ) {
            return ReaderTransition(state)
        }
        val next = state.copy(
            chapterId = command.chapterId,
            pageIds = emptyList(),
            pageId = null,
            pageIndex = command.initialPageIndex.coerceAtLeast(0),
            direction = ReaderNavigationDirection.NONE,
            viewportMode = command.viewportMode,
            transform = ReaderTransform(),
            draftTransform = null,
            menuVisible = false,
            phase = ReaderSessionPhase.LoadingChapter,
        )
        val effects = buildList {
            if (state.chapterId != null) {
                if (state.pageId != null) {
                    add(
                        ReaderEffect.PersistProgress(
                            state.chapterId,
                            state.pageId,
                            state.pageIndex,
                        ),
                    )
                }
                add(ReaderEffect.ClearWorkingResources)
            }
            add(ReaderEffect.LoadChapter(command.chapterId, next.pageIndex))
        }
        return ReaderTransition(next, effects)
    }

    private fun chapterLoaded(state: ReaderSessionState, command: ReaderCommand.ChapterLoaded): ReaderTransition {
        if (state.phase !is ReaderSessionPhase.LoadingChapter || state.chapterId != command.chapterId) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        val pageIds = command.pageIds.distinct()
        if (pageIds.isEmpty()) return reject(state, ReaderErrorCode.INVALID_COMMAND)
        val pageIndex = command.initialPageIndex.coerceIn(0, pageIds.lastIndex)
        val pageId = pageIds[pageIndex]
        val next = state.copy(
            pageIds = pageIds,
            pageId = pageId,
            pageIndex = pageIndex,
            phase = ReaderSessionPhase.LoadingPage(pageId),
        )
        return ReaderTransition(next, pageEffects(next))
    }

    private fun chapterFailed(state: ReaderSessionState, command: ReaderCommand.ChapterFailed): ReaderTransition {
        if (state.phase !is ReaderSessionPhase.LoadingChapter || state.chapterId != command.chapterId) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        val error = command.error.copy(retryable = true)
        return ReaderTransition(
            state.copy(phase = ReaderSessionPhase.Error(error, null)),
            listOf(ReaderEffect.ShowError(error)),
        )
    }

    private fun selectPage(state: ReaderSessionState, command: ReaderCommand.SelectPage): ReaderTransition {
        if (!state.canAcceptPageSelection()) return reject(state, ReaderErrorCode.INVALID_COMMAND)
        val index = state.pageIds.indexOf(command.pageId)
        if (index < 0) return reject(state, ReaderErrorCode.INVALID_COMMAND)
        val next = state.copy(
            pageId = command.pageId,
            pageIndex = index,
            direction = ReaderNavigationDirection.NONE,
            phase = ReaderSessionPhase.LoadingPage(command.pageId),
        )
        return ReaderTransition(next, pageEffects(next))
    }

    private fun move(state: ReaderSessionState, delta: Int, direction: ReaderNavigationDirection): ReaderTransition {
        if (state.phase !is ReaderSessionPhase.Ready) return reject(state, ReaderErrorCode.INVALID_COMMAND)
        val target = state.pageIndex + delta
        if (target in state.pageIds.indices) {
            val next = state.copy(
                pageId = state.pageIds[target],
                pageIndex = target,
                direction = direction,
                phase = ReaderSessionPhase.LoadingPage(state.pageIds[target]),
            )
            return ReaderTransition(next, pageEffects(next))
        }
        val chapterId = state.chapterId ?: return reject(state, ReaderErrorCode.INVALID_COMMAND)
        return ReaderTransition(
            state.copy(direction = direction),
            listOf(ReaderEffect.NavigateChapter(chapterId, direction)),
        )
    }

    private fun pageLoaded(state: ReaderSessionState, command: ReaderCommand.PageLoaded): ReaderTransition {
        if (state.pageId != command.pageId || state.phase !is ReaderSessionPhase.LoadingPage) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        return ReaderTransition(state.copy(phase = ReaderSessionPhase.Ready))
    }

    private fun pageFailed(state: ReaderSessionState, command: ReaderCommand.PageFailed): ReaderTransition {
        if (state.pageId != command.pageId || state.phase !is ReaderSessionPhase.LoadingPage) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        val error = command.error.copy(retryable = true)
        return ReaderTransition(
            state.copy(phase = ReaderSessionPhase.Error(error, command.pageId)),
            listOf(ReaderEffect.ShowError(error)),
        )
    }

    private fun retryPage(state: ReaderSessionState, command: ReaderCommand.RetryPage): ReaderTransition {
        val chapterId = state.chapterId ?: return reject(state, ReaderErrorCode.INVALID_COMMAND)
        val errorPageId = (state.phase as? ReaderSessionPhase.Error)?.pageId
        if (state.phase !is ReaderSessionPhase.Error || errorPageId != command.pageId) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        return ReaderTransition(
            state.copy(phase = ReaderSessionPhase.LoadingPage(command.pageId)),
            listOf(ReaderEffect.RetryPage(chapterId, command.pageId)),
        )
    }

    private fun beginTransform(state: ReaderSessionState): ReaderTransition {
        if (state.phase !is ReaderSessionPhase.Ready || state.pageId == null) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        return ReaderTransition(state.copy(draftTransform = state.transform))
    }

    private fun updateTransform(state: ReaderSessionState, command: ReaderCommand.UpdateTransform): ReaderTransition {
        if (state.draftTransform == null || !command.transform.isValid()) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        return ReaderTransition(state.copy(draftTransform = command.transform))
    }

    private fun commitTransform(state: ReaderSessionState): ReaderTransition {
        val draft = state.draftTransform ?: return reject(state, ReaderErrorCode.INVALID_COMMAND)
        if (!draft.isValid()) return reject(state, ReaderErrorCode.INVALID_COMMAND)
        return persistViewport(
            state.copy(
                transform = draft.copy(revision = state.transform.revision + 1),
                draftTransform = null,
            ),
        )
    }

    private fun cancelTransform(state: ReaderSessionState): ReaderTransition {
        if (state.draftTransform == null) return reject(state, ReaderErrorCode.INVALID_COMMAND)
        return ReaderTransition(state.copy(draftTransform = null))
    }

    private fun doubleTap(state: ReaderSessionState, command: ReaderCommand.DoubleTap): ReaderTransition {
        if (state.phase !is ReaderSessionPhase.Ready || state.pageId == null || !command.transform.isValid()) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        return persistViewport(
            state.copy(
                transform = command.transform.copy(revision = state.transform.revision + 1),
                draftTransform = null,
            ),
        )
    }

    private fun toggleMenu(state: ReaderSessionState, command: ReaderCommand.ToggleMenu): ReaderTransition {
        if (state.phase is ReaderSessionPhase.Closed) return reject(state, ReaderErrorCode.INVALID_COMMAND)
        return ReaderTransition(state.copy(menuVisible = command.visible ?: !state.menuVisible))
    }

    private fun restoreState(state: ReaderSessionState, command: ReaderCommand.RestoreState): ReaderTransition {
        val snapshot = command.snapshot
        if (!snapshot.isRestorable(state.sessionId)) {
            return reject(state, ReaderErrorCode.INVALID_COMMAND)
        }
        val effects = when (snapshot.phase) {
            is ReaderSessionPhase.LoadingChapter ->
                snapshot.chapterId
                    ?.let { listOf(ReaderEffect.LoadChapter(it, snapshot.pageIndex)) }
                    .orEmpty()
            is ReaderSessionPhase.Ready -> pageEffects(snapshot)
            is ReaderSessionPhase.LoadingPage -> {
                val chapterId = snapshot.chapterId
                val pageId = snapshot.pageId
                if (chapterId != null && pageId != null) {
                    listOf(ReaderEffect.LoadPage(chapterId, pageId))
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
        return ReaderTransition(snapshot, effects)
    }

    private fun closeChapter(state: ReaderSessionState): ReaderTransition {
        if (state.phase is ReaderSessionPhase.Closed) return ReaderTransition(state)
        val effects = buildList {
            state.chapterId?.let { chapterId ->
                state.pageId?.let { pageId ->
                    add(ReaderEffect.PersistProgress(chapterId, pageId, state.pageIndex))
                }
            }
            add(ReaderEffect.ClearWorkingResources)
        }
        return ReaderTransition(
            state.copy(
                chapterId = null,
                pageIds = emptyList(),
                pageId = null,
                pageIndex = 0,
                direction = ReaderNavigationDirection.NONE,
                transform = ReaderTransform(),
                draftTransform = null,
                menuVisible = false,
                phase = ReaderSessionPhase.Closed,
            ),
            effects,
        )
    }

    private fun pageEffects(state: ReaderSessionState): List<ReaderEffect> {
        val chapterId = state.chapterId ?: return emptyList()
        val current = state.pageId ?: return emptyList()
        val prefetch = listOfNotNull(
            state.pageIds.getOrNull(state.pageIndex - 1),
            state.pageIds.getOrNull(state.pageIndex + 1),
        )
        return buildList {
            add(ReaderEffect.LoadPage(chapterId, current))
            if (prefetch.isNotEmpty()) add(ReaderEffect.PrefetchPages(chapterId, prefetch))
        }
    }

    private fun persistViewport(state: ReaderSessionState): ReaderTransition {
        val chapterId = state.chapterId
        val pageId = state.pageId
        if (chapterId == null || pageId == null) return ReaderTransition(state)
        return ReaderTransition(
            state,
            listOf(
                ReaderEffect.PersistViewport(
                    chapterId = chapterId,
                    pageId = pageId,
                    transform = state.transform,
                ),
            ),
        )
    }

    private fun reject(state: ReaderSessionState, code: ReaderErrorCode): ReaderTransition =
        ReaderTransition(state, listOf(ReaderEffect.ShowError(ReaderSessionError(code, retryable = false))))
}
