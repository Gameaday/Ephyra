package ephyra.domain.content.repository

import ephyra.core.common.util.Result
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentUnit
import kotlinx.coroutines.flow.Flow

/**
 * Clean local database contract abstracting DAO and Room details behind generic models.
 * Operates strictly with [Result] wrappers to ensure structured, safe operations.
 */
interface ContentDatabase {
    suspend fun insertItem(item: ContentItem): Result<Long>
    suspend fun getItemById(id: Long): Result<ContentItem?>
    fun getItemByIdAsFlow(id: Long): Flow<Result<ContentItem?>>
    suspend fun getFavorites(): Result<List<ContentItem>>
    suspend fun updateItem(item: ContentItem): Result<Boolean>
    suspend fun deleteItem(id: Long): Result<Boolean>

    suspend fun insertUnit(unit: ContentUnit): Result<Long>
    suspend fun getUnitsByItemId(itemId: Long): Result<List<ContentUnit>>
    fun getUnitsByItemIdAsFlow(itemId: Long): Flow<Result<List<ContentUnit>>>
    suspend fun updateUnit(unit: ContentUnit): Result<Boolean>
}
