package ephyra.feature.browse.source.globalsearch

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
    searchCache: GlobalSearchCache,
    private val recentSearches: RecentSearches,
    private val getLibraryManga: GetLibraryManga,
) : SearchViewModel(
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
    searchCache = searchCache,
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

    override fun search() {
        state.value.searchQuery?.let { recentSearches.record(it) }
        super.search()
    }

    private var isInitialized = false

    fun init(initialQuery: String = "", initialExtensionFilter: String? = null) {
        if (isInitialized) return
        isInitialized = true

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
