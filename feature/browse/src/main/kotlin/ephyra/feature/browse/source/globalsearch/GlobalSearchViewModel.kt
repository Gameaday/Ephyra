package ephyra.feature.browse.source.globalsearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.TitleNormalizer
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
    extensionUpdateManager: ExtensionManager,
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
     * Search suggestions: recent queries when the field is blank or short, plus
     * fuzzy-matched library titles (same normalization engine as Smart Merge) once
     * the user starts typing. Drives the one-tap chip row on the search screen.
     */
    val suggestions: StateFlow<List<String>> = combine(
        state.map { it.searchQuery.orEmpty().trim() }.distinctUntilChanged(),
        getLibraryManga.subscribe(),
    ) { query, library ->
        val recents = recentSearches.get().filterNot { it.equals(query, ignoreCase = true) }
        if (query.isBlank()) {
            recents.take(SUGGESTION_LIMIT)
        } else {
            val normalizedQuery = TitleNormalizer.forEquality(query)
            val libraryMatches = library.asSequence()
                .map { it.manga.title }
                .filter { title ->
                    val normalized = TitleNormalizer.forEquality(title)
                    normalizedQuery.length >= 2 && normalized.contains(normalizedQuery)
                }
                .take(SUGGESTION_LIMIT)
            (libraryMatches + recents.filter { it.contains(query, ignoreCase = true) })
                .distinct()
                .take(SUGGESTION_LIMIT)
                .toList()
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

    /**
     * Count of installed extensions with an update waiting, from the locally cached
     * extension list (populated by the background update checker). Reading it costs
     * no network: it exists so the Discover search page can badge "updates available"
     * without waking the network-heavy `ExtensionsViewModel` (which refreshes
     * repositories on init) just to render a chip.
     */
    val extensionUpdateCount: StateFlow<Int> = extensionUpdateManager.installedExtensionsFlow
        .map { installed -> installed.count { it.hasUpdate } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    private companion object {
        const val SUGGESTION_LIMIT = 6
    }
}
