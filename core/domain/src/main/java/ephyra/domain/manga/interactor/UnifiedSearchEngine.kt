package ephyra.domain.manga.interactor

import ephyra.core.common.extension.runExtensionCall
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toDomainManga
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Search Engine & Domain Pipeline.
 *
 * Consolidates fan-out search across sources for Global Search, Browse, and Migration into a
 * single, robust domain service.
 *
 * Core capabilities:
 * 1. [searchSource]: Queries a single CatalogueSource with automatic URL deduplication,
 *    persistence via [networkToLocalManga], and SmartSourceSearchEngine fallback.
 * 2. [matchSource]: Tiered cross-source manga matching:
 *    - Tier 1: Zero-network Canonical ID local DB resolution (1.0 confidence)
 *    - Tier 2 & 3: Multi-title smart matching across primary + alternative titles
 *    - Tier 4: Deep search fallback with cleaned word tokens
 * 3. [fanOutSearchFlow]: Streams progressive search results across multiple sources with bounded concurrency.
 * 4. [fanOutMatchFlow]: Streams progressive matching results across multiple sources with bounded concurrency.
 */
@Singleton
class UnifiedSearchEngine @Inject constructor(
    private val networkToLocalManga: NetworkToLocalManga,
    private val getFavoritesByCanonicalId: GetFavoritesByCanonicalId,
) {
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(5)

    /**
     * Executes search on a single [source] with query [query], persisting results locally.
     * If the standard source search returns 0 results, attempts [SmartSourceSearchEngine.deepSearch] fallback.
     */
    suspend fun searchSource(
        source: CatalogueSource,
        query: String,
        extraSearchParams: String? = null,
        dispatcher: CoroutineDispatcher = defaultDispatcher,
    ): List<Manga> {
        val page = withContext(dispatcher) {
            runExtensionCall(sourceName = source.name) {
                source.getSearchManga(1, query, source.getFilterList())
            }
        }

        val seenUrls = HashSet<String>(page.mangas.size)
        val domainMangas = page.mangas.mapNotNullTo(ArrayList(page.mangas.size)) { smanga ->
            if (seenUrls.add(smanga.url)) smanga.toDomainManga(source.id) else null
        }
        val titles = networkToLocalManga(domainMangas)
        if (titles.isNotEmpty()) {
            return titles
        }

        // Deep search fallback: if standard search returned 0 results
        return try {
            val smartEngine = SmartSourceSearchEngine(extraSearchParams)
            val smartMatch = smartEngine.deepSearch(source, query)
            if (smartMatch != null) {
                networkToLocalManga(listOf(smartMatch))
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Executes tiered search to find an equivalent match for [manga] on [targetSource].
     *
     * Tier 1: Canonical ID resolution (0 API calls, fast-path DB lookup).
     * Tier 2/3: Multi-title search (primary title and alternative titles).
     * Tier 4: Deep search fallback with cleaned tokens (if [deepSearchMode] is true).
     */
    suspend fun matchSource(
        targetSource: CatalogueSource,
        manga: Manga,
        deepSearchMode: Boolean = true,
        extraSearchParams: String? = null,
    ): MatchedSourceResult? {
        // Tier 1: Zero-call canonical identity match
        val canonicalMatch = findByCanonicalId(manga, targetSource.id)
        if (canonicalMatch != null) {
            return MatchedSourceResult(
                manga = canonicalMatch,
                matchConfidence = 1.0,
                isCanonicalMatch = true,
            )
        }

        // Tier 2 - 4: Multi-title smart search
        val smartEngine = SmartSourceSearchEngine(extraSearchParams)
        val titleResult = smartEngine.multiTitleSearch(
            source = targetSource,
            primaryTitle = manga.title,
            alternativeTitles = manga.alternativeTitles,
            deepSearchFallback = deepSearchMode,
        )

        if (titleResult == null) return null

        val matchedDomain = titleResult.first
        // Ignore self-matches on the same source
        if (matchedDomain.url == manga.url && targetSource.id == manga.source) return null

        val localManga = networkToLocalManga(matchedDomain)
        return MatchedSourceResult(
            manga = localManga,
            matchConfidence = titleResult.second,
            isCanonicalMatch = false,
        )
    }

    /**
     * Finds a favorite manga on [targetSourceId] that shares the same [manga.canonicalId].
     */
    suspend fun findByCanonicalId(manga: Manga, targetSourceId: Long): Manga? {
        val canonicalId = manga.canonicalId ?: return null
        return try {
            getFavoritesByCanonicalId.await(canonicalId, manga.id)
                .firstOrNull { it.source == targetSourceId }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Progressive streaming fan-out search across [sources].
     * Emits each source's [FanOutSearchResult] as soon as it completes.
     */
    fun fanOutSearchFlow(
        sources: List<CatalogueSource>,
        query: String,
        concurrency: Int = 5,
        extraSearchParams: String? = null,
    ): Flow<FanOutSearchResult> = channelFlow {
        val semaphore = Semaphore(concurrency)
        sources.forEach { source ->
            async {
                semaphore.withPermit {
                    try {
                        val mangas = searchSource(source, query, extraSearchParams)
                        send(FanOutSearchResult.Success(source, mangas))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        send(FanOutSearchResult.Error(source, e))
                    }
                }
            }
        }
    }

    /**
     * Progressive streaming fan-out matching for [manga] across [sources].
     * Emits each source's [FanOutMatchResult] as soon as it completes.
     */
    fun fanOutMatchFlow(
        sources: List<CatalogueSource>,
        manga: Manga,
        deepSearchMode: Boolean = true,
        concurrency: Int = 5,
        extraSearchParams: String? = null,
    ): Flow<FanOutMatchResult> = channelFlow {
        val semaphore = Semaphore(concurrency)
        sources.forEach { source ->
            async {
                semaphore.withPermit {
                    try {
                        val match = matchSource(source, manga, deepSearchMode, extraSearchParams)
                        if (match != null) {
                            send(FanOutMatchResult.Success(source, match))
                        } else {
                            send(FanOutMatchResult.NotFound(source))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        send(FanOutMatchResult.Error(source, e))
                    }
                }
            }
        }
    }
}

data class MatchedSourceResult(
    val manga: Manga,
    val matchConfidence: Double,
    val isCanonicalMatch: Boolean,
)

sealed interface FanOutSearchResult {
    data class Success(val source: CatalogueSource, val mangas: List<Manga>) : FanOutSearchResult
    data class Error(val source: CatalogueSource, val error: Throwable) : FanOutSearchResult
}

sealed interface FanOutMatchResult {
    data class Success(val source: CatalogueSource, val result: MatchedSourceResult) : FanOutMatchResult
    data class NotFound(val source: CatalogueSource) : FanOutMatchResult
    data class Error(val source: CatalogueSource, val error: Throwable) : FanOutMatchResult
}
