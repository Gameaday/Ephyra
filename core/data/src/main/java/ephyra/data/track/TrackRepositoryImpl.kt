package ephyra.data.track

import ephyra.core.common.di.IoDispatcher
import ephyra.data.room.daos.TrackDao
import ephyra.data.room.entities.TrackEntity
import ephyra.domain.track.model.Track
import ephyra.domain.track.repository.TrackRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TrackRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrackRepository {

    override suspend fun getTrackById(id: Long): Track? = withContext(ioDispatcher) {
        trackDao.getTrackById(id)?.let(TrackMapper::mapTrack)
    }

    override suspend fun getTracksByMangaId(mangaId: Long): List<Track> = withContext(ioDispatcher) {
        trackDao.getTracksByMangaId(mangaId).map(TrackMapper::mapTrack)
    }

    override fun getTracksAsFlow(): Flow<List<Track>> {
        return trackDao.getTracksAsFlow()
            .map { list -> list.map(TrackMapper::mapTrack) }
            .flowOn(ioDispatcher)
    }

    override fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<Track>> {
        return trackDao.getTracksByMangaIdAsFlow(mangaId)
            .map { list -> list.map(TrackMapper::mapTrack) }
            .flowOn(ioDispatcher)
    }

    override suspend fun delete(mangaId: Long, trackerId: Long): Unit = withContext(ioDispatcher) {
        trackDao.delete(mangaId, trackerId)
    }

    override suspend fun insert(track: Track): Unit = withContext(ioDispatcher) {
        insertAll(listOf(track))
    }

    override suspend fun insertAll(tracks: List<Track>): Unit = withContext(ioDispatcher) {
        if (tracks.isEmpty()) return@withContext
        val entities = tracks.map { mangaTrack ->
            TrackEntity(
                id = mangaTrack.id,
                mangaId = mangaTrack.mangaId,
                syncId = mangaTrack.trackerId,
                remoteId = mangaTrack.remoteId,
                libraryId = mangaTrack.libraryId,
                title = mangaTrack.title,
                lastChapterRead = mangaTrack.lastChapterRead,
                totalChapters = mangaTrack.totalChapters,
                status = mangaTrack.status,
                score = mangaTrack.score,
                remoteUrl = mangaTrack.remoteUrl,
                startDate = mangaTrack.startDate,
                finishDate = mangaTrack.finishDate,
                isPrivate = mangaTrack.isPrivate,
            )
        }
        trackDao.insertAll(entities)
    }
}
