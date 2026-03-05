package mihon.feature.migration.list.search

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.model.Manga

class SmartSourceSearchEngine(extraSearchParams: String?) : BaseSmartSearchEngine<SManga>(extraSearchParams) {

    override fun getTitle(result: SManga) = result.title

    suspend fun regularSearch(source: CatalogueSource, title: String): Manga? {
        return regularSearch(makeSearchAction(source), title).let {
            it?.toDomainManga(source.id)
        }
    }

    suspend fun deepSearch(source: CatalogueSource, title: String): Manga? {
        return deepSearch(makeSearchAction(source), title).let {
            it?.toDomainManga(source.id)
        }
    }

    /**
     * Enhanced search that tries multiple known titles for a manga.
     * Attempts exact match with primary title first, then tries each alternative
     * title, and falls back to deep search. This dramatically improves matching
     * for manga that use different titles across sources (e.g. romaji vs english).
     */
    suspend fun multiTitleSearch(
        source: CatalogueSource,
        primaryTitle: String,
        alternativeTitles: List<String> = emptyList(),
    ): Manga? {
        return multiTitleSearch(makeSearchAction(source), primaryTitle, alternativeTitles)?.let {
            it.toDomainManga(source.id)
        }
    }

    private fun makeSearchAction(source: CatalogueSource): SearchAction<SManga> = { query ->
        source.getSearchManga(1, query, source.getFilterList()).mangas
    }
}
