package ephyra.feature.browse.migration.search

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.SmartSourceSearchEngine
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.feature.browse.source.globalsearch.GlobalSearchCache
import ephyra.feature.browse.source.globalsearch.SearchItemResult
import ephyra.feature.browse.source.globalsearch.SearchViewModel
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MigrateSearchViewModel @Inject constructor(
    private val getManga: GetManga,
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    searchCache: GlobalSearchCache,
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

    private val migrationSources by lazy { sourcePreferences.migrationSources().getSync() }
    private val migrationSourceRank by lazy { migrationSources.withIndex().associate { (i, id) -> id to i } }

    override val sortComparator = { map: Map<CatalogueSource, SearchItemResult> ->
        compareBy<CatalogueSource>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { migrationSourceRank.getOrDefault(it.id, Int.MAX_VALUE) },
        )
    }

    private var isInitialized = false

    fun init(mangaId: Long) {
        if (isInitialized) return
        isInitialized = true
        viewModelScope.launch {
            val manga = getManga.await(mangaId)!!
            updateState {
                it.copy(
                    from = manga,
                    searchQuery = manga.title,
                )
            }
            search()
        }
    }

    override suspend fun searchSource(source: CatalogueSource, query: String): List<Manga> {
        val standardResults = super.searchSource(source, query)
        if (standardResults.isNotEmpty()) return standardResults

        val fromManga = state.value.from ?: return emptyList()
        val match = unifiedSearchEngine.matchSource(
            targetSource = source,
            manga = fromManga,
            deepSearchMode = true,
        )
        return if (match != null) listOf(match.manga) else emptyList()
    }

    override fun getEnabledSources(): List<CatalogueSource> {
        return migrationSources.mapNotNull { sourceManager.get(it) as? CatalogueSource }
    }
}
