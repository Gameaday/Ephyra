package ephyra.feature.browse.source.globalsearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.TitleNormalizer
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
    searchCache: GlobalSearchCache,
    private val recentSearches: RecentSearches,
    private val getLibraryManga: GetLibraryManga,
    unifiedSearchEngine: ephyra.domain.manga.interactor.UnifiedSearchEngine,
) : SearchViewModel(
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
    searchCache = searchCache,
    unifiedSearchEngine = unifiedSearchEngine,
) {

    /**
     * Library matches for the current query — powers the "From your library" section at the
     * top of the results. Instant (local DB), no network; same normalization engine as Smart
     * Merge. Empty unless the query is >= 2 chars after normalization, so it is safe to
     * render as-you-type without firing network traffic.
     */
    val libraryMatches: StateFlow<List<Manga>> = combine(
        state.map { it.searchQuery.orEmpty().trim() }.distinctUntilChanged(),
        getLibraryManga.subscribe(),
    ) { query, library ->
        if (query.isBlank()) return@combine emptyList()
        val normalizedQuery = TitleNormalizer.forEquality(query)
        if (normalizedQuery.length < 2) return@combine emptyList()
        library.asSequence()
            .map { it.manga }
            .filter { manga ->
                TitleNormalizer.forEquality(manga.title).contains(normalizedQuery)
            }
            .take(SUGGESTION_LIMIT)
            .toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * Recent-query chip row: recent searches filtered to the active query, for one-tap
     * re-runs. Library titles are intentionally no longer chips — they live in the
     * [libraryMatches] section as full rows, so the page reads Library → recents → sources
     * instead of duplicating the same work as both a chip and a card.
     */
    val suggestions: StateFlow<List<String>> = combine(
        state.map { it.searchQuery.orEmpty().trim() }.distinctUntilChanged(),
        // recentSearches.observe() is reactive, so a freshly recorded search appears here
        // without needing a query change to re-trigger the combine.
        recentSearches.observe(),
    ) { query, recordedRecents ->
        val recents = recordedRecents.filterNot { it.equals(query, ignoreCase = true) }
        if (query.isBlank()) {
            recents.take(SUGGESTION_LIMIT)
        } else {
            recents.filter { it.contains(query, ignoreCase = true) }
                .take(SUGGESTION_LIMIT)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        val navQuery: String? = savedStateHandle.get<String>("query")
        if (!navQuery.isNullOrBlank()) {
            init(navQuery)
        }
    }

    override fun search() {
        state.value.searchQuery?.let { recentSearches.record(it) }
        if (!sourceManager.isInitialized.value) {
            viewModelScope.launch {
                sourceManager.isInitialized.first { it }
                super.search()
            }
            return
        }
        super.search()
    }

    private var isInitialized = false

    fun init(initialQuery: String = "", initialExtensionFilter: String? = null) {
        if (isInitialized) return
        isInitialized = true
        savedStateHandle["query"] = initialQuery

        updateSearchQuery(initialQuery)
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                setSourceFilter(SourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || it.id in pinnedSourceIds }
    }

    private companion object {
        const val SUGGESTION_LIMIT = 6
    }
}
