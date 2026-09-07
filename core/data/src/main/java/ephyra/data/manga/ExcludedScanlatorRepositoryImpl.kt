package ephyra.data.manga

import ephyra.core.common.di.IoDispatcher
import ephyra.data.room.daos.ExcludedScanlatorDao
import ephyra.domain.manga.repository.ExcludedScanlatorRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExcludedScanlatorRepositoryImpl @Inject constructor(
    private val dao: ExcludedScanlatorDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExcludedScanlatorRepository {

    override suspend fun getExcludedScanlators(mangaId: Long): Set<String> = withContext(ioDispatcher) {
        dao.getExcludedScanlators(mangaId).toSet()
    }

    override fun subscribeExcludedScanlators(mangaId: Long): Flow<Set<String>> {
        return dao.getExcludedScanlatorsAsFlow(mangaId)
            .map { it.toSet() }
            .flowOn(ioDispatcher)
    }

    override suspend fun setExcludedScanlators(mangaId: Long, scanlators: Set<String>): Unit = withContext(
        ioDispatcher,
    ) {
        dao.setExcludedScanlators(mangaId, scanlators)
    }
}
