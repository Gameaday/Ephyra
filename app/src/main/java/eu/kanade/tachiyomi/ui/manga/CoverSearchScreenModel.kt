package eu.kanade.tachiyomi.ui.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Screen model for searching cover images across sources.
 * Uses limited parallelism to be respectful of source server resources.
 */
class CoverSearchScreenModel(
    private val mangaTitle: String,
    private val currentSourceId: Long,
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<CoverSearchScreenModel.State>(State()) {

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(3)
    private var searchJob: Job? = null

    /**
     * Search across all enabled catalogue sources for covers matching the manga title.
     * Only fetches the first page and extracts thumbnail URLs to minimize network calls.
     */
    fun search() {
        val query = mangaTitle
        if (query.isBlank()) return

        searchJob?.cancel()

        val sources = sourceManager.getCatalogueSources()
            .filterIsInstance<HttpSource>()
            .sortedBy { if (it.id == currentSourceId) 0 else 1 }

        mutableState.update {
            it.copy(
                isLoading = true,
                results = emptyList(),
                total = sources.size,
                progress = 0,
            )
        }

        searchJob = screenModelScope.launch {
            sources.map { source ->
                async {
                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, query, source.getFilterList())
                        }

                        val covers = page.mangas
                            .mapNotNull { manga ->
                                manga.thumbnail_url?.let { url ->
                                    CoverResult(
                                        thumbnailUrl = url,
                                        sourceName = source.name,
                                        sourceId = source.id,
                                        mangaTitle = manga.title,
                                    )
                                }
                            }
                            .take(3) // Limit to 3 covers per source to reduce resource usage

                        if (isActive && covers.isNotEmpty()) {
                            mutableState.update { state ->
                                state.copy(
                                    results = state.results + covers,
                                    progress = state.progress + 1,
                                )
                            }
                        } else if (isActive) {
                            mutableState.update { state ->
                                state.copy(progress = state.progress + 1)
                            }
                        }
                    } catch (_: Exception) {
                        if (isActive) {
                            mutableState.update { state ->
                                state.copy(progress = state.progress + 1)
                            }
                        }
                    }
                }
            }.awaitAll()

            mutableState.update { it.copy(isLoading = false) }
        }
    }

    override fun onDispose() {
        super.onDispose()
        searchJob?.cancel()
    }

    @Immutable
    data class State(
        val isLoading: Boolean = false,
        val results: List<CoverResult> = emptyList(),
        val progress: Int = 0,
        val total: Int = 0,
    )
}

@Immutable
data class CoverResult(
    val thumbnailUrl: String,
    val sourceName: String,
    val sourceId: Long,
    val mangaTitle: String,
)
