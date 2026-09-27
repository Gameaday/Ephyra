package ephyra.domain.updates.repository

import ephyra.domain.updates.model.UpdatesWithRelations
import kotlinx.coroutines.flow.Flow

interface UpdatesRepository {

    suspend fun awaitWithRead(read: Boolean, after: Long, limit: Long): List<UpdatesWithRelations>

    fun subscribeAll(
        after: Long,
        limit: Long,
        unread: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean = false,
        // DEF-006: the safe value is the default here too. A repository implementation reached
        // through some other path would otherwise inherit the permissive reading of the
        // parameter, which is how the default becomes a policy by accident.
        libraryOnly: Boolean = true,
    ): Flow<List<UpdatesWithRelations>>

    fun subscribeWithRead(read: Boolean, after: Long, limit: Long): Flow<List<UpdatesWithRelations>>
}
