package ephyra.feature.manga

import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.preference.CheckboxState
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.mapAsCheckboxState
import ephyra.core.common.util.addOrRemove
import ephyra.core.common.util.insertSeparators
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.launchNonCancellable
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.core.download.DownloadCache
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.interactor.GetAvailableScanlators
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.getChapterSort
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.jellyfin.interactor.SyncJellyfin
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.model.applyFilter
import ephyra.domain.manga.model.chaptersFiltered
import ephyra.domain.manga.model.downloadedFilter
import ephyra.domain.manga.model.toSManga
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.feature.manga.presentation.components.ChapterDownloadAction
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.util.asState
import ephyra.presentation.core.util.manga.DownloadAction
import ephyra.presentation.core.util.manga.removeCovers
import ephyra.presentation.core.util.system.toast
import ephyra.source.local.isLocal
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class MangaViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val getManga: ephyra.domain.manga.interactor.GetManga,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val getMangaAndChapters: GetMangaWithChapters,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getAvailableScanlators: GetAvailableScanlators,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val getCategories: GetCategories,
    val sourceManager: SourceManager,
    private val mangaInfoInteractor: ephyra.feature.manga.interactor.MangaInfoInteractor,
    private val mangaChapterInteractor: ephyra.feature.manga.interactor.MangaChapterInteractor,
    private val mangaTrackInteractor: ephyra.feature.manga.interactor.MangaTrackInteractor,
    private val syncJellyfin: ephyra.domain.jellyfin.interactor.SyncJellyfin,
    val libraryPreferences: LibraryPreferences,
    val readerPreferences: ReaderPreferences,
    val basePreferences: ephyra.domain.base.BasePreferences,
    val coverCache: ephyra.domain.manga.service.CoverCache,
    val appInfo: AppInfo,
) : BaseUdfViewModel<MangaViewModel.State, MangaScreenEvent, MangaScreenEffect>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val skipRead by readerPreferences.skipRead().asState(viewModelScope)
    private val skipFiltered by readerPreferences.skipFiltered().asState(viewModelScope)
    private val skipDupe by readerPreferences.skipDupe().asState(viewModelScope)

    private var isInitialized = false

    init {
        val navMangaId: Long? = savedStateHandle.get<Long>("mangaId")
            ?: savedStateHandle.get<String>("mangaId")?.toLongOrNull()
        val navFromSource: Boolean = savedStateHandle.get<Boolean>("fromSource")
            ?: savedStateHandle.get<String>("fromSource")?.toBooleanStrictOrNull()
            ?: false
        if (navMangaId != null && navMangaId > 0L) {
            init(navMangaId, navFromSource)
        }
    }

    fun init(mangaId: Long, isFromSource: Boolean) {
        if (isInitialized) return
        isInitialized = true
        savedStateHandle["mangaId"] = mangaId
        savedStateHandle["fromSource"] = isFromSource

        viewModelScope.launch {
            getManga.subscribe(mangaId)
                .distinctUntilChanged()
                .collect { manga ->
                    updateState { state ->
                        when (state) {
                            is State.Loading -> State.Success(
                                manga = manga,
                                source = sourceManager.getOrStub(manga.source),
                                isAnySelected = false,
                                chapterListItems = emptyList(),
                                isFromSource = isFromSource,
                            )
                            is State.Success -> state.copy(manga = manga, isFromSource = isFromSource)
                        }
                    }
                }
        }

        viewModelScope.launch {
            sourceManager.isInitialized.first { it }
            updateState { state ->
                val success = state as? State.Success ?: return@updateState state
                success.copy(source = sourceManager.getOrStub(success.manga.source))
            }
        }

        if (isFromSource) {
            viewModelScope.launchIO {
                sourceManager.isInitialized.first { it }
                val manga = getManga.await(mangaId) ?: return@launchIO
                val src = sourceManager.getOrStub(manga.source)
                runCatching {
                    mangaChapterInteractor.syncChaptersWithSource(
                        chapters = emptyList(),
                        manga = manga,
                        source = src,
                        manualFetch = false,
                    )
                }.onFailure { e ->
                    logcat(LogPriority.ERROR, e) { "Failed to auto-fetch manga from source" }
                }
            }
        }

        viewModelScope.launch {
            combine(
                getMangaAndChapters.subscribe(mangaId),
                downloadCache.changes,
                downloadManager.queueState,
                libraryPreferences.swipeToEndAction().changes(),
                libraryPreferences.swipeToStartAction().changes(),
            ) { (manga, chapters), _, queue, swipeStart, swipeEnd ->
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(
                        manga = manga,
                        chapters = chapters,
                        chapterListItems = chapters.toChapterListItems(manga, queue, success.selectedChapterIds),
                        chapterSwipeStartAction = swipeStart,
                        chapterSwipeEndAction = swipeEnd,
                    )
                }
            }.collect {}
        }

        viewModelScope.launch {
            mangaTrackInteractor.loggedInTrackersFlow()
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { hasLoggedIn ->
                    updateState { state ->
                        when (state) {
                            is State.Loading -> state
                            is State.Success -> state.copy(hasLoggedInTrackers = hasLoggedIn)
                        }
                    }
                }
        }
    }

    fun toggleFavorite() = toggleFavoriteInternal(checkDuplicate = true)

    /**
     * Toggles the favorite state. When adding to the library, optionally checks for
     * duplicates first and shows the duplicate-manga dialog instead of silently adding.
     */
    private fun toggleFavoriteInternal(checkDuplicate: Boolean) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            if (checkDuplicate && !manga.favorite) {
                val duplicates = getDuplicateLibraryManga(manga)
                if (duplicates.isNotEmpty()) {
                    updateState { state ->
                        val success = state as? State.Success ?: return@updateState state
                        success.copy(dialog = Dialog.DuplicateManga(duplicates))
                    }
                    return@launchIO
                }
            }
            setFavorite(manga, !manga.favorite)
        }
    }

    private suspend fun setFavorite(manga: Manga, favorite: Boolean) {
        if (mangaInfoInteractor.updateFavorite(manga.id, favorite)) {
            mangaInfoInteractor.markJellyfinFavoriteIfLinked(manga, favorite)
            if (favorite) {
                mangaInfoInteractor.syncLibraryAdditionToTrackers(manga)
            }
        }
    }

    /** Adds the manga to the selected categories (creating a library entry when needed). */
    private fun moveMangaToCategoriesAndAddToLibrary(target: Manga, categories: List<Long>) {
        viewModelScope.launchIO {
            mangaInfoInteractor.setMangaCategories(target.id, categories)
            if (!target.favorite) {
                setFavorite(target, true)
            }
            updateState { state ->
                val success = state as? State.Success ?: return@updateState state
                success.copy(dialog = null)
            }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        viewModelScope.launchIO {
            sourceManager.isInitialized.first { it }
            val success = successState ?: return@launchIO
            val manga = success.manga
            val source = sourceManager.getOrStub(manga.source)
            runCatching {
                mangaChapterInteractor.syncChaptersWithSource(
                    chapters = success.chapters,
                    manga = manga,
                    source = source,
                    manualFetch = manualFetch,
                )
            }.onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Failed to fetch chapters from source" }
                if (manualFetch) {
                    emitEffect(
                        MangaScreenEffect.ShowToast(
                            e.message ?: "Failed to fetch chapters from source '${source.name}'",
                        ),
                    )
                }
            }
        }
    }

    /** Runs [block] with the current success-state manga on the IO dispatcher, if available. */
    private fun withSuccessManga(block: suspend (Manga) -> Unit) {
        val success = successState ?: return
        viewModelScope.launchIO {
            block(success.manga)
        }
    }

    /** Applies a metadata edit and mirrors it to Jellyfin when linked. */
    private fun editManga(update: (Manga) -> MangaUpdate) {
        val manga = manga ?: return
        viewModelScope.launchIO {
            mangaInfoInteractor.updateManga(update(manga))
            mangaInfoInteractor.pushMetadataToJellyfinIfLinked(manga)
        }
    }

    private fun onChapterSwipe(
        item: ChapterList.Item,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val success = successState ?: return
        viewModelScope.launchIO {
            when (swipeAction) {
                LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                    mangaChapterInteractor.markChaptersRead(
                        chapters = listOf(item.chapter),
                        read = !item.chapter.read,
                    )
                }
                LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                    mangaChapterInteractor.bookmarkChapters(
                        chapters = listOf(item.chapter),
                        bookmarked = !item.chapter.bookmark,
                    )
                }
                LibraryPreferences.ChapterSwipeAction.Download -> {
                    when (item.downloadState) {
                        Download.State.DOWNLOADED -> {
                            mangaChapterInteractor.deleteChapters(
                                chapters = listOf(item.chapter),
                                manga = success.manga,
                                source = success.source,
                            )
                        }
                        Download.State.NOT_DOWNLOADED, Download.State.ERROR -> {
                            mangaChapterInteractor.downloadChapters(
                                chapters = listOf(item.chapter),
                                manga = success.manga,
                            )
                        }
                        Download.State.QUEUE, Download.State.DOWNLOADING -> {}
                    }
                }
                LibraryPreferences.ChapterSwipeAction.Disabled -> {}
            }
        }
    }

    override fun onEvent(event: MangaScreenEvent) {
        when (event) {
            is MangaScreenEvent.ToggleSelection -> {
                updateSelection { selected, _ ->
                    if (event.selected) selected.adding(event.item.id) else selected.removing(event.item.id)
                }
            }
            is MangaScreenEvent.ToggleAllSelection -> {
                updateSelection { selected, success ->
                    if (event.selected) {
                        selected.addingAll(success.chapters.mapNotNull { it.id })
                    } else {
                        selected.removingAll(success.chapters.mapNotNull { it.id })
                    }
                }
            }
            is MangaScreenEvent.ClearSelection -> {
                updateSelection { _, _ -> persistentSetOf() }
            }
            is MangaScreenEvent.InvertSelection -> {
                updateSelection { selected, success ->
                    val allIds = success.chapters.mapNotNull { it.id }.toSet()
                    val inverted = allIds.filter { it !in selected }
                    persistentSetOf<Long>().addingAll(inverted)
                }
            }
            MangaScreenEvent.DismissDialog -> {
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(dialog = null)
                }
            }
            MangaScreenEvent.ShowSettingsDialog -> {
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(dialog = Dialog.SettingsSheet)
                }
            }
            MangaScreenEvent.ShowTrackDialog -> {
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(dialog = Dialog.TrackSheet)
                }
            }
            MangaScreenEvent.ShowCoverDialog -> {
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(dialog = Dialog.FullCover)
                }
            }
            MangaScreenEvent.ShowEditMetadataDialog -> {
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(dialog = Dialog.EditMetadata)
                }
            }
            is MangaScreenEvent.ShowDeleteChapterDialog -> {
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(dialog = Dialog.DeleteChapters(event.chapters))
                }
            }
            is MangaScreenEvent.ShowMigrateDialog -> {
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(dialog = Dialog.Migrate(current = success.manga, target = event.duplicate))
                }
            }
            MangaScreenEvent.ShowChangeCategoryDialog -> {
                viewModelScope.launchIO {
                    val manga = manga ?: return@launchIO
                    val categories = getCategories.await()
                    val selection = categories.map { category ->
                        CheckboxState.State.None(category) as CheckboxState<Category>
                    }
                    updateState { state ->
                        val success = state as? State.Success ?: return@updateState state
                        success.copy(dialog = Dialog.ChangeCategory(manga, selection))
                    }
                }
            }
            MangaScreenEvent.ShowSetFetchIntervalDialog -> {
                updateState { state ->
                    val success = state as? State.Success ?: return@updateState state
                    success.copy(dialog = Dialog.SetFetchInterval(success.manga))
                }
            }
            is MangaScreenEvent.SetFetchInterval -> {
                viewModelScope.launchIO {
                    val updatedManga = event.manga.copy(fetchInterval = -event.interval)
                    mangaInfoInteractor.updateFetchInterval(updatedManga)
                    updateState { state ->
                        val success = state as? State.Success ?: return@updateState state
                        success.copy(
                            manga = updatedManga,
                            dialog = null,
                        )
                    }
                }
            }
            MangaScreenEvent.ShowShareRecommendationDialog -> {
                viewModelScope.launch {
                    val success = state.value as? State.Success ?: return@launch
                    val chapterItems = success.chapterListItems.filterIsInstance<ChapterList.Item>()
                    val readCount = chapterItems.count { it.chapter.read }
                    val totalCount = chapterItems.size
                    val tracks = runCatching {
                        mangaTrackInteractor.getTracks(success.manga.id)
                    }.getOrDefault(emptyList())
                    val trackingScore = tracks.firstOrNull { it.score > 0.0 }?.score
                    val url = (success.source as? HttpSource)?.let {
                        runCatching { it.getMangaUrl(success.manga.toSManga()) }.getOrNull()
                    }
                    updateState { state ->
                        val current = state as? State.Success ?: return@updateState state
                        current.copy(
                            dialog = Dialog.ShareRecommendation(
                                manga = current.manga,
                                readChapters = readCount,
                                totalChapters = totalCount,
                                score = trackingScore,
                                url = url,
                            ),
                        )
                    }
                }
            }
            is MangaScreenEvent.FetchAllFromSource -> {
                fetchAllFromSource(event.manualFetch)
            }
            is MangaScreenEvent.ToggleFavorite -> {
                toggleFavoriteInternal(event.checkDuplicate)
            }
            is MangaScreenEvent.MoveMangaToCategoriesAndAddToLibrary -> {
                moveMangaToCategoriesAndAddToLibrary(event.manga, event.categories)
            }
            is MangaScreenEvent.ChapterSwipe -> {
                onChapterSwipe(event.chapterItem, event.swipeAction)
            }
            is MangaScreenEvent.RunChapterDownloadActions -> {
                runChapterDownloadActions(event.items, event.action)
            }
            is MangaScreenEvent.RunDownloadAction -> {
                runDownloadAction(event.action)
            }
            is MangaScreenEvent.MarkPreviousChapterRead -> {
                markPreviousChapterRead(event.pointer)
            }
            is MangaScreenEvent.MarkChaptersRead -> {
                viewModelScope.launchIO {
                    mangaChapterInteractor.markChaptersRead(event.chapters, event.read)
                }
            }
            is MangaScreenEvent.BookmarkChapters -> {
                viewModelScope.launchIO {
                    mangaChapterInteractor.bookmarkChapters(event.chapters, event.bookmarked)
                }
            }
            is MangaScreenEvent.DeleteChapters -> {
                deleteChapters(event.chapters)
            }
            is MangaScreenEvent.SetUnreadFilter -> {
                withSuccessManga { mangaChapterInteractor.setUnreadFilter(it, event.state) }
            }
            is MangaScreenEvent.SetDownloadedFilter -> {
                withSuccessManga { mangaChapterInteractor.setDownloadedFilter(it, event.state) }
            }
            is MangaScreenEvent.SetBookmarkedFilter -> {
                withSuccessManga { mangaChapterInteractor.setBookmarkedFilter(it, event.state) }
            }
            is MangaScreenEvent.SetDisplayMode -> {
                withSuccessManga { mangaChapterInteractor.setDisplayMode(it, event.mode) }
            }
            is MangaScreenEvent.SetSorting -> {
                withSuccessManga { mangaChapterInteractor.setSorting(it, event.sort) }
            }
            is MangaScreenEvent.SetCurrentSettingsAsDefault -> {
                withSuccessManga { mangaChapterInteractor.setCurrentSettingsAsDefault(it, event.applyToExisting) }
            }
            MangaScreenEvent.ResetToDefaultSettings -> {
                withSuccessManga { mangaChapterInteractor.resetToDefaultSettings(it) }
            }
            is MangaScreenEvent.EditTitle -> {
                editManga { MangaUpdate(id = it.id, title = event.value) }
            }
            is MangaScreenEvent.EditAuthor -> {
                editManga { MangaUpdate(id = it.id, author = event.value) }
            }
            is MangaScreenEvent.EditArtist -> {
                editManga { MangaUpdate(id = it.id, artist = event.value) }
            }
            is MangaScreenEvent.EditDescription -> {
                editManga { MangaUpdate(id = it.id, description = event.value) }
            }
            is MangaScreenEvent.EditStatus -> {
                editManga { MangaUpdate(id = it.id, status = event.value) }
            }
            is MangaScreenEvent.EditGenres -> {
                editManga { MangaUpdate(id = it.id, genre = event.value) }
            }
            is MangaScreenEvent.ToggleLockedField -> {
                editManga { MangaUpdate(id = it.id, lockedFields = it.lockedFields xor event.field) }
            }
            is MangaScreenEvent.SetLockedFields -> {
                editManga { MangaUpdate(id = it.id, lockedFields = event.mask) }
            }
            is MangaScreenEvent.SetMetadataSource -> {
                editManga { MangaUpdate(id = it.id, metadataSource = event.sourceId, metadataUrl = event.mangaUrl) }
            }
            is MangaScreenEvent.SetExcludedScanlators -> {
                setExcludedScanlators(event.excludedScanlators)
            }
            MangaScreenEvent.RefreshFromAuthority -> {
                withSuccessManga { mangaInfoInteractor.refreshFromAuthority(it) }
            }
            MangaScreenEvent.UnlinkAuthority -> {
                withSuccessManga { mangaInfoInteractor.unlinkAuthority(it) }
            }
            MangaScreenEvent.ResolveCanonicalId -> {
                // Full-library canonical matching is an explicit library action; resolving a
                // single manga is deferred until MatchUnlinkedManga exposes a per-manga API.
            }
        }
    }

    private fun runChapterDownloadActions(
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
    ) {
        if (items.isEmpty()) return
        val success = successState ?: return
        val chapters = items.map { it.chapter }
        when (action) {
            ChapterDownloadAction.START -> {
                mangaChapterInteractor.downloadChapters(chapters, success.manga)
            }
            ChapterDownloadAction.START_NOW -> {
                viewModelScope.launchIO {
                    mangaChapterInteractor.downloadChapters(chapters, success.manga)
                    downloadManager.startDownloadNow(chapters.first().id)
                }
            }
            ChapterDownloadAction.CANCEL -> {
                viewModelScope.launchIO {
                    val queued = chapters.mapNotNull { downloadManager.getQueuedDownloadOrNull(it.id) }
                    downloadManager.cancelQueuedDownloads(queued)
                }
            }
            ChapterDownloadAction.DELETE -> {
                deleteChapters(chapters)
            }
        }
    }

    private fun runDownloadAction(action: DownloadAction) {
        val success = successState ?: return
        viewModelScope.launchIO {
            val items = success.chapterListItems.filterIsInstance<ChapterList.Item>()
            when (action) {
                DownloadAction.NEXT_1_CHAPTER -> downloadNextChapters(items, success.manga, 1)
                DownloadAction.NEXT_5_CHAPTERS -> downloadNextChapters(items, success.manga, 5)
                DownloadAction.NEXT_10_CHAPTERS -> downloadNextChapters(items, success.manga, 10)
                DownloadAction.NEXT_25_CHAPTERS -> downloadNextChapters(items, success.manga, 25)
                DownloadAction.UNREAD_CHAPTERS -> downloadNextChapters(items, success.manga, null)
                DownloadAction.BOOKMARKED_CHAPTERS -> {
                    val chapters = items
                        .filter { it.chapter.bookmark && it.downloadState == Download.State.NOT_DOWNLOADED }
                        .map { it.chapter }
                    mangaChapterInteractor.downloadChapters(chapters, success.manga)
                }
                DownloadAction.SYNC_TO_JELLYFIN, DownloadAction.SYNC_ALL_TO_JELLYFIN -> {
                    syncChaptersToJellyfin(
                        success.manga,
                        items.map { it.chapter },
                        SyncJellyfin.SyncAction.SYNC_ALL_TO_JELLYFIN,
                    )
                }
                DownloadAction.SYNC_READ_TO_JELLYFIN -> {
                    syncChaptersToJellyfin(
                        success.manga,
                        items.map { it.chapter }.filter { it.read },
                        SyncJellyfin.SyncAction.SYNC_READ_TO_JELLYFIN,
                    )
                }
            }
        }
    }

    private suspend fun downloadNextChapters(
        items: List<ChapterList.Item>,
        manga: Manga,
        count: Int?,
    ) {
        val chapters = items
            .filter { !it.chapter.read && it.downloadState == Download.State.NOT_DOWNLOADED }
            .let { if (count == null) it else it.take(count) }
            .map { it.chapter }
        mangaChapterInteractor.downloadChapters(chapters, manga)
    }

    private suspend fun syncChaptersToJellyfin(
        manga: Manga,
        chapters: List<Chapter>,
        syncAction: SyncJellyfin.SyncAction,
    ) {
        val downloadStates = chapters.associate { chapter ->
            chapter.id to downloadManager.isChapterDownloaded(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                chapterUrl = chapter.url,
                mangaTitle = manga.title,
                sourceId = manga.source,
            )
        }
        syncJellyfin.syncToJellyfin(manga, chapters, downloadStates, syncAction)
    }

    /** Marks every chapter displayed above [pointer] (in the current sort order) as read. */
    private fun markPreviousChapterRead(pointer: Chapter) {
        val success = successState ?: return
        val items = success.chapterListItems.filterIsInstance<ChapterList.Item>()
        val index = items.indexOfFirst { it.chapter.id == pointer.id }
        if (index <= 0) return
        val chapters = items.take(index).map { it.chapter }
        viewModelScope.launchIO {
            mangaChapterInteractor.markChaptersRead(chapters, true)
        }
    }

    private fun deleteChapters(chapters: List<Chapter>) {
        val success = successState ?: return
        mangaChapterInteractor.deleteChapters(chapters, success.manga, success.source)
    }

    private fun setExcludedScanlators(excludedScanlators: Set<String>) {
        viewModelScope.launchIO {
            val success = successState ?: return@launchIO
            mangaInfoInteractor.setExcludedScanlators(success.manga.id, excludedScanlators)
            updateState { state ->
                val current = state as? State.Success ?: return@updateState state
                current.copy(excludedScanlators = excludedScanlators)
            }
        }
    }

    private fun updateSelection(transform: (PersistentSet<Long>, State.Success) -> PersistentSet<Long>) {
        updateState { state ->
            val success = state as? State.Success ?: return@updateState state
            val newSelection = transform(success.selectedChapterIds, success)
            success.copy(
                selectedChapterIds = newSelection,
                isAnySelected = newSelection.isNotEmpty(),
                chapterListItems = success.chapterListItems.map { item ->
                    if (item is ChapterList.Item) {
                        item.copy(selected = item.id in newSelection)
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun List<Chapter>.toChapterListItems(
        manga: Manga,
        queue: List<Download>,
        selectedIds: PersistentSet<Long>,
    ): List<ChapterList> {
        val items = map { chapter ->
            val download = queue.find { it.chapter.id == chapter.id }
            ChapterList.Item(
                chapter = chapter,
                downloadState = download?.status ?: if (
                    downloadManager.isChapterDownloaded(
                        chapter.name,
                        chapter.scanlator,
                        chapter.url,
                        manga.title,
                        manga.source,
                    )
                ) {
                    Download.State.DOWNLOADED
                } else {
                    Download.State.NOT_DOWNLOADED
                },
                downloadProgress = download?.progress ?: 0,
                selected = chapter.id in selectedIds,
            )
        }

        return items.insertSeparators { before, after ->
            // Detect gaps in chapter numbering and insert a missing count indicator
            if (before != null && after != null) {
                val beforeNum = before.chapter.chapterNumber
                val afterNum = after.chapter.chapterNumber
                if (beforeNum > 0 && afterNum > 0) {
                    val diff = (beforeNum - afterNum).toInt() - 1
                    if (diff > 0) {
                        ChapterList.MissingCount(id = -1L, count = diff)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    // Skeleton implementations for missing methods to fix build
    fun getNextUnreadChapter(): Chapter? {
        val success = successState ?: return null
        return success.chapterListItems.filterIsInstance<ChapterList.Item>().getNextUnread(success.manga)
    }

    private fun List<ChapterList.Item>.getNextUnread(manga: Manga): Chapter? {
        // Simple implementation for now
        return find { !it.chapter.read }?.chapter
    }

    @Immutable
    sealed class State {
        @Immutable
        data object Loading : State()

        @Immutable
        data class Success(
            val manga: Manga,
            val source: Source,
            val chapters: List<Chapter> = emptyList(),
            val chapterListItems: List<ChapterList> = emptyList(),
            val selectedChapterIds: PersistentSet<Long> = persistentSetOf(),
            val isAnySelected: Boolean = false,
            val filterActive: Boolean = false,
            val scanlatorFilterActive: Boolean = false,
            val availableScanlators: Set<String> = emptySet(),
            val excludedScanlators: Set<String> = emptySet(),
            val isRefreshingData: Boolean = false,
            val isJellyfinLinked: Boolean = false,
            val isUpdateIntervalEnabled: Boolean = false,
            val trackingCount: Int = 0,
            val metadataSourceName: String? = null,
            val jellyfinServerUrl: String? = null,
            val imagesInDescription: Boolean = false,
            val isFromSource: Boolean = false,
            val chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction =
                LibraryPreferences.ChapterSwipeAction.ToggleRead,
            val chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction =
                LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
            val dialog: Dialog? = null,
            val hasLoggedInTrackers: Boolean = false,
        ) : State()
    }

    @Immutable
    sealed interface Dialog {
        @Immutable
        data object SettingsSheet : Dialog

        @Immutable
        data object TrackSheet : Dialog

        @Immutable
        data object FullCover : Dialog

        @Immutable
        data object EditMetadata : Dialog

        @Immutable
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog

        @Immutable
        data class DuplicateManga(val duplicates: List<MangaWithChapterCount>) : Dialog

        @Immutable
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog

        @Immutable
        data class Migrate(val current: Manga, val target: Manga) : Dialog

        @Immutable
        data class SetFetchInterval(val manga: Manga) : Dialog

        @Immutable
        data class ShareRecommendation(
            val manga: Manga,
            val readChapters: Int,
            val totalChapters: Int,
            val score: Double?,
            val url: String?,
        ) : Dialog
    }
}

@Immutable
sealed class ChapterList {
    abstract val id: Long

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: Download.State = Download.State.NOT_DOWNLOADED,
        val downloadProgress: Int = 0,
        val selected: Boolean = false,
    ) : ChapterList() {
        override val id: Long = chapter.id
    }

    @Immutable
    data class MissingCount(
        override val id: Long,
        val count: Int,
    ) : ChapterList()
}
