package ephyra.source.api

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * Adapter implementing [ContentCatalogueSource] and wrapping a legacy Tachiyomi [Source] or [CatalogueSource].
 * Bridges old manga scraping implementations dynamically to our generic interfaces.
 */
class ContentSourceAdapter(
    val delegate: Source,
) : ContentCatalogueSource {

    override val id: Long = delegate.id
    override val name: String = delegate.name
    override val supportedTypes: Set<ContentType> = setOf(ContentType.MANGA)

    override val lang: String = delegate.lang
    override val supportsLatest: Boolean = (delegate as? CatalogueSource)?.supportsLatest ?: false

    override suspend fun getContentDetails(item: ContentItem): ContentItem {
        val sManga = SManga.create().apply {
            url = item.url
            title = item.title
            thumbnail_url = item.thumbnailUrl
        }
        val details = delegate.getMangaDetails(sManga)
        return ContentItem(
            id = item.id,
            sourceId = id,
            url = details.url ?: item.url,
            title = details.title ?: item.title,
            author = details.author,
            artist = details.artist,
            description = details.description,
            genres = details.genre?.split(",")?.map { it.trim() } ?: item.genres,
            status = ContentStatus.fromLegacyInt(details.status.toLong()),
            thumbnailUrl = details.thumbnail_url ?: item.thumbnailUrl,
            contentType = ContentType.MANGA,
            favorite = item.favorite,
            dateAdded = item.dateAdded,
            lastUpdate = item.lastUpdate,
            initialized = details.initialized,
        )
    }

    override suspend fun getContentUnits(item: ContentItem): List<ContentUnit> {
        val sManga = SManga.create().apply {
            url = item.url
            title = item.title
        }
        val chapters = delegate.getChapterList(sManga)
        return chapters.map { chapter ->
            ContentUnit(
                id = -1L,
                contentItemId = item.id,
                url = chapter.url,
                title = chapter.name,
                number = chapter.chapter_number.toDouble(),
                dateUpload = chapter.date_upload,
                progress = 0L,
                totalLength = 0L,
                lastRead = 0L,
                read = false,
                bookmark = false,
                scanlator = chapter.scanlator,
            )
        }
    }

    override suspend fun getPageList(unit: ContentUnit): List<String> {
        val chapter = SChapter.create().apply {
            url = unit.url
            name = unit.title
            chapter_number = unit.number.toFloat()
            scanlator = unit.scanlator
        }
        val pages = delegate.getPageList(chapter)
        return pages.mapNotNull { it.imageUrl }
    }

    override suspend fun getPopularContent(page: Int, type: ContentType): List<ContentItem> {
        if (type != ContentType.MANGA || delegate !is CatalogueSource) return emptyList()
        return delegate.getPopularManga(page).mangas.map { it.toContentItem(id) }
    }

    override suspend fun getSearchContent(page: Int, query: String, type: ContentType): List<ContentItem> {
        if (type != ContentType.MANGA || delegate !is CatalogueSource) return emptyList()
        return delegate.getSearchManga(page, query, FilterList()).mangas.map { it.toContentItem(id) }
    }

    override suspend fun getLatestContent(page: Int, type: ContentType): List<ContentItem> {
        if (type != ContentType.MANGA || delegate !is CatalogueSource) return emptyList()
        return delegate.getLatestUpdates(page).mangas.map { it.toContentItem(id) }
    }
}

private fun SManga.toContentItem(sourceId: Long): ContentItem {
    return ContentItem(
        id = -1L,
        sourceId = sourceId,
        url = url,
        title = title,
        author = author,
        artist = artist,
        description = description,
        genres = genre?.split(",")?.map { it.trim() } ?: emptyList(),
        status = ContentStatus.fromLegacyInt(status.toLong()),
        thumbnailUrl = thumbnail_url,
        contentType = ContentType.MANGA,
        initialized = initialized,
    )
}
