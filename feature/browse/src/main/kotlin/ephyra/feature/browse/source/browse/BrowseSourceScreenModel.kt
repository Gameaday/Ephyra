package ephyra.feature.browse.source.browse

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.CheckboxState
import ephyra.core.common.preference.mapAsCheckboxState
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.category.model.Category
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.model.toMangaUpdate
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.interactor.GetRemoteManga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.track.interactor.AddTracks
import ephyra.presentation.core.util.asState
import ephyra.presentation.core.util.manga.removeCovers
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import eu.kanade.tachiyomi.source.model.Filter as SourceModelFilter

@HiltViewModel
class BrowseSourceScreenModel @Inject constructor(
    val sourceManager: SourceManager,
    private val sourcePreferences: SourcePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val coverCache: CoverCache,
    private val getRemoteManga: GetRemoteManga,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val getManga: GetManga,
    private val updateManga: UpdateManga,
    private val addTracks: AddTracks,
    private val getIncognitoState: GetIncognitoState,
) : ViewModel() {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(viewModelScope)

    private val _state = MutableStateFlow<State>(State(Listing.Popular))
    val state: StateFlow<State> = _state.asStateFlow()

    var sourceId: Long = -1L
        private set

    var source: eu.kanade.tachiyomi.source.Source? = null
        private set

    private var isInitialized = false

    fun init(sourceId: Long, listingQuery: String?) {
        if (isInitialized) return
        isInitialized = true
        this.sourceId = sourceId
        val src = sourceManager.getOrStub(sourceId)
        this.source = src

        _state.value = State(Listing.valueOf(listingQuery))

        if (src is CatalogueSource) {
            _state.update {
                var query: String? = null
                var listing = it.listing

                if (listing is Listing.Search) {
                    query = listing.query
                    listing = Listing.Search(query, src.getFilterList())
                }

                it.copy(
                    listing = listing,
                    filters = src.getFilterList(),
                    toolbarQuery = query,
                )
            }
        }

        if (!getIncognitoState.await(src.id)) {
            sourcePreferences.lastUsedSource().set(src.id)
        }
    }

    private val hideInLibraryItems = sourcePreferences.hideInLibraryItems().getSync()
    val mangaPagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            if (sourceId == -1L) return@map emptyFlow()
            Pager(PagingConfig(pageSize = 25, prefetchDistance = 15)) {
                getRemoteManga(sourceId, listing.query ?: "", listing.filters)
            }.flow.map { pagingData ->
                pagingData.map { manga ->
                    getManga.subscribe(manga.url, manga.source)
                        .map { it ?: manga }
                        .stateIn(viewModelScope)
                }
                    .let { pd -> if (hideInLibraryItems) pd.filter { !it.value.favorite } else pd }
            }
                .cachedIn(viewModelScope)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns()
        } else {
            libraryPreferences.portraitColumns()
        }.getSync()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    fun onEvent(event: BrowseSourceScreenEvent) {
        when (event) {
            is BrowseSourceScreenEvent.ResetFilters -> resetFilters()
            is BrowseSourceScreenEvent.SetListing -> setListing(event.listing)
            is BrowseSourceScreenEvent.SetFilters -> setFilters(event.filters)
            is BrowseSourceScreenEvent.Search -> search(event.query, event.filters)
            is BrowseSourceScreenEvent.SearchGenre -> searchGenre(event.genreName)
            is BrowseSourceScreenEvent.ChangeMangaFavorite -> changeMangaFavorite(event.manga)
            is BrowseSourceScreenEvent.AddFavorite -> addFavorite(event.manga)
            is BrowseSourceScreenEvent.MoveMangaToCategories -> moveMangaToCategories(event.manga, event.categoryIds)
            is BrowseSourceScreenEvent.OpenFilterSheet -> openFilterSheet()
            is BrowseSourceScreenEvent.SetDialog -> setDialog(event.dialog)
            is BrowseSourceScreenEvent.SetToolbarQuery -> setToolbarQuery(event.query)
        }
    }

    private fun resetFilters() {
        val src = source ?: return
        if (src !is CatalogueSource) return
        _state.update { it.copy(filters = src.getFilterList()) }
    }

    private fun setListing(listing: Listing) {
        _state.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    private fun setFilters(filters: FilterList) {
        val src = source ?: return
        if (src !is CatalogueSource) return
        _state.update { it.copy(filters = filters) }
    }

    private fun search(query: String? = null, filters: FilterList? = null) {
        val src = source ?: return
        if (src !is CatalogueSource) return

        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = src.getFilterList())

        _state.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                ),
                toolbarQuery = query ?: input.query,
            )
        }
    }

    private fun searchGenre(genreName: String) {
        val src = source ?: return
        if (src !is CatalogueSource) return

        val defaultFilters = src.getFilterList()
        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is SourceModelFilter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is SourceModelFilter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is SourceModelFilter.TriState -> filter.state = 1
                            is SourceModelFilter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is SourceModelFilter.Select<*>) {
                val index = sourceFilter.values.indexOfFirst { it is String && it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }

        _state.update {
            val listing = if (genreExists) {
                Listing.Search(query = null, filters = defaultFilters)
            } else {
                Listing.Search(query = genreName, filters = defaultFilters)
            }
            it.copy(
                filters = defaultFilters,
                listing = listing,
                toolbarQuery = listing.query,
            )
        }
    }

    private fun changeMangaFavorite(manga: Manga) {
        val src = source ?: return
        viewModelScope.launch {
            var new = manga.copy(
                favorite = !manga.favorite,
                dateAdded = when (manga.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
            } else {
                setMangaDefaultChapterFlags.await(manga)
                addTracks.bindEnhancedTrackers(manga, src)
            }

            updateManga.await(new.toMangaUpdate())
        }
    }

    private fun addFavorite(manga: Manga) {
        viewModelScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory().get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                defaultCategory != null -> {
                    moveMangaToCategories(manga, defaultCategory)
                    changeMangaFavorite(manga)
                }
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveMangaToCategories(manga)
                    changeMangaFavorite(manga)
                }
                else -> {
                    val preselectedIds = getCategories.await(manga.id).mapTo(HashSet()) { it.id }
                    setDialog(
                        Dialog.ChangeMangaCategory(
                            manga,
                            categories.mapAsCheckboxState { it.id in preselectedIds }.toImmutableList(),
                        ),
                    )
                }
            }
        }
    }

    suspend fun getCategories(): List<Category> {
        return getCategories.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCategory }
            .orEmpty()
    }

    suspend fun getDuplicateLibraryManga(manga: Manga): List<MangaWithChapterCount> {
        return getDuplicateLibraryManga.invoke(manga)
    }

    private fun moveMangaToCategories(manga: Manga, vararg categories: Category) {
        moveMangaToCategories(manga, categories.mapNotNull { if (it.id != 0L) it.id else null })
    }

    private fun moveMangaToCategories(manga: Manga, categoryIds: List<Long>) {
        viewModelScope.launchIO {
            setMangaCategories.await(
                mangaId = manga.id,
                categoryIds = categoryIds,
            )
        }
    }

    private fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    private fun setDialog(dialog: Dialog?) {
        _state.update { it.copy(dialog = dialog) }
    }

    private fun setToolbarQuery(query: String?) {
        _state.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        data object Popular : Listing(query = GetRemoteManga.QUERY_POPULAR, filters = FilterList())
        data object Latest : Listing(query = GetRemoteManga.QUERY_LATEST, filters = FilterList())
        data class Search(
            override val query: String?,
            override val filters: FilterList,
        ) : Listing(query = query, filters = filters)

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    GetRemoteManga.QUERY_POPULAR -> Popular
                    GetRemoteManga.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = FilterList())
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data class RemoveManga(val manga: Manga) : Dialog
        data class AddDuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeMangaCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState.State<Category>>,
        ) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }
}
