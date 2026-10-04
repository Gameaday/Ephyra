package ephyra.feature.browse.source.globalsearch

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.source.api.NativeSourceRegistry
import ephyra.source.api.SourceResult
import ephyra.source.api.SourceSearchRequest
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Unified singular content search: one query fanned out over BOTH worlds of sources.
 *
 *  - Extension `CatalogueSource`s via the inherited [SearchViewModel] machinery
 *    ([ephyra.domain.manga.interactor.UnifiedSearchEngine.searchSource]), which also keeps
 *    local sources and the [GlobalSearchCache] warm-cache behavior intact.
 *  - Target-native gateways ([NativeSourceRegistry]: Jellyfin today, OPDS once configured),
 *    keyed by stable source-id strings rather than legacy numeric ids.
 *
 * It extends [GlobalSearchViewModel] rather than duplicating it so the recent-searches /
 * suggestions UX and the merged-results default are inherited, and migration search
 * (a plain [SearchViewModel] subclass) is untouched.
 *
 * Native results cannot collapse into `SearchItemResult.Success(List<Manga>)` — protocol
 * `SourceContentItem`s have no legacy persistence id, so navigating them to a
 * `MangaDetails` route would be broken by construction. They therefore live in the parallel
 * [nativeItems] map until a native details route exists.
 */
@HiltViewModel
class UnifiedSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sourcePreferences: SourcePreferences,
    sourceManager: SourceManager,
    extensionManager: ExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
    searchCache: GlobalSearchCache,
    recentSearches: RecentSearches,
    getLibraryManga: GetLibraryManga,
    unifiedSearchEngine: ephyra.domain.manga.interactor.UnifiedSearchEngine,
    private val nativeSearchCache: NativeSearchCache,
    nativeSourceRegistry: NativeSourceRegistry,
) : GlobalSearchViewModel(
    savedStateHandle = savedStateHandle,
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
    searchCache = searchCache,
    recentSearches = recentSearches,
    getLibraryManga = getLibraryManga,
    unifiedSearchEngine = unifiedSearchEngine,
) {

    private val mutableNativeItems = MutableStateFlow<PersistentMap<String, NativeSourceResult>>(persistentMapOf())

    /** Native-gateway results keyed by [ephyra.source.api.SourceId] string. */
    val nativeItems: StateFlow<PersistentMap<String, NativeSourceResult>> = mutableNativeItems.asStateFlow()

    /** Stable display-name lookup for the native rows in the results list. */
    val nativeSourceNames: Map<String, String> = nativeSourceRegistry.descriptors
        .associate { it.id.value to it.displayName }

    private val gateways = nativeSourceRegistry.gateways
    private var nativeSearchJob: Job? = null

    override fun search() {
        // Extension fan-out (with recents recording and init gating) first…
        super.search()
        // …then the native fan-out for the same query.
        searchNativeSources()
    }

    private fun searchNativeSources() {
        val query = state.value.searchQuery
        if (query.isNullOrBlank() || gateways.isEmpty()) {
            nativeSearchJob?.cancel()
            mutableNativeItems.value = persistentMapOf()
            return
        }

        nativeSearchJob?.cancel()

        // Same serve-from-cache policy as the extension path: a previously fetched query
        // repopulates instantly; only uncached sources go back to Loading.
        val cached = nativeSearchCache.get(query)
        val initial: Map<String, NativeSourceResult> = gateways.associate { gateway ->
            val id = gateway.descriptor.id.value
            id to (cached?.get(id) ?: NativeSourceResult.Loading)
        }
        mutableNativeItems.value = initial.toPersistentMap()
        if (initial.values.none { it is NativeSourceResult.Loading }) return

        nativeSearchJob = viewModelScope.launchIO {
            gateways.map { gateway ->
                async {
                    val id = gateway.descriptor.id.value
                    if (mutableNativeItems.value[id] !is NativeSourceResult.Loading) return@async
                    val result = try {
                        when (val outcome = gateway.search(SourceSearchRequest(query))) {
                            is SourceResult.Success -> NativeSourceResult.Success(outcome.value.items)
                            SourceResult.Empty -> NativeSourceResult.Success(emptyList())
                            is SourceResult.Unsupported ->
                                NativeSourceResult.Error("Search not supported by this source")
                            is SourceResult.TransientFailure -> NativeSourceResult.Error(outcome.message)
                            is SourceResult.PermanentFailure -> NativeSourceResult.Error(outcome.message)
                            is SourceResult.RateLimited -> NativeSourceResult.Error("Rate limited, try again later")
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        NativeSourceResult.Error(failure.message)
                    }
                    updateNativeItem(id, result)
                }
            }.awaitAll()
        }
    }

    private fun updateNativeItem(id: String, result: NativeSourceResult) {
        mutableNativeItems.update { current -> current.put(id, result) }
        // Keep the native cache warm as results stream in, mirroring the extension path.
        state.value.searchQuery?.let { query -> nativeSearchCache.put(query, mutableNativeItems.value) }
    }
}
