package eu.kanade.tachiyomi.source.online

import ephyra.core.common.util.getOrThrow
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response

/**
 * Bridges the generic [ContentSourceOrchestrator] and dynamic [SourceProfile] models
 * into the legacy [HttpSource] / [CatalogueSource] contracts. This exposes our scraper/heuristic
 * sources transparently to all existing UI and ViewModel components in Ephyra.
 */
class DynamicHttpSource(
    val profile: SourceProfile,
    private val orchestrator: ContentSourceOrchestrator,
) : HttpSource() {

    override val baseUrl: String = profile.baseUrl
    override val name: String = profile.displayName
    override val lang: String = "en"
    override val id: Long = profile.baseUrl.hashCode().toLong()
    override val supportsLatest: Boolean = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val items = orchestrator.getPopular(baseUrl, page).getOrThrow()
        return MangasPage(
            mangas = items.map { it.toSManga() },
            hasNextPage = items.isNotEmpty(),
        )
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val items = orchestrator.search(baseUrl, query, page).getOrThrow()
        return MangasPage(
            mangas = items.map { it.toSManga() },
            hasNextPage = items.isNotEmpty(),
        )
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val items = orchestrator.getLatest(baseUrl, page).getOrThrow()
        return MangasPage(
            mangas = items.map { it.toSManga() },
            hasNextPage = items.isNotEmpty(),
        )
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val fullUrl = if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url
        val item = orchestrator.getItem(baseUrl, fullUrl).getOrThrow()
        return item.toSManga().apply { initialized = true }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val fullUrl = if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url
        val units = orchestrator.getChapters(baseUrl, fullUrl).getOrThrow()
        return units.map { unit ->
            SChapter.create().apply {
                url = if (unit.url.startsWith(baseUrl)) unit.url.removePrefix(baseUrl) else unit.url
                name = unit.title
                chapter_number = unit.number.toFloat()
                date_upload = unit.dateUpload
                scanlator = unit.scanlator
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val fullUrl = if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
        val pages = orchestrator.getPages(baseUrl, fullUrl).getOrThrow()
        return pages.mapIndexed { index, imageUrl ->
            Page(index = index, url = imageUrl, imageUrl = imageUrl)
        }
    }

    private fun ContentItem.toSManga(): SManga {
        return SManga.create().apply {
            url =
                if (this@toSManga.url.startsWith(
                        baseUrl,
                    )
                ) {
                    this@toSManga.url.removePrefix(baseUrl)
                } else {
                    this@toSManga.url
                }
            title = this@toSManga.title
            thumbnail_url = this@toSManga.thumbnailUrl
            description = this@toSManga.description
            author = this@toSManga.author
            artist = this@toSManga.artist
            status = this@toSManga.status.toLegacyInt()
            initialized = this@toSManga.initialized
        }
    }

    private fun ephyra.domain.content.model.ContentStatus.toLegacyInt(): Int {
        return when (this) {
            is ephyra.domain.content.model.ContentStatus.Ongoing -> 1
            is ephyra.domain.content.model.ContentStatus.Completed -> 2
            is ephyra.domain.content.model.ContentStatus.Licensed -> 4
            is ephyra.domain.content.model.ContentStatus.Cancelled -> 5
            is ephyra.domain.content.model.ContentStatus.Hiatus -> 6
            else -> 0
        }
    }

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException("DynamicHttpSource delegates getPopularManga directly")
    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("DynamicHttpSource delegates getPopularManga directly")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("DynamicHttpSource delegates getSearchManga directly")
    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("DynamicHttpSource delegates getSearchManga directly")
    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("DynamicHttpSource delegates getLatestUpdates directly")
    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("DynamicHttpSource delegates getLatestUpdates directly")
    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException("DynamicHttpSource delegates getMangaDetails directly")
    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException("DynamicHttpSource delegates getChapterList directly")
    override fun chapterPageParse(response: Response): SChapter =
        throw UnsupportedOperationException("DynamicHttpSource delegates getChapterList directly")
    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("DynamicHttpSource delegates getPageList directly")
    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("DynamicHttpSource delegates getImageUrl directly")
}
