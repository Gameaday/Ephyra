package ephyra.data.history

import ephyra.core.common.di.IoDispatcher
import ephyra.data.room.daos.HistoryDao
import ephyra.data.room.entities.HistoryEntity
import ephyra.domain.history.model.History
import ephyra.domain.history.model.HistoryUpdate
import ephyra.domain.history.model.HistoryWithRelations
import ephyra.domain.history.repository.HistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HistoryRepository {

    override fun getHistory(query: String): Flow<List<HistoryWithRelations>> {
        return historyDao.getHistory(query)
            .map { list -> list.map(HistoryMapper::mapHistoryWithRelations) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getLastHistory(): HistoryWithRelations? = withContext(ioDispatcher) {
        historyDao.getLatestHistory()?.let(HistoryMapper::mapHistoryWithRelations)
    }

    override suspend fun getTotalReadDuration(): Long = withContext(ioDispatcher) {
        historyDao.getTotalReadDuration()
    }

    override suspend fun getHistoryByMangaId(mangaId: Long): List<History> = withContext(ioDispatcher) {
        historyDao.getHistoryByMangaId(mangaId).map(HistoryMapper::mapHistory)
    }

    override suspend fun resetHistory(historyId: Long) = withContext(ioDispatcher) {
        historyDao.resetHistory(historyId)
    }

    override suspend fun resetHistoryByMangaId(mangaId: Long) = withContext(ioDispatcher) {
        historyDao.resetHistoryByMangaId(mangaId)
    }

    override suspend fun deleteAllHistory(): Boolean = withContext(ioDispatcher) {
        try {
            historyDao.removeAll()
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun upsertHistory(historyUpdate: HistoryUpdate): Unit = withContext(ioDispatcher) {
        val entity = HistoryEntity(
            id = 0,
            chapterId = historyUpdate.chapterId,
            lastRead = historyUpdate.readAt,
            timeRead = historyUpdate.sessionReadDuration,
        )
        historyDao.upsert(entity)
    }

    override suspend fun getHistoryByChapterId(chapterId: Long): History? = withContext(ioDispatcher) {
        historyDao.getHistoryByChapterId(chapterId)?.let(HistoryMapper::mapHistory)
    }

    override suspend fun removeResettedHistory() = withContext(ioDispatcher) {
        historyDao.removeResettedHistory()
    }
}
