package ephyra.feature.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.di.IoDispatcher
import ephyra.core.common.preference.CheckboxState
import ephyra.core.common.preference.mapAsCheckboxState
import ephyra.core.common.util.insertSeparators
import ephyra.core.common.util.lang.toLocalDate
import ephyra.core.common.util.lang.toTimestampString
import ephyra.core.common.util.system.logcat
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.interactor.RemoveHistory
import ephyra.domain.history.model.HistoryWithRelations
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.AddTracks
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.util.formatChapterNumber
import ephyra.presentation.core.util.lang.searchResults
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import java.util.Date
import javax.inject.Inject

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModel @Inject constructor(
    private val addTracks: AddTracks,
    private val getCategories: GetCategories,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val historyRepository: HistoryRepository,
    private val getManga: GetManga,
    private val getNextChapters: GetNextChapters,
    private val libraryPreferences: LibraryPreferences,
    private val removeHistory: RemoveHistory,
    private val setMangaCategories: SetMangaCategories,
    private val updateManga: UpdateManga,
    private val sourceManager: SourceManager,
    /**
     * The scope [init]'s long-lived collection runs in.
     *
     * **Why this is a constructor parameter rather than something tests reach in and cancel.** The
     * collector launched here runs for the lifetime of the ViewModel. Under `runTest` the test scope
     * is not its parent, so nothing owned by the test ever waits for it or stops it: it survives its
     * own test and reports into the shared uncaught-exception collector, which makes a *later* test
     * fail with `UncaughtExceptionsBeforeTest` before executing a line. That is how
     * `HistoryViewModelTest` came to fail on `clearing all history emits HistoryCleared effect` — a
     * test with no relationship to whatever actually threw — and only on CI, where timing lets the
     * leak land between two tests rather than after the last one.
     *
     * Production passes `null` and gets [viewModelScope]. A test passes the `backgroundScope` of its
     * `runTest`, which is cancelled automatically when that test ends: the collector stops *because
     * the test finished*, not because the test remembered to stop it. That is the property a shared
     * scheduler cannot provide — it isolates coroutines without ever ending them.
     */
    private val collectionScope: CoroutineScope? = null,
    // Injectable so a test can drive this on its own scheduler. `launchIO`/`withIOContext` are
    // `Dispatchers.IO`, a real thread pool that `Dispatchers.setMain` and `advanceUntilIdle()`
    // cannot reach. Production is unchanged: `@IoDispatcher` binds `Dispatchers.IO`.
    // See `InjectableViewModelDispatcherTest`.
    @IoDispatcher
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseUdfViewModel<HistoryViewModel.State, HistoryScreenEvent, HistoryViewModel.Effect>(State()) {

    fun getSource(sourceId: Long): Source = sourceManager.getOrStub(sourceId)

    init {
        (collectionScope ?: viewModelScope).launch {
            combine(
                state.map { it.searchQuery }
                    .searchResults(debounce = 0L) { query ->
                        historyRepository.getHistory(query)
                            .distinctUntilChanged()
                            .catch { error ->
                                logcat(LogPriority.ERROR, error)
                                emitEffect(Effect.InternalError)
                            }
                    },
                // Expansion toggles re-derive the grouping from already-loaded entries; the
                // repository is never re-queried for a presentational expand/collapse.
                state.map { it.expandedSeriesIds }.distinctUntilChanged(),
            ) { entries, expanded ->
                entries.toHistoryUiModels(expanded)
            }
                .collect { newList -> updateState { it.copy(list = newList) } }
        }
    }

    /**
     * Turns flat history rows into the list the screen renders.
     *
     * **Cover interning happens here.** `HistoryMapper` builds a `MangaCover` per row, so a series
     * read 50 times arrived as 50 equal-but-distinct `MangaCover` instances. `MangaCover` is a
     * `data class`, so those instances are `equals()`-equal but **not identical**, and `MangaCover.kt`
     * does `remember(data, isSharedElement) { ImageRequest... }` — keyed on the instance. Fifty
     * distinct instances therefore build fifty `ImageRequest`s and issue fifty Coil requests for one
     * cover, even though Coil's memory cache dedupes the *result*. On a long history or updates
     * list that is a lot of duplicate work per scroll frame, and it is why the same cover could
     * appear to reload while others stayed warm.
     *
     * Interning by `mangaId` makes the instances identical, so every row of a series shares one
     * `remember` entry and one in-flight request. It is done in the ViewModel rather than in Coil
     * because this is where the "same series" relationship is already known — the `groupBy` below
     * is exactly that grouping.
     */
    private fun List<HistoryWithRelations>.toHistoryUiModels(
        expandedSeries: Set<Long>,
    ): List<HistoryUiModel> {
        // getHistory returns entries most-recent-first, so each manga group's first element is
        // its latest read and the groups themselves stay in most-recent-activity order.
        val topLevel = mutableListOf<HistoryUiModel>()
        groupBy { it.mangaId }.values.forEach { entries ->
            val latest = entries.first()
            // One cover instance per series, shared by every row in the group. `latest.coverData`
            // is used as the canonical instance because every row in a group describes the same
            // series and therefore carries an equal MangaCover (same id, url, lastModified).
            val sharedCover = latest.coverData
            val group = entries.map { entry -> entry.copy(coverData = sharedCover).toUiItem() }
            topLevel += if (group.size == 1) {
                HistoryUiModel.Item(group.first())
            } else {
                HistoryUiModel.Series(latest = group.first(), older = group.drop(1))
            }
        }
        return topLevel.insertSeparators { before, after ->
            val beforeDate = before?.representativeReadAt()?.time?.toLocalDate()
            val afterDate = after?.representativeReadAt()?.time?.toLocalDate()
            when {
                beforeDate != afterDate && afterDate != null -> HistoryUiModel.Header(afterDate)
                // Return null to avoid adding a separator between two items.
                else -> null
            }
        }
    }

    /**
     * Maps one persisted history row to what the row renders.
     *
     * All formatting happens here so the composable holds no domain knowledge: it must not know that
     * `chapterNumber < 0` is the sentinel for "unknown", and it must not carry a `Date` formatter.
     * Both were previously inline in `HistoryItem`, which made the rules untestable without
     * composing and made a `HistoryView` column rename a UI change.
     */
    private fun HistoryWithRelations.toUiItem(): HistoryUiItem = HistoryUiItem(
        id = id,
        mangaId = mangaId,
        chapterId = chapterId,
        title = title,
        // `< 0` is the source's "no usable chapter number" sentinel; render the timestamp alone.
        chapterLabel = if (chapterNumber > -1) formatChapterNumber(chapterNumber) else null,
        readAtLabel = readAt?.toTimestampString().orEmpty(),
        coverData = coverData,
        // Offered only when the series is not already in the library. Read off the interned cover so
        // it cannot disagree with the cover that is actually drawn.
        canAddToLibrary = !coverData.isMangaFavorite,
        readAt = readAt,
    )

    /**
     * The read date a top-level entry is dated by for day headers. A stacked series is dated by
     * its latest read; its older chapters may span earlier days, which is the intended
     * trade-off — the group lives where the user last touched that series.
     */
    private fun HistoryUiModel.representativeReadAt(): Date? = when (this) {
        is HistoryUiModel.Series -> latest.readAt
        is HistoryUiModel.Item -> item.readAt
        is HistoryUiModel.Header -> null
    }

    // UDF entry-point: all UI interactions are routed through this single method

    override fun onEvent(event: HistoryScreenEvent) {
        when (event) {
            is HistoryScreenEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            is HistoryScreenEvent.GetNextChapterForManga -> getNextChapterForManga(event.mangaId, event.chapterId)
            is HistoryScreenEvent.AddFavoriteById -> addFavorite(event.mangaId)
            is HistoryScreenEvent.AddFavorite -> addFavorite(event.manga)
            is HistoryScreenEvent.MoveMangaToCategoriesAndAddToLibrary ->
                moveMangaToCategoriesAndAddToLibrary(event.manga, event.categories)
            is HistoryScreenEvent.RemoveFromHistory -> removeFromHistory(event.historyId)
            is HistoryScreenEvent.RemoveAllForManga -> removeAllFromHistory(event.mangaId)
            is HistoryScreenEvent.RemoveAllHistory -> removeAllHistory()
            is HistoryScreenEvent.ShowMigrateDialog -> showMigrateDialog(event.target, event.current)
            is HistoryScreenEvent.ShowChangeCategoryDialog -> showChangeCategoryDialog(event.manga)
            is HistoryScreenEvent.SetDialog -> setDialog(event.dialog)
            is HistoryScreenEvent.ToggleSeriesExpanded -> toggleSeriesExpanded(event.mangaId)
        }
    }

    // Suspend accessor — returns a value so cannot be modelled as a fire-and-
    // forget event; callers (HistoryTab) invoke this directly in a LaunchedEffect.

    suspend fun getNextChapter(): Chapter? {
        return withContext(ioDispatcher) { getNextChapters.await(onlyUnread = false).firstOrNull() }
    }

    // Private business logic - not part of the public API
    private fun getNextChapterForManga(mangaId: Long, chapterId: Long) {
        viewModelScope.launch(ioDispatcher) {
            sendNextChapterEvent(getNextChapters.await(mangaId, chapterId, onlyUnread = false))
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<Chapter>) {
        val chapter = chapters.firstOrNull()
        emitEffect(Effect.OpenChapter(chapter))
    }

    private fun removeFromHistory(historyId: Long) {
        viewModelScope.launch(ioDispatcher) {
            removeHistory.await(historyId)
        }
    }

    private fun removeAllFromHistory(mangaId: Long) {
        viewModelScope.launch(ioDispatcher) {
            removeHistory.awaitByMangaId(mangaId)
        }
    }

    private fun removeAllHistory() {
        viewModelScope.launch(ioDispatcher) {
            val result = removeHistory.awaitAll()
            if (!result) return@launch
            emitEffect(Effect.HistoryCleared)
        }
    }

    private fun updateSearchQuery(query: String?) {
        updateState { it.copy(searchQuery = query) }
    }

    private fun setDialog(dialog: Dialog?) {
        updateState { it.copy(dialog = dialog) }
    }

    /** Toggles a stacked series group. Presentational only — no repository call. */
    private fun toggleSeriesExpanded(mangaId: Long) {
        updateState { current ->
            val ids = current.expandedSeriesIds
            current.copy(
                expandedSeriesIds = if (mangaId in ids) ids.remove(mangaId) else ids.add(mangaId),
            )
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    private suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveMangaToCategory(mangaId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveMangaToCategory(mangaId, categoryIds)
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        viewModelScope.launch(ioDispatcher) {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    private fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        viewModelScope.launch(ioDispatcher) {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    private fun addFavorite(mangaId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val manga = getManga.await(mangaId) ?: return@launch

            val duplicates = getDuplicateLibraryManga(manga)
            if (duplicates.isNotEmpty()) {
                updateState { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
                return@launch
            }

            addFavorite(manga)
        }
    }

    private fun addFavorite(manga: Manga) {
        viewModelScope.launch(ioDispatcher) {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launch
                    moveMangaToCategory(manga.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launch
                    moveMangaToCategory(manga.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(manga)
            }

            // Sync with tracking services if applicable
            addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
        }
    }

    private fun showMigrateDialog(target: Manga, current: Manga) {
        updateState { currentState ->
            currentState.copy(dialog = Dialog.Migrate(target = target, current = current))
        }
    }

    private fun showChangeCategoryDialog(manga: Manga) {
        viewModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            updateState { currentState ->
                currentState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<HistoryUiModel>? = null,
        val dialog: Dialog? = null,
        val expandedSeriesIds: PersistentSet<Long> = persistentSetOf(),
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val mangaId: Long, val historyId: Long) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog

        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }

    sealed interface Effect {
        data class OpenChapter(val chapter: Chapter?) : Effect
        data object InternalError : Effect
        data object HistoryCleared : Effect
    }
}
