package ephyra.domain.content.repository

import ephyra.domain.content.model.ContentItem
import kotlinx.coroutines.flow.Flow

/**
 * Generic content repository interface.
 * Exposes core library/history actions in a completely media-agnostic way.
 */
interface ContentRepository {

    suspend fun getContentItemById(id: Long): ContentItem?

    suspend fun getContentItemByIdAsFlow(id: Long): Flow<ContentItem?>

    suspend fun getFavorites(): List<ContentItem>

    suspend fun update(contentItem: ContentItem): Boolean
}
