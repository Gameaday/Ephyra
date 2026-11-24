package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toDomainManga
import ephyra.source.api.SourceContentItem
import ephyra.source.api.SourceGateway
import ephyra.source.api.SourceResult
import ephyra.source.api.SourceSearchRequest

class SmartSourceSearchEngine(extraSearchParams: String?) : BaseSmartSearchEngine<SourceContentItem>(
    extraSearchParams,
) {

    override fun getTitle(result: SourceContentItem) = result.title

    override fun getAlternativeTitles(result: SourceContentItem): List<String> {
        return result.metadata["altTitles"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun regularSearch(gateway: SourceGateway, title: String): Manga? {
        return regularSearch(makeSearchAction(gateway), title)?.toDomainManga()
    }

    suspend fun deepSearch(gateway: SourceGateway, title: String): Manga? {
        return deepSearch(makeSearchAction(gateway), title)?.toDomainManga()
    }

    /**
     * Enhanced search that tries multiple known titles for a manga.
     * Implements tiered fallback: primary title → alt titles → near-match → deep search.
     * Returns a pair of (Manga, matchConfidence) where confidence is 0.0-1.0.
     *
     * @param deepSearchFallback If true, falls back to deep search when no title matches.
     *   Set to false to limit to title-based matching only (fewer API calls).
     */
    suspend fun multiTitleSearch(
        gateway: SourceGateway,
        primaryTitle: String,
        alternativeTitles: List<String> = emptyList(),
        deepSearchFallback: Boolean = true,
    ): Pair<Manga, Double>? {
        return multiTitleSearch(makeSearchAction(gateway), primaryTitle, alternativeTitles, deepSearchFallback)?.let {
            it.entry.toDomainManga() to it.distance
        }
    }

    private fun makeSearchAction(gateway: SourceGateway): SearchAction<SourceContentItem> = { query ->
        val request = SourceSearchRequest(query = query, cursor = "1", filters = emptyMap())
        val result = gateway.search(request)
        when (result) {
            is SourceResult.Success -> result.value.items
            is SourceResult.Empty -> emptyList()
            else -> throw RuntimeException("Search failed: $result")
        }
    }
}
