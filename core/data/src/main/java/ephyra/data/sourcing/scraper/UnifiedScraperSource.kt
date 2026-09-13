package ephyra.data.sourcing.scraper

import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.UnifiedContentSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * Pillar 3: Quarantined Scraper Runtime.
 *
 * Implements [UnifiedContentSource] by delegating to a quarantined Tachiyomi [Source],
 * converting scraper results to canonical [ContentPage.ImagePage] at the module boundary
 * without leaking legacy classes (SManga, SChapter, Page, FilterList) into higher layers.
 */
class UnifiedScraperSource(
    val delegate: Source,
) : UnifiedContentSource {

    override val id: String = delegate.id.toString()
    override val name: String = delegate.name
    override val supportedTypes: Set<ContentType> = setOf(ContentType.MANGA)

    override suspend fun getCatalog(page: Int, filter: FilterSet): List<CatalogEntry> {
        if (delegate !is CatalogueSource) return emptyList()
        val mangasPage = if (filter.query.isNotBlank()) {
            delegate.getSearchManga(page, filter.query, FilterList())
        } else {
            when (filter.sortOrder) {
                FilterSet.SortOrder.LATEST -> if (delegate.supportsLatest) {
                    delegate.getLatestUpdates(page)
                } else {
                    delegate.getPopularManga(page)
                }
                else -> delegate.getPopularManga(page)
            }
        }

        return mangasPage.mangas.map { manga ->
            CatalogEntry(
                key = manga.url,
                title = manga.title,
                url = manga.url,
                coverUrl = manga.thumbnail_url,
                type = ContentType.MANGA,
                author = manga.author,
                genres = manga.genre?.split(",")?.map { it.trim() } ?: emptyList(),
            )
        }
    }

    override suspend fun getChapterManifest(entryKey: String): List<ChapterInfo> {
        val sManga = SManga.create().apply { url = entryKey }
        val chapters = delegate.getChapterList(sManga)
        return chapters.map { chapter ->
            ChapterInfo(
                key = chapter.url,
                title = chapter.name,
                number = chapter.chapter_number.toDouble(),
                dateUpload = chapter.date_upload,
                scanlator = chapter.scanlator,
                url = chapter.url,
            )
        }
    }

    override suspend fun loadPage(chapterKey: String, pageIndex: Int): ContentPage {
        val pages = loadPages(chapterKey)
        return pages.getOrNull(pageIndex) ?: ContentPage.ImagePage(index = pageIndex)
    }

    override suspend fun loadPages(chapterKey: String): List<ContentPage> {
        val chapter = SChapter.create().apply { url = chapterKey }
        val pages = delegate.getPageList(chapter)
        return pages.mapIndexed { index, page ->
            ContentPage.ImagePage(
                index = index,
                imageUrl = page.imageUrl ?: page.url,
            )
        }
    }
}

/**
 * Extension to wrap any quarantined legacy [Source] into a canonical [UnifiedContentSource].
 */
fun Source.toUnifiedContentSource(): UnifiedContentSource = UnifiedScraperSource(this)
