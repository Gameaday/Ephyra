package ephyra.feature.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.toggle
import ephyra.core.common.saver.Image
import ephyra.core.common.saver.ImageSaver
import ephyra.core.common.saver.Location
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.storage.cacheImageDir
import ephyra.core.common.util.system.DeviceUtil
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.core.download.DownloadProvider
import ephyra.core.download.util.filterDownloaded
import ephyra.core.download.util.removeDuplicates
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.domain.chapter.model.toSChapter
import ephyra.domain.chapter.service.ChapterCache
import ephyra.domain.chapter.service.ChapterNumber
import ephyra.domain.chapter.service.getChapterSort
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.model.HistoryUpdate
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.SetMangaViewerFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.reader.policy.ChapterCompletionPolicy
import ephyra.domain.reader.policy.DefaultReadingModeResolver
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.diagnostics.SourceResolutionDiagnostics
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.service.TrackPreferences
import ephyra.feature.reader.images.ReaderImageActions
import ephyra.feature.reader.images.ReaderImageEffectHandler
import ephyra.feature.reader.loader.ChapterLoader
import ephyra.feature.reader.loader.DownloadPageLoader
import ephyra.feature.reader.loading.ChapterLoadingCoordinator
import ephyra.feature.reader.loading.ChapterLoadingEffectHandler
import ephyra.feature.reader.model.InsertPage
import ephyra.feature.reader.model.NavigationVector
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderChapterWindowAdapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.model.ViewerChapters
import ephyra.feature.reader.model.resolveChapterStartRequest
import ephyra.feature.reader.session.ReaderErrorCode
import ephyra.feature.reader.session.ReaderNavigationDirection
import ephyra.feature.reader.session.ReaderSessionCoordinator
import ephyra.feature.reader.session.ReaderSessionEffectLog
import ephyra.feature.reader.session.ReaderSessionError
import ephyra.feature.reader.session.ReaderSessionId
import ephyra.feature.reader.session.ReaderSessionState
import ephyra.feature.reader.session.ReaderViewportMode
import ephyra.feature.reader.viewer.Viewer
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.util.manga.editCover
import ephyra.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import java.io.InputStream
import java.time.Instant
import java.util.Date
import javax.inject.Inject

/**
 * Presenter used by the activity to perform background operations.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val sourceManager: SourceManager,
    private val sourceResolutionDiagnostics: SourceResolutionDiagnostics,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val imageSaver: ImageSaver,
    val readerPreferences: ReaderPreferences,
    private val basePreferences: ephyra.domain.base.BasePreferences,
    private val downloadPreferences: DownloadPreferences,
    private val trackPreferences: TrackPreferences,
    private val trackChapter: TrackChapter,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getNextChapters: GetNextChapters,
    private val historyRepository: HistoryRepository,
    private val updateChapter: UpdateChapter,
    private val setMangaViewerFlags: SetMangaViewerFlags,
    private val getIncognitoState: GetIncognitoState,
    private val libraryPreferences: LibraryPreferences,
    private val app: Application,
    private val coverCache: CoverCache,
    private val localCoverManager: LocalCoverManager,
    private val updateManga: UpdateManga,
    private val chapterCache: ChapterCache,
) : BaseUdfViewModel<ReaderViewModel.State, ReaderEvent, ReaderViewModel.Event>(State()),
    ChapterLoadingEffectHandler,
    ReaderImageEffectHandler {
    private companion object {
        /** Current chapter plus two in either direction stays hot. */
        const val WINDOW_SIZE = 5
    }

    val eventFlow: Flow<Event> get() = effects

    /**
     * Number of pages to proactively start loading when an adjacent chapter is preloaded,
     * scaled to the device's performance tier so high-end devices prefetch more aggressively.
     */
    private val preloadChapterAheadPages: Int by lazy {
        when (DeviceUtil.performanceTier(app)) {
            DeviceUtil.PerformanceTier.LOW -> 2
            DeviceUtil.PerformanceTier.MEDIUM -> 4
            DeviceUtil.PerformanceTier.HIGH -> 6
        }
    }

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    val manga: Manga?
        get() = state.value.manga

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    /**
     * The visible page index of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterPageIndex = savedState.get<Int>("page_index") ?: -1
        set(value) {
            savedState["page_index"] = value
            field = value
        }
    private var hasAppliedSavedPageIndex = false

    /**
     * Identity of the chapter that most recently produced a page selection. Used by
     * [resolveNavigationVector] to distinguish a genuine within-chapter backward scroll
     * from the first selection of a freshly loaded chapter (whose [chapterPageIndex] may
     * still hold the previous chapter's value).
     */
    private var lastSelectedChapterId: Long? = null

    /**
     * The [NavigationVector] resolved for the most recent page selection. Absorb-driven
     * completion checks reuse it so that landing on a merged parent page never counts as
     * forward progress unless the user actually arrived there moving forward.
     */
    var lastNavigationVector: NavigationVector = NavigationVector.FORWARD
        private set

    /**
     * The effects the reader session emits, recorded in order (RDR-001 wiring).
     *
     * In this migration step every effect names work the dispatching path already performs -- the
     * chapter load is in flight when OpenChapter is dispatched, progress persists through
     * updateChapterProgress/updateHistory on the same seams, and page bytes are bounded by the
     * chapter window ref/unref. Re-issuing them would duplicate loads, persists and prefetches,
     * so the log acknowledges each request instead of executing it; it is the seam the
     * replacement resource owners delegate through when they land.
     */
    val sessionEffects = ReaderSessionEffectLog()

    /**
     * The reader session this view model drives (RDR-001). The session owns chapter/page
     * identity, page index, arrival direction, menu visibility and phase for the wired slice --
     * the loose-fields shape that produced DEF-011 and DEF-015.
     *
     * lastSelectedChapterId and chapterPageIndex deliberately stay view-model-local: the
     * session model cannot express opened-but-nothing-selected-yet, and absorbing that changes
     * ReaderSession and its reducer table (see [resolveNavigationVector]).
     */
    private val readerSession = ReaderSessionCoordinator(ReaderSessionId("reader-session"), sessionEffects)

    /** The authoritative reader session state (RDR-001): identity, index, direction, menu, phase. */
    val sessionState: ReaderSessionState get() = readerSession.state

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * The chapter-loading coordinator (R-A, Stage 3 seam 1 of the reader modular redesign,
     * 2026-11-08): the VM drives it; it owns the open/preload ordering and the buffer-health
     * decisions that were previously inlined here (and, for the buffer count, duplicated
     * between preload() and tryPreloadNextChapterImages). The VM implements the effect
     * handler - the override bodies below are the code that used to live in
     * loadChapter/preload, moved, not rethought.
     */
    private val chapterLoading by lazy {
        ChapterLoadingCoordinator(preloadAheadPages = preloadChapterAheadPages, effects = this)
    }

    private val imageActions by lazy { ReaderImageActions(this) }

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Long? = null

    private var chapterToDownload: Download? = null

    private var unfilteredChapterListCache: List<Chapter>? = null
    private suspend fun getUnfilteredChapterList(): List<Chapter> {
        if (unfilteredChapterListCache == null) {
            val manga = manga!!
            unfilteredChapterListCache = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = false)
        }
        return unfilteredChapterListCache!!
    }

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private var chapterListCache: List<ReaderChapter>? = null
    private suspend fun getChapterList(): List<ReaderChapter> {
        chapterListCache?.let { return it }

        // Ensure the DownloadCache has finished reading its on-disk snapshot before we
        // run any filter that calls isChapterDownloaded() (the downloadedOnly filter below).
        // On the warm path this is a single deferred check
        // with no suspension. On a cold start it waits for the brief disk-cache read.
        downloadManager.awaitCacheReady()

        val manga = manga!!
        val chapters = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)

        val selectedChapter = chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        // Series-screen filters describe what is displayed there, not the reader's navigation
        // universe. Keep read chapters in this list so backward navigation can deliberately
        // revisit them; skipRead is applied only when selecting the forward neighbor below.
        val filteredChapters = chapters
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .run {
                if (readerPreferences.skipDupe().get()) {
                    removeDuplicates(selectedChapter)
                } else {
                    this
                }
            }
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloaded(manga, downloadManager)
                } else {
                    this
                }
            }
        val resultChapters = if (filteredChapters.any { it.id == selectedChapter.id }) {
            filteredChapters
        } else {
            filteredChapters + selectedChapter
        }.sortedWith(getChapterSort(manga, sortDescending = false))
        val result = resultChapters.map(::ReaderChapter)
        chapterListCache = result
        return result
    }

    private val incognitoMode: Boolean by lazy { getIncognitoState.await(manga?.source) }
    private suspend fun downloadAheadAmount(): Int = downloadPreferences.autoDownloadWhileReading().get()

    init {
        // To save state
        state.map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { currentChapter ->
                val startRequest = resolveChapterStartRequest(
                    chapterId = currentChapter.chapter.id,
                    chapterRead = currentChapter.chapter.read,
                    lastPageRead = currentChapter.chapter.lastPageRead,
                    startingAtBeginning = currentChapter.startingAtBeginning,
                    startFromEnd = currentChapter.startFromEnd,
                    savedChapterId = chapterId,
                    savedPageIndex = chapterPageIndex,
                    savedRestoreApplied = hasAppliedSavedPageIndex,
                )
                startRequest.requestedPage?.let { currentChapter.requestedPage = it }
                // The restore decision is made for the first chapter that resolves — whether or not
                // there was an index to restore — so a later chapter can never be mistaken for the
                // restored one. Progress stays synchronized through onPageSelected().
                hasAppliedSavedPageIndex = startRequest.savedRestoreResolved
                chapterId = currentChapter.chapter.id
                trackReadingWindow(currentChapter.chapter)

                // The indicator and slider describe the *current* chapter. Carrying the previous
                // chapter's number across a boundary is how a fresh chapter could present as
                // "page 2" while nothing had reported a page yet (the pager was still showing the
                // transition card). Clear it until the first page selection reports the real
                // position; the indicator is hidden at 0 rather than showing a stale number.
                updateState { current ->
                    if (current.currentPage == 0) current else current.copy(currentPage = 0)
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Cold-tier eviction (doc/cache-retention-policy.md rule 1) ────────────
    // The reader keeps the current chapter ±2 hot. A chapter that falls out of the
    // window has its page list and images evicted: pages are large, viewed once
    // sequentially, and cheap to re-fetch in the rare long backward jump — retention
    // follows revisit probability, not arrival order. Eviction makes no network calls.
    private val readingWindow = ArrayDeque<ephyra.domain.chapter.model.Chapter>(WINDOW_SIZE)

    private fun trackReadingWindow(chapter: ephyra.domain.chapter.model.Chapter) {
        if (readingWindow.lastOrNull()?.id == chapter.id) return
        // Reorder on revisit so an in-window backward jump re-marks the chapter hot
        // instead of double-counting it.
        readingWindow.removeAll { it.id == chapter.id }
        readingWindow.addLast(chapter)
        while (readingWindow.size > WINDOW_SIZE) {
            val evicted = readingWindow.removeFirst()
            chapterCache.removeChapter(evicted)
        }
    }

    override fun onCleared() {
        val currentChapters = state.value.viewerChapters
        if (currentChapters != null) {
            currentChapters.unref()
            chapterToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
        // Series-switch eviction (doc/cache-retention-policy.md rule 1, second half):
        // the sliding window only slides within one series, and each series gets its
        // own ViewModel — so without this, reading the latest chapter of N series
        // caches N × WINDOW_SIZE chapters until byte-pressure LRU notices. On reader
        // close, keep only the chapter being left (instant resume) and decache the
        // rest. Rotation does not call onCleared, so this fires on genuine exit only.
        val keepId = readingWindow.lastOrNull()?.id
        readingWindow.forEach { chapter ->
            if (chapter.id != keepId) {
                chapterCache.removeChapter(chapter)
            }
        }
        readingWindow.clear()
    }

    // ── UDF entry-point ──────────────────────────────────────────────────────
    override fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.ActivityFinish -> onActivityFinish()
            is ReaderEvent.ViewerLoaded -> onViewerLoaded(event.viewer)
            is ReaderEvent.PageSelected -> onPageSelected(event.page)
            is ReaderEvent.RestartReadTimer -> restartReadTimer()
            is ReaderEvent.ToggleChapterBookmark -> toggleChapterBookmark()
            is ReaderEvent.SetMangaReadingMode -> setMangaReadingMode(event.readingMode)
            is ReaderEvent.SetMangaOrientationType -> setMangaOrientationType(event.orientation)
            is ReaderEvent.ToggleCropBorders -> toggleCropBorders()
            is ReaderEvent.ShowMenus -> showMenus(event.visible)
            is ReaderEvent.ShowLoadingDialog -> showLoadingDialog()
            is ReaderEvent.OpenReadingModeSelectDialog -> openReadingModeSelectDialog()
            is ReaderEvent.OpenOrientationModeSelectDialog -> openOrientationModeSelectDialog()
            is ReaderEvent.OpenPageDialog -> openPageDialog(event.page)
            is ReaderEvent.OpenSettingsDialog -> openSettingsDialog(0)
            is ReaderEvent.OpenSettingsDialogWithPage -> openSettingsDialog(event.initialPage)
            is ReaderEvent.CloseDialog -> closeDialog()
            is ReaderEvent.SaveImage -> saveImage()
            is ReaderEvent.ShareImage -> shareImage(event.copyToClipboard)
            is ReaderEvent.SetAsCover -> setAsCover()
            is ReaderEvent.BlockPage -> blockPage()
            is ReaderEvent.UnblockPage -> unblockPage(event.hex)
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    private fun onActivityFinish() {
        // RDR-001: close the session first. Its CloseChapter transition is the
        // persist-and-release hand-off; the effects it emits are acknowledged by [sessionEffects]
        // while the history persistence below owns the actual write in this step.
        readerSession.closeChapter()
        // Persist the final chapter's history (read-at time + session duration) before exiting,
        // as [updateHistory] is otherwise only invoked when switching chapters.
        viewModelScope.launchNonCancellable {
            try {
                updateHistory()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to update history on activity finish" }
            }
        }
        deletePendingChapters()
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    suspend fun init(mangaId: Long, initialChapterId: Long): Result<Boolean> {
        if (!needsInit()) return Result.success(true)
        return withIOContext {
            try {
                val manga = getManga.await(mangaId)
                if (manga != null) {
                    sourceManager.isInitialized.first { it }
                    updateState { it.copy(manga = manga) }
                    if (chapterId == -1L) chapterId = initialChapterId

                    val source = sourceManager.getOrStub(manga.source)
                    // DEF-029 instrument: an entry whose source is no longer registered fails later
                    // and elsewhere, so record how this entry's source id resolves right here.
                    sourceResolutionDiagnostics.reportEntry(manga.source)
                    loader = ChapterLoader(
                        app,
                        downloadManager,
                        downloadProvider,
                        manga,
                        source,
                        downloadPreferences,
                        chapterCache,
                    )

                    loadChapter(getChapterList().first { chapterId == it.chapter.id })
                    Result.success(true)
                } else {
                    // Unlikely but okay
                    Result.success(false)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                recordChapterLoadFailure(
                    chapterListCache?.firstOrNull { it.chapter.id == chapterId },
                    e,
                )
                Result.failure(e)
            }
        }
    }

    /**
     * Loads the given [chapter] and updates the currently active chapters. Callers must handle
     * errors.
     *
     * R-A (Stage 3 seam 1 of the reader modular redesign, 2026-11-08): the ordering of the open
     * path lives in [chapterLoading] - the coordinator drives, and this class is the effect
     * handler (see the overrides below). What changed is where the sequence is decided; every
     * step still lands exactly where it did.
     */
    private suspend fun loadChapter(chapter: ReaderChapter) {
        chapterLoading.openChapter(chapter)
    }

    // ── Chapter-loading effects (R-A Stage 3 seam 1: the coordinator drives, the VM executes) ──

    override suspend fun openSessionForChapter(chapter: ReaderChapter) {
        // RDR-001: the session owns the chapter-open transition (persist the prior chapter
        // progress, release its working resources, then LoadingChapter). The effects it emits
        // are acknowledged by [sessionEffects], not re-issued -- the load below is the work.
        readerSession.openChapter(
            chapter = chapter.chapter,
            sourceId = sessionSourceId(),
            initialPageIndex = chapter.requestedPage ?: 0,
            viewportMode = sessionViewportMode(),
        )
    }

    override suspend fun loadChapterPages(chapter: ReaderChapter, isPreloadOnly: Boolean): Boolean {
        val loader = loader ?: return false
        loader.loadChapter(chapter, isPreloadOnly = isPreloadOnly)
        return true
    }

    override fun wireFilteredPageReload(chapter: ReaderChapter) {
        // When the pre-processor blocks a page during online loading (after the image
        // arrives), the adapter must rebuild its item list to exclude the newly hidden
        // page. Wire up the callback so the viewer refreshes automatically.
        chapter.pageLoader?.onPageFiltered = {
            emitEffect(Event.ReloadViewerChapters)
        }
    }

    override fun queueBackgroundChapterPreload(chapter: ReaderChapter) {
        // Queue every page at the lowest background priority so the smart-combine pre-scan
        // can process the entire chapter without waiting for the user to scroll to each page.
        // Priority is kept below the nearby-page preload (0) and current-page load (1) so
        // this never competes for bandwidth with what the user is actively reading.
        // (B-010 / audit F3: this call site's policy; the decision to call it is the
        // coordinator's - see ChapterLoadingCoordinator.openChapter.)
        chapter.pageLoader?.preloadAllPages()
    }

    override suspend fun installViewerChapters(chapter: ReaderChapter) {
        val chapterList = getChapterList()
        // RDR-002: the prev/next window is selected through the domain ReaderChapterWindowPolicy
        // (via the adapter), replacing the feature-local sibling helpers. The pinned contracts
        // -- nulls at the boundaries, read chapters still eligible backwards, no head-of-list
        // fallback for a missing current -- are characterised in ReaderChapterWindowAdapterTest.
        val skipRead = readerPreferences.skipRead().get()
        val neighbours = ReaderChapterWindowAdapter.neighbours(
            chapters = chapterList,
            current = chapter,
            skipReadForward = skipRead,
        )
        val newChapters = ViewerChapters(
            chapter,
            neighbours.previous,
            neighbours.next,
        )

        // MutableStateFlow.update is thread-safe — no UI-thread dispatch needed.
        updateState {
            // Add new references first to avoid unnecessary recycling
            newChapters.ref()
            it.viewerChapters?.unref()

            chapterToDownload = cancelQueuedDownloads(newChapters.currChapter)
            it.copy(
                viewerChapters = newChapters,
                bookmarked = newChapters.currChapter.chapter.bookmark,
            )
        }
    }

    override suspend fun recordOpenedChapterHistory(chapter: ReaderChapter) {
        // Record the newly current chapter in reading history. This lives on the open path (the
        // one path shared by initial open, viewer page transitions, and toolbar prev/next
        // navigation) rather than only in loadNewChapter, so every chapter the user actually
        // opens is written to history. Previously the initial chapter and adjacent-chapter
        // navigation bypassed updateHistory(), leaving the History tab empty for
        // single-chapter sessions. The row is updated (upsert by chapter id) with the real read
        // duration when the chapter is closed out by updateHistory().
        if (!incognitoMode) {
            historyRepository.upsertHistory(
                HistoryUpdate(
                    chapterId = chapter.chapter.id,
                    readAt = Date(),
                    sessionReadDuration = 0L,
                ),
            )
        }
    }

    override fun restartReadTimer() {
        chapterReadStartTime = Instant.now().toEpochMilli()
    }

    override fun reportChapterLoadedToSession(chapter: ReaderChapter) {
        // RDR-001: report the loaded pages to the session. A chapter with no pages is rejected
        // by the machine (its own contract) -- the rejection is acknowledged in [sessionEffects].
        readerSession.chapterLoaded(
            chapter = chapter.chapter,
            sourceId = sessionSourceId(),
            pages = chapter.pages ?: emptyList(),
            initialPageIndex = 0,
        )
    }

    override suspend fun promoteDownloadedPreload(chapter: ReaderChapter): Boolean {
        val manga = manga ?: return false
        val dbChapter = chapter.chapter
        // Ensure the DownloadCache is ready before querying: on the fast path (deferred
        // already complete) this is a single non-suspending check; on a cold start it waits
        // for the disk-cache read so we don't incorrectly skip the reload.
        downloadManager.awaitCacheReady()
        val isDownloaded = downloadManager.isChapterDownloaded(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            manga.source,
        )
        if (isDownloaded) {
            chapter.state = ReaderChapter.State.Wait
        }
        return true
    }

    override fun startAdjacentImagePreload(chapter: ReaderChapter, pages: Int) {
        chapter.pageLoader?.preloadFirstPages(pages)
    }

    override fun requestViewerReload() {
        emitEffect(Event.ReloadViewerChapters)
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        if (loader == null) return

        viewModelScope.launchIO {
            logcat { "Loading ${chapter.chapter.url}" }

            updateHistory()
            restartReadTimer()

            try {
                loadChapter(chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                recordChapterLoadFailure(chapter, e)
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    /**
     * Called when the user is going to load the prev/next chapter through the toolbar buttons.
     */
    private suspend fun loadAdjacent(
        chapter: ReaderChapter,
        startFromEnd: Boolean,
    ) {
        if (loader == null) return

        logcat { "Loading adjacent ${chapter.chapter.url}" }

        // Direction-aware start for toolbar transitions: the plan (which end to start at,
        // and the explicit end target when pages are already known vs. the clamp-me
        // fallback) is owned by the coordinator; the activity then snaps to the first/last
        // visible page, excluding hidden pages.
        val start = chapterLoading.adjacentStartPlan(startFromEnd, chapter.pages?.lastIndex)
        chapter.startingAtBeginning = start.startingAtBeginning
        chapter.startFromEnd = start.startFromEnd
        chapter.requestedPage = start.requestedPage

        updateState { it.copy(isLoadingAdjacentChapter = true) }
        try {
            withIOContext {
                loadChapter(chapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            recordChapterLoadFailure(chapter, e)
            logcat(LogPriority.ERROR, e)
        } finally {
            updateState { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     *
     * R-A (Stage 3 seam 1): the eligibility ladder, the buffer-health gate and the
     * whole-chapter image prefetch are decided in [chapterLoading]; this is the drive call.
     */
    suspend fun preload(chapter: ReaderChapter) {
        chapterLoading.preloadChapter(
            chapter = chapter,
            currentPages = getCurrentChapter()?.pages,
            currentPageIndex = chapterPageIndex,
        )
    }

    private fun onViewerLoaded(viewer: Viewer?) {
        updateState {
            it.copy(viewer = viewer)
        }
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    private fun onPageSelected(page: ReaderPage) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) {
            return
        }

        val selectedChapter = page.chapter
        val pages = selectedChapter.pages ?: return

        // Resolve the direction the reader moved to reach this page. Completion side-effects
        // are gated on FORWARD movement: re-entering a chapter from its top boundary (or
        // scrolling back within one) must never mark it read.
        val navigationVector = resolveNavigationVector(selectedChapter, page)
        lastNavigationVector = navigationVector
        recordSessionSelection(page, selectedChapter.chapter, navigationVector)

        // Save last page read and mark as read if needed
        viewModelScope.launchNonCancellable {
            updateChapterProgress(selectedChapter, page, navigationVector)
        }

        if (selectedChapter != getCurrentChapter()) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            loadNewChapter(selectedChapter)
        }

        val inDownloadRange = page.number.toDouble() / pages.size > 0.25
        if (inDownloadRange) {
            downloadNextChapters()
        }

        // If the next chapter is already loaded (preloaded) but its image prefetch was
        // previously deferred because the current buffer was too thin, retry it now that
        // another page has been consumed. This makes the preload truly opportunistic: it
        // starts the moment bandwidth is available rather than only at the instant preload()
        // is first called.
        tryPreloadNextChapterImages(page.index, pages)

        emitEffect(Event.PageChanged)
    }

    /**
     * Starts image preloading for the next chapter if the current chapter's forward buffer is
     * healthy enough that we can spare bandwidth. Idempotent — pages already downloading or
     * cached are skipped by [preloadFirstPages].
     *
     * @param currentPageIndex zero-based index of the page the user just navigated to.
     * @param currentPages     the full page list of the chapter being read.
     */
    private fun tryPreloadNextChapterImages(currentPageIndex: Int, currentPages: List<ReaderPage>) {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        val shouldPreload = chapterLoading.shouldPreloadNextChapterImages(
            nextChapter,
            currentPages.map { it.status },
            currentPageIndex,
        )
        if (shouldPreload) {
            nextChapter.pageLoader?.preloadFirstPages(chapterLoading.preloadAheadPages)
        }
    }

    private fun downloadNextChapters() {
        val manga = manga ?: return

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return
        val nextChapter = state.value.viewerChapters?.nextChapter?.chapter ?: return

        viewModelScope.launchIO {
            val downloadAheadAmount = downloadAheadAmount()
            if (downloadAheadAmount == 0) return@launchIO
            // Ensure the DownloadCache has finished reading its on-disk snapshot before
            // querying it. On the warm path this is a non-suspending check; on a cold
            // start it waits for the brief disk-cache read to avoid a false negative that
            // would suppress the download-ahead even though the next chapter is on disk.
            downloadManager.awaitCacheReady()
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                nextChapter.url,
                manga.title,
                manga.source,
            )
            if (!isNextChapterDownloaded) return@launchIO

            val chaptersToDownload = getNextChapters.await(manga.id, nextChapter.id).run {
                if (readerPreferences.skipDupe().get()) {
                    removeDuplicates(nextChapter)
                } else {
                    this
                }
            }.take(downloadAheadAmount)

            downloadManager.downloadChapters(
                manga,
                chaptersToDownload,
            )
        }
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private fun cancelQueuedDownloads(currentChapter: ReaderChapter): Download? {
        return downloadManager.getQueuedDownloadOrNull(currentChapter.chapter.id)?.also {
            downloadManager.cancelQueuedDownloads(listOf(it))
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private suspend fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        if (removeAfterReadSlots == -1) return

        // Determine which chapter should be deleted and enqueue
        val chapterList = getChapterList()
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        // If chapter is completely read, no need to download it
        chapterToDownload = null

        if (chapterToDelete != null) {
            enqueueDeleteReadChapters(chapterToDelete)
        }
    }

    /**
     * Resolves the [NavigationVector] that produced the selection of [selectedChapter]/[page].
     *
     * - Crossing chapters follows the canonical reader list order, independent of source order.
     * - Within the same chapter, moving to a lower page index is BACKWARD.
     * - The initial selection of a freshly opened reader is BACKWARD only when the chapter
     *   was explicitly positioned at its end (backward arrival); otherwise FORWARD.
     *
     * **Why [lastSelectedChapterId] and [chapterPageIndex] are not read from the reader session**,
     * which owns the same concepts and is otherwise the better home for them. `ReaderSessionState`
     * has `chapterId`, `pageId` and `pageIndex`, so this looks like a mechanical swap. It is not,
     * and the difference is the whole point of the field.
     *
     * [lastSelectedChapterId] is the chapter that most recently produced a **page selection**,
     * which is `null` until the reader selects one -- and the `previousChapterId == null` branch
     * below exists precisely to handle that first selection, distinguishing a deliberate arrival at
     * the end of a chapter from an arrival at its start. The session cannot express it:
     * `chapterLoaded` moves straight to `LoadingPage` with a `pageId` set, and `isRestorable`
     * requires `pageId == pageIds[pageIndex]`, so "opened but nothing selected yet" is not a
     * representable state there.
     *
     * Absorbing it therefore means the session has to model *arrival* separately from *open*, which
     * is a change to a component with its own tests rather than a wiring change, and is not
     * attempted here. `DEF-011` and `DEF-015` were both completion decided from state that looked
     * right and was not, and a plausible-looking swap of this field is precisely that failure mode.
     */
    private fun resolveNavigationVector(selectedChapter: ReaderChapter, page: ReaderPage): NavigationVector {
        val previousChapterId = lastSelectedChapterId
        val vector = when {
            previousChapterId == null ->
                if (selectedChapter.startFromEnd) NavigationVector.BACKWARD else NavigationVector.FORWARD
            previousChapterId != selectedChapter.chapter.id -> {
                val previousIndex = chapterListCache?.indexOfFirst {
                    it.chapter.id == previousChapterId
                } ?: -1
                val selectedIndex = chapterListCache?.indexOfFirst {
                    it.chapter.id == selectedChapter.chapter.id
                } ?: -1
                if (previousIndex >= 0 && selectedIndex >= 0 && selectedIndex >= previousIndex) {
                    NavigationVector.FORWARD
                } else {
                    NavigationVector.BACKWARD
                }
            }
            page.index >= chapterPageIndex -> NavigationVector.FORWARD
            else -> NavigationVector.BACKWARD
        }
        // Record the selection identity only once the vector has been resolved, so the next
        // resolution compares against the chapter this vector was computed for.
        lastSelectedChapterId = selectedChapter.chapter.id
        return vector
    }

    /**
     * Records a page selection in the reader session (RDR-001). The machine records arrival
     * direction only through adjacent moves -- a jump lands as a direction-less selection,
     * which is the machine semantics rather than a tracking loss; completion gating keeps
     * using [NavigationVector] until the session model grows a jump-with-direction command.
     */
    private fun recordSessionSelection(
        page: ReaderPage,
        chapter: Chapter,
        navigationVector: NavigationVector,
    ) {
        val sourceId = sessionSourceId()
        readerSession.selectPage(page, chapter, sourceId, navigationVector.toSessionDirection())
        // The session settles the selected page with the selection itself: page-load completion
        // is owned by the chapter loaders and viewers in this step and gets its own completion
        // channel when the viewport owner migrates. When the session is not awaiting this page
        // (a selection racing a chapter switch), the machine rejects the commands and the
        // rejection is acknowledged in [sessionEffects] rather than acted on.
        readerSession.pageLoaded(page, chapter, sourceId)
    }

    private fun NavigationVector.toSessionDirection(): ReaderNavigationDirection =
        when (this) {
            NavigationVector.FORWARD -> ReaderNavigationDirection.FORWARD
            NavigationVector.BACKWARD -> ReaderNavigationDirection.BACKWARD
        }

    /** The source identity for the session; 0L before the manga resolves (no pages exist yet). */
    private fun sessionSourceId(): Long = manga?.source ?: 0L

    /**
     * The session record of the viewport mode, from the resolved reading mode. Best-effort in
     * this step: the session viewport mode is not yet consumed by render decisions.
     */
    private fun sessionViewportMode(): ReaderViewportMode =
        if (ReadingMode.isPagerType(getMangaReadingMode())) {
            ReaderViewportMode.PAGED
        } else {
            ReaderViewportMode.CONTINUOUS
        }

    private fun recordChapterLoadFailure(chapter: ReaderChapter?, e: Throwable) {
        if (chapter == null) return
        readerSession.chapterFailed(
            chapter = chapter.chapter,
            sourceId = sessionSourceId(),
            error = ReaderSessionError(ReaderErrorCode.CHAPTER_LOAD_FAILED, e.message),
        )
    }

    /**
     * Saves the chapter progress (last read page and whether it's read)
     * if incognito mode isn't on.
     */
    private suspend fun updateChapterProgress(
        readerChapter: ReaderChapter,
        page: Page,
        navigationVector: NavigationVector = NavigationVector.FORWARD,
    ) {
        val pageIndex = page.index
        val chapterPages = readerChapter.pages

        // Compute the 1-based display position by counting only visible pages up to
        // and including the current page. This keeps the bottom indicator and slider in sync
        // with what is actually visible — hidden pages (absorbed stubs and blocked pages)
        // are invisible to the user and should not inflate the page count or current position.
        val displayIndex = if (chapterPages != null) {
            chapterPages.take(pageIndex + 1).count { !it.isHidden }
        } else {
            pageIndex + 1
        }
        updateState {
            it.copy(currentPage = displayIndex)
        }
        readerChapter.requestedPage = pageIndex
        chapterPageIndex = pageIndex

        if (!incognitoMode && page.status !is Page.State.Error) {
            val prevLastPageRead = readerChapter.chapter.lastPageRead
            val prevRead = readerChapter.chapter.read

            readerChapter.chapter = readerChapter.chapter.copy(lastPageRead = pageIndex.toLong())

            // RDR-006: the completion rule lives in `ChapterCompletionPolicy` and is shared with
            // `checkChapterCompletion`. It previously existed twice in this file, inline, and the
            // two copies could disagree. The policy also refuses to complete an out-of-range index
            // or an unresolved chapter, which the inline form did not.
            val visiblePagesAfter = if (readerPreferences.smartCombinePaged().get()) {
                (chapterPages?.size ?: 0) - pageIndex - 1
            } else {
                chapterPages?.let { pages ->
                    ChapterCompletionPolicy.countVisiblePagesAfter(pageIndex, pages.size) { i ->
                        pages[i].isHidden
                    }
                } ?: 0
            }
            if (
                ChapterCompletionPolicy.isChapterComplete(
                    pageIndex = pageIndex,
                    pageCount = chapterPages?.size ?: 0,
                    visiblePagesAfter = chapterPages?.let { pages ->
                        ChapterCompletionPolicy.countVisiblePagesAfter(pageIndex, pages.size) { i ->
                            pages[i].isHidden
                        }
                    } ?: 0,
                    chapterResolved = readerChapter.state is ReaderChapter.State.Loaded,
                    movingForward = navigationVector == NavigationVector.FORWARD,
                )
            ) {
                updateChapterProgressOnComplete(readerChapter)
            }

            // Skip the DB write when neither lastPageRead nor read changed — a memory
            // equality check is cheaper than a SQL UPDATE and avoids unnecessary I/O.
            if (readerChapter.chapter.lastPageRead != prevLastPageRead ||
                readerChapter.chapter.read != prevRead
            ) {
                updateChapter.await(
                    ChapterUpdate(
                        id = readerChapter.chapter.id,
                        read = readerChapter.chapter.read,
                        lastPageRead = readerChapter.chapter.lastPageRead,
                    ),
                )
            }
        }
    }

    private suspend fun updateChapterProgressOnComplete(readerChapter: ReaderChapter) {
        readerChapter.chapter = readerChapter.chapter.copy(read = true)
        updateTrackChapterRead(readerChapter)
        deleteChapterIfNeeded(readerChapter)

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!markDuplicateAsRead) return

        val duplicateUnreadChapters = getUnfilteredChapterList()
            .mapNotNull { chapter ->
                if (
                    !chapter.read &&
                    chapter.isRecognizedNumber &&
                    ChapterNumber.sameChapterNumber(chapter.chapterNumber, readerChapter.chapter.chapterNumber)
                ) {
                    ChapterUpdate(id = chapter.id, read = true)
                } else {
                    null
                }
            }
        updateChapter.awaitAll(duplicateUnreadChapters)
    }

    /**
     * Checks if [page] has become the effective last page of the chapter
     * (e.g. following pages were absorbed by smart-combine or blocked by filter).
     * If so, marks the chapter read and updates trackers and DB — but only when the
     * arrival [navigationVector] is [NavigationVector.FORWARD]; backward movement never
     * mutates read state.
     */
    fun checkChapterCompletion(
        page: ReaderPage,
        navigationVector: NavigationVector = NavigationVector.FORWARD,
    ): kotlinx.coroutines.Job? {
        if (page is InsertPage || incognitoMode) return null
        val readerChapter = page.chapter
        val chapterPages = readerChapter.pages ?: return null
        val pageIndex = page.index

        // RDR-006: the same `ChapterCompletionPolicy` used on the page-progress path, so the two
        // call sites cannot drift. Both previously carried their own inline copy of this rule.
        if (
            ChapterCompletionPolicy.isChapterComplete(
                pageIndex = pageIndex,
                pageCount = chapterPages.size,
                visiblePagesAfter = ChapterCompletionPolicy.countVisiblePagesAfter(
                    pageIndex,
                    chapterPages.size,
                ) { i -> chapterPages[i].isHidden },
                chapterResolved = readerChapter.state is ReaderChapter.State.Loaded,
                movingForward = navigationVector == NavigationVector.FORWARD,
            )
        ) {
            return viewModelScope.launchNonCancellable {
                val prevRead = readerChapter.chapter.read
                val prevLastPageRead = readerChapter.chapter.lastPageRead
                readerChapter.chapter = readerChapter.chapter.copy(lastPageRead = pageIndex.toLong())
                updateChapterProgressOnComplete(readerChapter)
                if (readerChapter.chapter.lastPageRead != prevLastPageRead ||
                    readerChapter.chapter.read != prevRead
                ) {
                    updateChapter.await(
                        ChapterUpdate(
                            id = readerChapter.chapter.id,
                            read = readerChapter.chapter.read,
                            lastPageRead = readerChapter.chapter.lastPageRead,
                        ),
                    )
                }
            }
        }
        return null
    }

    /**
     * Saves the chapter last read history if incognito mode isn't on.
     */
    suspend fun updateHistory() {
        getCurrentChapter()?.let { readerChapter ->
            if (incognitoMode) return@let

            val chapterId = readerChapter.chapter.id
            val endTime = Date()
            val sessionReadDuration = chapterReadStartTime?.let { endTime.time - it } ?: 0

            historyRepository.upsertHistory(HistoryUpdate(chapterId, endTime, sessionReadDuration))
            chapterReadStartTime = null
        }
    }

    /**
     * Called from the activity to load and set the next chapter as active.
     */
    suspend fun loadNextChapter() {
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        loadAdjacent(nextChapter, startFromEnd = false)
    }

    /**
     * Called from the activity to load and set the previous chapter as active.
     */
    suspend fun loadPreviousChapter(): Int? {
        val prevChapter = state.value.viewerChapters?.prevChapter ?: return null
        loadAdjacent(prevChapter, startFromEnd = true)
        // loadAdjacent() completes after loadChapter() returns, so the target chapter's page
        // list has been initialized (or load failed). Return null when a visible index can't be
        // derived (null/empty/all-hidden pages; indexOfLast returns -1 for empty/all-hidden)
        // so the caller can choose an explicit fallback.
        return prevChapter.lastVisiblePageIndex()
    }

    /**
     * Returns the currently active chapter.
     */
    private fun getCurrentChapter(): ReaderChapter? {
        return state.value.currentChapter
    }

    private fun ReaderChapter.lastVisiblePageIndex(): Int? {
        val visibleCount = pages?.count { !it.isHidden } ?: 0
        return (visibleCount - 1).takeIf { it >= 0 }
    }

    fun getSource() = manga?.source?.let { sourceManager.getOrStub(it) } as? HttpSource

    fun getChapterUrl(): String? {
        val chapter = getCurrentChapter()?.chapter ?: return null
        val source = getSource() ?: return null

        return try {
            source.getChapterUrl(chapter.toSChapter())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    /**
     * Bookmarks the currently active chapter.
     */
    private fun toggleChapterBookmark() {
        val chapter = getCurrentChapter()?.chapter ?: return
        val bookmarked = !chapter.bookmark

        viewModelScope.launchNonCancellable {
            updateChapter.await(
                ChapterUpdate(
                    id = chapter.id,
                    bookmark = bookmarked,
                ),
            )
        }

        updateState {
            it.copy(
                bookmarked = bookmarked,
            )
        }
    }

    private val defaultReadingMode = readerPreferences.defaultReadingMode().stateIn(viewModelScope)
    private val defaultOrientation = readerPreferences.defaultOrientationType().stateIn(viewModelScope)

    /**
     * Returns the viewer position used by this manga or the default one.
     *
     * Precedence and long-strip inference are owned by [DefaultReadingModeResolver];
     * this only adapts reader state to that policy and back to the stored flag value.
     */
    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = defaultReadingMode.value
        val readingMode = ReadingMode.fromPreference(manga?.readingMode?.toInt())
        return when {
            resolveDefault && readingMode == ReadingMode.DEFAULT -> {
                val currentManga = manga
                if (currentManga == null) {
                    default
                } else {
                    DefaultReadingModeResolver.resolve(
                        explicitMode = readingMode,
                        userDefaultMode = ReadingMode.fromPreference(default),
                        contentType = currentManga.contentType,
                        genres = currentManga.genre,
                    ).flagValue
                }
            }

            else -> manga?.readingMode?.toInt() ?: default
        }
    }

    /**
     * Updates the viewer position for the open manga.
     */
    private fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            setMangaViewerFlags.awaitSetReadingMode(manga.id, readingMode.flagValue.toLong())
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.lastPageRead.toInt()

                val newManga = getManga.await(manga.id)
                updateState {
                    it.copy(
                        manga = newManga,
                        viewerChapters = currChapters,
                    )
                }
                emitEffect(Event.ReloadViewerChapters)
            }
        }
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientation(resolveDefault: Boolean = true): Int {
        val default = defaultOrientation.value
        val orientation = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
        return when {
            resolveDefault && orientation == ReaderOrientation.DEFAULT -> default
            else -> manga?.readerOrientation?.toInt() ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    private fun setMangaOrientationType(orientation: ReaderOrientation) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            setMangaViewerFlags.awaitSetOrientation(manga.id, orientation.flagValue.toLong())
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.lastPageRead.toInt()

                val newManga = getManga.await(manga.id)
                updateState {
                    it.copy(
                        manga = newManga,
                        viewerChapters = currChapters,
                    )
                }
                emitEffect(Event.SetOrientation(getMangaOrientation()))
                emitEffect(Event.ReloadViewerChapters)
            }
        }
    }

    private fun toggleCropBorders(): Boolean {
        val isPagerType = ReadingMode.isPagerType(getMangaReadingMode())
        val pref = if (isPagerType) readerPreferences.cropBorders() else readerPreferences.cropBordersWebtoon()
        val newValue = !pref.getSync()
        pref.set(newValue)
        return newValue
    }

    private fun showMenus(visible: Boolean) {
        // RDR-001: menu visibility is session state. The compose snapshot below stays the
        // render source of truth; the session records the same fact for the wired slice, and
        // while no chapter is open the machine rejects the toggle and the log acknowledges it.
        readerSession.toggleMenu(visible)
        updateState { it.copy(menuVisible = visible) }
    }

    private fun showLoadingDialog() {
        updateState { it.copy(dialog = Dialog.Loading) }
    }

    private fun openReadingModeSelectDialog() {
        openSettingsDialog(0)
    }

    private fun openOrientationModeSelectDialog() {
        openSettingsDialog(0)
    }

    private fun openPageDialog(page: ReaderPage) {
        updateState { it.copy(dialog = Dialog.PageActions(page)) }
    }

    private fun openSettingsDialog(initialPage: Int = 0) {
        updateState { it.copy(dialog = Dialog.Settings(initialPage)) }
    }

    private fun closeDialog() {
        updateState { it.copy(dialog = null) }
    }

    /**
     * Saves the image of the selected page on the pictures directory and notifies the UI of
     * the result. There's also a notification to allow sharing the image somewhere else or
     * deleting it. The coordinator owns the flow, the guards, the filename and the relative
     * path; this is the drive.
     */
    private fun saveImage() {
        viewModelScope.launchNonCancellable { imageActions.saveImage() }
    }

    override fun selectedPage(): ReaderPage? =
        (state.value.dialog as? Dialog.PageActions)?.page

    override fun currentManga(): Manga? = manga

    override fun folderPerManga(): Boolean = readerPreferences.folderPerManga().getSync()

    override fun resetSaveNotification() {
        SaveImageNotifier(app).onClear()
    }

    override fun showSaveNotificationError(message: String?) {
        SaveImageNotifier(app).onError(message)
    }

    override fun saveToPictures(page: ReaderPage, name: String, relativePath: String): Uri {
        val inputStream: () -> InputStream = resolveImageStream(page)
        return imageSaver.save(
            image = Image.Page(
                inputStream = inputStream,
                name = name,
                location = Location.Pictures.create(relativePath),
            ),
        )
    }

    override fun saveToCache(page: ReaderPage, name: String): Uri {
        val inputStream: () -> InputStream = resolveImageStream(page)
        return imageSaver.save(
            image = Image.Page(
                inputStream = inputStream,
                name = name,
                location = Location.Cache,
            ),
        )
    }

    /**
     * If the page was smart-combined with a stub, use the merged image so the exported file
     * reflects exactly what the reader showed rather than just the first page. The bitmap is
     * encoded to PNG on-the-fly - this is a rare, user-triggered action so the one-time
     * encode cost is negligible.
     */
    private fun resolveImageStream(page: ReaderPage): () -> InputStream =
        page.mergedBitmap?.let { bitmap ->
            {
                val buffer = okio.Buffer()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
                buffer.inputStream()
            }
        } ?: page.stream!!

    /**
     * Shares the image of the selected page and notifies the UI with the path of the file to
     * share. The image must be first copied to the internal partition because there are many
     * possible formats it can come from, like a zipped chapter. Only the last shared image
     * will be kept. The coordinator owns the flow - including the deliberate propagation of
     * cache failures, where the old outer try/catch was dead code.
     */
    private fun shareImage(copyToClipboard: Boolean) {
        viewModelScope.launchNonCancellable { imageActions.shareImage(copyToClipboard) }
    }

    override fun clearShareCache() {
        app.cacheImageDir.deleteRecursively()
    }

    override fun reportSavedImage(result: SaveImageResult) {
        emitEffect(Event.SavedImage(result))
    }

    override fun reportImageExported(uri: Uri, page: ReaderPage, copyToClipboard: Boolean) {
        emitEffect(if (copyToClipboard) Event.CopyImage(uri) else Event.ShareImage(uri, page))
    }

    override suspend fun applyCover(manga: Manga, stream: () -> InputStream) {
        manga.editCover(localCoverManager, stream(), updateManga, coverCache)
    }

    override fun reportSetCoverResult(result: SetAsCoverResult) {
        emitEffect(Event.SetCoverResult(result))
    }

    override suspend fun pageDHash(page: ReaderPage): Long? {
        val stream = page.stream ?: return null
        return try {
            withIOContext { stream().use { ImageUtil.computeDHash(it) } }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) {
                "Failed to check blocked-page hash for current page; treating as unblocked"
            }
            null
        }
    }

    override fun blockedHashes(): Set<String> = downloadPreferences.blockedPageHashes().get()

    override fun addBlockedHash(hex: String) {
        val pref = downloadPreferences.blockedPageHashes()
        val current = pref.get().toMutableSet()
        current.add(hex)
        pref.set(current)
        logcat(LogPriority.INFO) { "Blocked page dHash=$hex" }
    }

    override suspend fun removeBlockedHash(hex: String): Boolean =
        withIOContext {
            val pref = downloadPreferences.blockedPageHashes()
            val current = pref.get().toMutableSet()
            if (current.remove(hex)) {
                pref.set(current)
                logcat(LogPriority.INFO) { "Unblocked page dHash=$hex" }
                true
            } else {
                false
            }
        }

    /**
     * Sets the image of the selected page as cover and notifies the UI of the result. The
     * coordinator owns the flow, the guards and the result mapping; this is the drive.
     */
    private fun setAsCover() {
        viewModelScope.launchNonCancellable { imageActions.setAsCover() }
    }

    /**
     * Blocks the selected page by its perceptual hash; future downloads silently skip any
     * page whose dHash is within the configured Hamming distance threshold. The result
     * includes the hex so the caller can offer an "Undo" action. The coordinator owns the
     * flow and the error mapping.
     */
    private fun blockPage() {
        viewModelScope.launchNonCancellable { imageActions.blockPage() }
    }

    override fun reportBlockResult(result: BlockPageResult) {
        emitEffect(Event.BlockPageResult(result))
    }

    override fun logError(message: String, error: Throwable) {
        logcat(LogPriority.ERROR, error) { message }
    }

    /**
     * Unblocks a page by removing its dHash hex string from the blocked-pages set. The
     * coordinator delegates the removal; this is the drive.
     */
    private fun unblockPage(hex: String) {
        viewModelScope.launchNonCancellable { imageActions.unblockPage(hex) }
    }

    /**
     * Checks whether the currently selected page's dHash matches any entry in
     * the blocked-pages set (within the configured Hamming distance threshold).
     *
     * @return The matching hex hash if blocked, or `null` if not blocked / not computable.
     */
    suspend fun findMatchingBlockedHash(): String? = imageActions.findMatchingBlockedHash()

    enum class SetAsCoverResult {
        Success,
        AddToLibraryFirst,
        Error,
    }

    sealed interface BlockPageResult {
        data class Success(val hex: String) : BlockPageResult
        data object Error : BlockPageResult
    }

    sealed interface SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult
        class Error(val error: Throwable) : SaveImageResult
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (incognitoMode) return
        if (!trackPreferences.autoUpdateTrack().getSync()) return

        val manga = manga ?: return
        viewModelScope.launchNonCancellable {
            trackChapter.await(manga.id, readerChapter.chapter.chapterNumber)
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: ReaderChapter) {
        if (!chapter.chapter.read) return
        val manga = manga ?: return

        viewModelScope.launchNonCancellable {
            downloadManager.enqueueChaptersToDelete(listOf(chapter.chapter), manga)
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingChapters()
        }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val viewerChapters: ViewerChapters? = null,
        val bookmarked: Boolean = false,
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,

        /**
         * Viewer used to display the pages (pager, webtoon, ...).
         */
        val viewer: Viewer? = null,
        val dialog: Dialog? = null,
        val menuVisible: Boolean = false,
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            // Count only visible pages so the indicator reflects the number of pages
            // the user actually navigates through. Hidden pages (absorbed stubs and
            // blocked credit pages) are invisible and should not inflate the total.
            get() = currentChapter?.pages?.count { !it.isHidden } ?: -1
    }

    sealed interface Dialog {
        data object Loading : Dialog
        data class Settings(val initialPage: Int = 0) : Dialog
        data class PageActions(val page: ReaderPage) : Dialog
    }

    sealed interface Event {
        data object ReloadViewerChapters : Event
        data object PageChanged : Event
        data class SetOrientation(val orientation: Int) : Event
        data class SetCoverResult(val result: SetAsCoverResult) : Event
        data class BlockPageResult(val result: ReaderViewModel.BlockPageResult) : Event

        data class SavedImage(val result: SaveImageResult) : Event
        data class ShareImage(val uri: Uri, val page: ReaderPage) : Event
        data class CopyImage(val uri: Uri) : Event
    }
}
