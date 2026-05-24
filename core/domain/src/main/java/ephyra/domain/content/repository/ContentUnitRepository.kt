package ephyra.domain.content.repository

import ephyra.domain.content.model.ContentUnit
import kotlinx.coroutines.flow.Flow

/**
 * Generic content unit repository interface.
 * Accesses chapters/episodes/sections in a media-agnostic way.
 */
interface ContentUnitRepository {

    suspend fun getUnitsByContentItemId(contentItemId: Long): List<ContentUnit>

    suspend fun getUnitsByContentItemIdAsFlow(contentItemId: Long): Flow<List<ContentUnit>>

    suspend fun update(unit: ContentUnit): Boolean

    suspend fun updateAll(units: List<ContentUnit>): Boolean
}
