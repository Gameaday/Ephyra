package ephyra.domain.content.source

import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet

/**
 * Unified canonical interface for all content sources across the three pillars:
 * 1. Local Storage (SAF Streaming Archives: CBZ/CBR/ZIP/EPUB)
 * 2. Remote APIs (OPDS 1.2/2.0, Jellyfin, Kavita, Calibre)
 * 3. Quarantined Scraper Runtime (In-App Sandboxed DEX Runner)
 */
interface UnifiedContentSource {
    val id: String
    val name: String
    val supportedTypes: Set<ContentType>

    suspend fun getCatalog(page: Int, filter: FilterSet): List<CatalogEntry>
    suspend fun getChapterManifest(entryKey: String): List<ChapterInfo>
    suspend fun loadPage(chapterKey: String, pageIndex: Int): ContentPage
    suspend fun loadPages(chapterKey: String): List<ContentPage> = emptyList()
}
