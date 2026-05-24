package ephyra.source.api

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType

/**
 * Orchestrates content retrieval and search actions across all registered [ContentSource]s.
 */
interface ContentSourceEngine {

    /**
     * Resolves a source by its unique ID.
     */
    fun getSource(id: Long): ContentSource?

    /**
     * Obtains the list of all registered sources.
     */
    fun getAllSources(): List<ContentSource>

    /**
     * Queries all active sources supporting the specified content type.
     */
    suspend fun search(query: String, page: Int, type: ContentType): List<ContentItem>
}
