package ephyra.data.source

import ephyra.core.common.di.IoDispatcher
import ephyra.data.room.daos.SourceDao
import ephyra.data.room.entities.SourceEntity
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.repository.StubSourceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StubSourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StubSourceRepository {

    override fun subscribeAll(): Flow<List<StubSource>> {
        return sourceDao.subscribeAll()
            .map { list -> list.map(SourceMapper::mapStubSource) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getStubSource(id: Long): StubSource? = withContext(ioDispatcher) {
        sourceDao.getStubSource(id)?.let(SourceMapper::mapStubSource)
    }

    override suspend fun upsertStubSource(id: Long, lang: String, name: String) = withContext(ioDispatcher) {
        sourceDao.upsert(SourceEntity(id, lang, name))
    }
}

object SourceMapper {
    fun mapStubSource(entity: SourceEntity): StubSource {
        return StubSource(id = entity.id, lang = entity.lang, name = entity.name)
    }
}
