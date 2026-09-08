package ephyra.feature.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ephyra.core.common.preference.toggle
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.SmartSourceSearchEngine
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toDomainManga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class SearchViewModel(
    initialState: State = State(),
    private val sourcePreferences: SourcePreferences,
    protected val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    protected val networkToLocalManga: NetworkToLocalManga,
    private val getManga: GetManga,
    private val searchCache: GlobalSearchCache,
) : BaseUdfViewModel<SearchViewModel.State, SearchScreenEvent, SearchEffect>(initialState) {

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().getSync()

    private val disabledSourceIds = sourcePreferences.disabledSources().getSync()
        .mapNotNullTo(HashSet()) { it.toLongOrNull() }
    protected val pinnedSourceIds = sourcePreferences.pinnedSources().getSync()
        .mapNotNullTo(HashSet()) { it.toLongOrNull() }

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<CatalogueSource, SearchItemResult> ->
        val sortKeys = HashMap<Long, String>((map.size / 0.75f + 1).toInt())
        map.keys.forEach { sortKeys[it.id] = "${it.name.lowercase()} (${it.lang})" }
        compareBy<CatalogueSource>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { it.id !in pinnedSourceIds },
            { sortKeys.getValue(it.id) },
        )
    }

    init {
        viewModelScope.launch {
            sourcePreferences.globalSearchFilterState().changes().collectLatest { filterState ->
                updateState { it.copy(onlyShowHasResults = filterState) }
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<CatalogueSource> {
        val filtered = sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && it.id !in disabledSourceIds }
        val sortKeys = HashMap<Long, String>((filtered.size / 0.75f + 1).toInt())
        filtered.forEach { sortKeys[it.id] = "${it.name.lowercase()} (${it.lang})" }
        return filtered.sortedWith(
            compareBy(
                { it.id !in pinnedSourceIds },
                { sortKeys.getValue(it.id) },
            ),
        )
    }

    private fun getSelectedSources(): List<CatalogueSource> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        val enabledIds = enabledSources.mapTo(HashSet(enabledSources.size)) { it.id }
        val result = mutableListOf<CatalogueSource>()
        for (ext in extensionManager.installedExtensionsFlow.value) {
            if (ext.pkgName != filter) continue
            for (source in ext.sources) {
                if (source is CatalogueSource && source.id in enabledIds) {
                    result.add(source)
                }
            }
        }
        return result
    }

    override fun onEvent(event: SearchScreenEvent) {
        when (event) {
            is SearchScreenEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            is SearchScreenEvent.SetSourceFilter -> setSourceFilter(event.filter)
            is SearchScreenEvent.ToggleFilterResults -> toggleFilterResults()
            is SearchScreenEvent.Search -> search()
            is SearchScreenEvent.SetMigrateDialog -> setMigrateDialog(event.currentId, event.target)
            is SearchScreenEvent.ClearDialog -> clearDialog()
        }
    }

    protected fun updateSearchQuery(query: String?) {
        updateState { it.copy(searchQuery = query) }
    }

    protected fun setSourceFilter(filter: SourceFilter) {
        updateState { it.copy(sourceFilter = filter) }
        search()
    }

    protected fun toggleFilterResults() {
        viewModelScope.launch { sourcePreferences.globalSearchFilterState().toggle() }
    }

    protected open fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()

        val sources = getSelectedSources()

        // Serve a previous result set for the same query instantly (back-navigation
        // case); only sources missing from the cached map go back to Loading and are
        // re-fetched.
        val cached = searchCache.get(query)
        val itemsForSources: Map<CatalogueSource, SearchItemResult> = if (cached != null) {
            sources.associateWith { source ->
                cached[source] ?: SearchItemResult.Loading
            }
        } else {
            sources.associateWith { SearchItemResult.Loading }
        }

        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: itemsForSources[it] ?: SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(itemsForSources.toPersistentMap())
        }

        val needsNetwork = itemsForSources.values.any { it is SearchItemResult.Loading }
        if (!needsNetwork) return

        searchJob = viewModelScope.launchIO {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is SearchItemResult.Loading) {
                        return@async
                    }

                    try {
                        val titles = searchSource(source, query)

                        if (isActive) {
                            updateItem(source, SearchItemResult.Success(titles))
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (isActive) {
                            updateItem(source, SearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    protected open suspend fun searchSource(
        source: CatalogueSource,
        query: String,
    ): List<Manga> {
        val page = withContext(coroutineDispatcher) {
            source.getSearchManga(1, query, source.getFilterList())
        }

        val seenUrls = HashSet<String>(page.mangas.size)
        val domainMangas = page.mangas.mapNotNullTo(ArrayList(page.mangas.size)) { smanga ->
            if (seenUrls.add(smanga.url)) smanga.toDomainManga(source.id) else null
        }
        val titles = networkToLocalManga(domainMangas)
        if (titles.isNotEmpty()) {
            return titles
        }

        // Library search fallback: If standard source search returned 0 results, try SmartSourceSearchEngine deep search
        return try {
            val smartEngine = SmartSourceSearchEngine(extraSearchParams = null)
            val smartMatch = smartEngine.deepSearch(source, query)
            if (smartMatch != null) {
                networkToLocalManga(listOf(smartMatch))
            } else {
                emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun updateItems(items: PersistentMap<CatalogueSource, SearchItemResult>) {
        updateState {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: CatalogueSource, result: SearchItemResult) {
        val newItems = state.value.items.mutate {
            it[source] = result
        }
        updateItems(newItems)
        // Keep the cache warm as results stream in so re-entering the screen is instant.
        state.value.searchQuery?.let { query -> searchCache.put(query, newItems) }
    }

    protected fun setMigrateDialog(currentId: Long, target: Manga) {
        viewModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            updateState { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    protected fun clearDialog() {
        updateState { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<CatalogueSource, SearchItemResult> = persistentMapOf(),
        val dialog: Dialog? = null,
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = if (!onlyShowHasResults) {
            items
        } else {
            items.filter { (_, result) ->
                result.isVisible(onlyShowHasResults)
            }
        }

        /**
         * Cross-source Smart-Merge view of all successful results: duplicates from
         * different catalogues collapsed into one entry with a source-count badge.
         */
        val mergedResults: List<MergedSearchResult> by lazy {
            SearchResultMerger.merge(
                items.values.filterIsInstance<SearchItemResult.Success>().flatMap { it.result },
            )
        }
    }

    @Immutable
    sealed interface Dialog {
        @Immutable
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

enum class SourceFilter {
    All,
    PinnedOnly,
}

@Immutable
sealed interface SearchItemResult {
    @Immutable
    data object Loading : SearchItemResult

    @Immutable
    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    @Immutable
    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
