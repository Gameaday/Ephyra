package ephyra.data.updates

import ephyra.core.common.di.IoDispatcher
import ephyra.data.room.daos.UpdateDao
import ephyra.domain.updates.model.UpdatesWithRelations
import ephyra.domain.updates.repository.UpdatesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdatesRepositoryImpl @Inject constructor(
    private val updateDao: UpdateDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdatesRepository {

    override suspend fun awaitWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): List<UpdatesWithRelations> = withContext(ioDispatcher) {
        updateDao.getUpdatesByReadStatusBlocking(read, after, limit)
            .map(UpdatesMapper::mapUpdatesWithRelations)
    }

    override fun subscribeAll(
        after: Long,
        limit: Long,
        unread: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean,
    ): Flow<List<UpdatesWithRelations>> {
        return updateDao.getRecentUpdatesWithFilters(
            after = after,
            limit = limit,
            read = unread?.let { !it },
            bookmarked = bookmarked,
            hideExcludedScanlators = if (hideExcludedScanlators) 1 else 0,
        )
            .map { list -> list.map(UpdatesMapper::mapUpdatesWithRelations) }
            .flowOn(ioDispatcher)
    }

    override fun subscribeWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): Flow<List<UpdatesWithRelations>> {
        return updateDao.getUpdatesByReadStatus(read, after, limit)
            .map { list -> list.map(UpdatesMapper::mapUpdatesWithRelations) }
            .flowOn(ioDispatcher)
    }
}
