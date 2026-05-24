package ephyra.source.api

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType

/**
 * Interface for catalog-based sources supporting popular, latest, and query-based search operations.
 */
interface ContentCatalogueSource : ContentSource {

    /**
     * Language code (e.g. "en", "ja") for the catalog.
     */
    val lang: String

    /**
     * Whether the source supports fetching recently updated content list.
     */
    val supportsLatest: Boolean

    /**
     * Fetches a paginated list of popular items of the specified content type.
     */
    suspend fun getPopularContent(page: Int, type: ContentType): List<ContentItem>

    /**
     * Searches the catalog for items matching a query of the specified content type.
     */
    suspend fun getSearchContent(page: Int, query: String, type: ContentType): List<ContentItem>

    /**
     * Fetches a paginated list of recently updated items of the specified content type.
     */
    suspend fun getLatestContent(page: Int, type: ContentType): List<ContentItem>
}
