package ephyra.feature.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import ephyra.core.common.util.system.logcat
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority

@HiltViewModel
class CoverSearchScreenModel @Inject constructor(
    private val sourceManager: SourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(3)
    private var searchJob: Job? = null

    private var mangaTitle: String = ""
    private var currentSourceId: Long = -1L
    private var isInitialized = false

    fun init(mangaTitle: String, currentSourceId: Long) {
        if (isInitialized) return
        isInitialized = true
        this.mangaTitle = mangaTitle
        this.currentSourceId = currentSourceId
    }

    fun onEvent(event: CoverSearchScreenEvent) {
        when (event) {
            CoverSearchScreenEvent.Search -> search()
            CoverSearchScreenEvent.Refresh -> refresh()
        }
    }

    fun search() {
        val query = mangaTitle
        if (query.isBlank()) return

        // Return cached results if available
        val cached = coverResultsCache[query]
        if (cached != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    results = cached,
                    fromCache = true,
                )
            }
            return
        }

        fetchFromSources(query)
    }

    fun refresh() {
        val query = mangaTitle
        if (query.isBlank()) return
        coverResultsCache.remove(query)
        fetchFromSources(query)
    }

    private fun deduplicateResults(results: List<CoverResult>): List<CoverResult> {
        val seen = linkedMapOf<String, CoverResult>()
        for (result in results) {
            val existing = seen[result.thumbnailUrl]
            if (existing != null) {
                if (result.sourceName !in existing.additionalSourceNames &&
                    result.sourceName != existing.sourceName
                ) {
                    seen[result.thumbnailUrl] = existing.copy(
                        additionalSourceNames = existing.additionalSourceNames + result.sourceName,
                    )
                }
            } else {
                seen[result.thumbnailUrl] = result
            }
        }
        return seen.values.toList()
    }

    private fun fetchFromSources(query: String) {
        searchJob?.cancel()

        val sources = sourceManager.getCatalogueSources()
            .filterIsInstance<HttpSource>()
            .sortedBy { if (it.id == currentSourceId) 0 else 1 }

        _state.update {
            it.copy(
                isLoading = true,
                results = emptyList(),
                total = sources.size,
                progress = 0,
                fromCache = false,
            )
        }

        searchJob = viewModelScope.launch {
            sources.map { source ->
                async(coroutineDispatcher) {
                    try {
                        val page = source.getSearchManga(1, query, source.getFilterList())

                        val covers = page.mangas
                            .mapNotNull { manga ->
                                manga.thumbnail_url?.let { url ->
                                    CoverResult(
                                        thumbnailUrl = url,
                                        sourceName = source.name,
                                        sourceId = source.id,
                                        mangaTitle = manga.title,
                                        mangaUrl = manga.url,
                                    )
                                }
                            }

                        val bestMatch = covers.firstOrNull()
                        val seriesCovers = if (bestMatch != null) {
                            val normalizedTitle = bestMatch.mangaTitle.lowercase().trim()
                            covers.filter {
                                it.mangaTitle.lowercase().trim() == normalizedTitle
                            }.distinctBy { it.thumbnailUrl }
                        } else {
                            emptyList()
                        }

                        if (isActive && seriesCovers.isNotEmpty()) {
                            _state.update { state ->
                                state.copy(
                                    results = deduplicateResults(state.results + seriesCovers),
                                    progress = state.progress + 1,
                                )
                            }
                        } else if (isActive) {
                            _state.update { state ->
                                state.copy(progress = state.progress + 1)
                            }
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "Cover search failed for source '${source.name}'; skipping" }
                        if (isActive) {
                            _state.update { state ->
                                state.copy(progress = state.progress + 1)
                            }
                        }
                    }
                }
            }.awaitAll()

            // Cache results for future use
            val results = state.value.results
            if (results.isNotEmpty()) {
                coverResultsCache[query] = results
            }

            _state.update { it.copy(isLoading = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }

    @Immutable
    data class State(
        val isLoading: Boolean = false,
        val results: List<CoverResult> = emptyList(),
        val progress: Int = 0,
        val total: Int = 0,
        val fromCache: Boolean = false,
    )

    companion object {
        private const val MAX_CACHE_ENTRIES = 20

        private val coverResultsCache = object : LinkedHashMap<String, List<CoverResult>>(
            MAX_CACHE_ENTRIES + 1,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<CoverResult>>): Boolean {
                return size > MAX_CACHE_ENTRIES
            }
        }
    }
}

@Immutable
data class CoverResult(
    val thumbnailUrl: String,
    val sourceName: String,
    val sourceId: Long,
    val mangaTitle: String,
    val mangaUrl: String,
    val additionalSourceNames: List<String> = emptyList(),
) {
    val sourceCount: Int get() = 1 + additionalSourceNames.size
}
