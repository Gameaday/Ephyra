package ephyra.data.room.target

import ephyra.domain.track.TargetTrackingRecord
import ephyra.domain.track.TargetTrackingRepository

/** Isolated repository for user-owned tracking state; production tracker services remain legacy. */
class TargetTrackingRepositoryImpl(
    private val database: TargetDatabase,
) : TargetTrackingRepository {
    override suspend fun get(seriesId: String): List<TargetTrackingRecord> {
        return database.targetSeriesDao().getTracking(seriesId).map { it.toRecord() }
    }

    override suspend fun upsert(record: TargetTrackingRecord): TargetTrackingRecord? {
        require(database.targetSeriesDao().getSeries(record.seriesId) != null) {
            "Cannot track missing target series ${record.seriesId}"
        }
        database.targetSeriesDao().upsertTracking(
            TargetTrackingEntity(
                seriesId = record.seriesId,
                trackerId = record.trackerId,
                remoteId = record.remoteId,
                libraryId = record.libraryId,
                title = record.title,
                lastChapterRead = record.lastChapterRead,
                totalChapters = record.totalChapters,
                status = record.status,
                score = record.score,
                remoteUrl = record.remoteUrl,
                startedAt = record.startedAt,
                finishedAt = record.finishedAt,
                isPrivate = record.isPrivate,
                updatedAt = record.updatedAt,
            ),
        )
        return record
    }

    override suspend fun delete(seriesId: String, trackerId: String) {
        database.targetSeriesDao().deleteTracking(seriesId, trackerId)
    }

    private fun TargetTrackingEntity.toRecord() = TargetTrackingRecord(
        seriesId = seriesId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startedAt = startedAt,
        finishedAt = finishedAt,
        isPrivate = isPrivate,
        updatedAt = updatedAt,
    )
}
