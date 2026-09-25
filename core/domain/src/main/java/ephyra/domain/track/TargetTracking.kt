package ephyra.domain.track

/**
 * Source-neutral, user-owned tracking state for one target series.
 * Numeric tracker and media IDs from the legacy database are not part of this contract.
 */
data class TargetTrackingRecord(
    val seriesId: String,
    val trackerId: String,
    val remoteId: String?,
    val libraryId: String?,
    val title: String,
    val lastChapterRead: Double,
    val totalChapters: Long,
    val status: String,
    val score: Double,
    val remoteUrl: String,
    val startedAt: Long,
    val finishedAt: Long,
    val isPrivate: Boolean,
    val updatedAt: Long,
) {
    init {
        require(seriesId.isNotBlank()) { "Tracking series id must not be blank" }
        require(trackerId.isNotBlank()) { "Tracker id must not be blank" }
        require(title.isNotBlank()) { "Tracking title must not be blank" }
        require(totalChapters >= 0L) { "Total chapters must not be negative" }
    }
}

interface TargetTrackingRepository {
    suspend fun get(seriesId: String): List<TargetTrackingRecord>
    suspend fun upsert(record: TargetTrackingRecord): TargetTrackingRecord?
    suspend fun delete(seriesId: String, trackerId: String)
}
