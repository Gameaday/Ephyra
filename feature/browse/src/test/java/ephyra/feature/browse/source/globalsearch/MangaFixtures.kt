package ephyra.feature.browse.source.globalsearch

import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.UpdateStrategy

/**
 * Minimal [Manga] factory for unit tests, built on the production [Manga.create]
 * factory so fixture defaults stay in sync with the real model.
 */
internal fun manga(
    id: Long = 0,
    source: Long = 0,
    title: String = "Title",
    author: String? = null,
    url: String = "/$id",
): Manga = Manga.create().copy(
    id = id,
    source = source,
    title = title,
    author = author,
    url = url,
)

/**
 * Scriptable [CatalogueSource] for unit tests: returns canned results or throws,
 * recording the last query for assertions. Pure JVM, no Android dependencies.
 */
internal class FakeCatalogueSource(
    override val id: Long,
    override val name: String,
    override val lang: String = "en",
    private val result: Result<MangasPage> = Result.success(MangasPage(emptyList(), false)),
) : CatalogueSource {

    var lastSearchQuery: String? = null
        private set

    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage = result.getOrThrow()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        lastSearchQuery = query
        return result.getOrThrow()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = result.getOrThrow()

    override fun getFilterList(): FilterList = FilterList(emptyList())

    override suspend fun getMangaDetails(manga: SManga): SManga = manga

    override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}
