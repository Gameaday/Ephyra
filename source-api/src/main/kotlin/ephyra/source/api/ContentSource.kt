package ephyra.source.api

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit

/**
 * Base interface for any media source (Manga, Anime, Books, Audiobook sources).
 * Decouples sourcing from Tachiyomi/manga-specific structures completely.
 */
interface ContentSource {

    /**
     * Unique identifier for the source.
     */
    val id: Long

    /**
     * Display name of the source.
     */
    val name: String

    /**
     * The set of content types natively supported by this source (e.g. MANGA, ANIME, BOOK).
     */
    val supportedTypes: Set<ContentType>

    /**
     * Obtains full, fresh details for a content item.
     */
    suspend fun getContentDetails(item: ContentItem): ContentItem

    /**
     * Resolves the list of chronological/sequential units (chapters/episodes/tracks).
     */
    suspend fun getContentUnits(item: ContentItem): List<ContentUnit>

    /**
     * Fetches the page list or stream segments for a unit.
     * Returns:
     * - Image URLs for Manga
     * - Video stream URLs for Anime
     * - EPUB/Text resource paths for Books
     */
    suspend fun getPageList(unit: ContentUnit): List<String>
}
