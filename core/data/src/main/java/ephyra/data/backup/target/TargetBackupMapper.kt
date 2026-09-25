package ephyra.data.backup.target

import ephyra.data.room.target.TargetChapterEntity
import ephyra.data.room.target.TargetChapterStateEntity
import ephyra.data.room.target.TargetHistoryEntity
import ephyra.data.room.target.TargetLibraryEntryEntity
import ephyra.data.room.target.TargetSeriesEntity
import ephyra.data.room.target.TargetSeriesSourceEntity
import kotlinx.serialization.json.Json

class TargetBackupMapper(
    private val json: Json = Json,
) {
    fun toBackupSeries(snapshot: TargetBackupSnapshot): TargetBackupSeries {
        return TargetBackupSeries(
            localId = snapshot.series.localId,
            contentType = snapshot.series.contentType,
            title = snapshot.series.title,
            author = snapshot.series.author,
            artist = snapshot.series.artist,
            description = snapshot.series.description,
            status = snapshot.series.status,
            genres = runCatching {
                json.decodeFromString<List<String>>(snapshot.series.genresJson)
            }.getOrDefault(emptyList()),
            createdAt = snapshot.series.createdAt,
            updatedAt = snapshot.series.updatedAt,
            sourceReferences = snapshot.sourceReferences.map { it.toBackup() },
            libraryEntry = snapshot.libraryEntry?.toBackup(),
            chapters = snapshot.chapters.map { it.toBackup() },
            chapterStates = snapshot.chapterStates.map { it.toBackup() },
            history = snapshot.history.map { it.toBackup() },
        )
    }

    fun toBackupDocument(snapshots: List<TargetBackupSnapshot>): TargetBackupDocument {
        return TargetBackupDocument(series = snapshots.map(::toBackupSeries))
    }

    fun fromBackup(series: TargetBackupSeries): TargetBackupSnapshot {
        require(series.localId.isNotBlank()) { "Target series local id must not be blank" }
        require(series.title.isNotBlank()) { "Target series title must not be blank" }
        val chapterIds = series.chapters.map { it.localId }.toSet()
        require(chapterIds.size == series.chapters.size) { "Target backup contains duplicate chapter local ids" }
        require(series.chapterStates.all { it.chapterId in chapterIds }) {
            "Target backup contains chapter state for an unknown chapter"
        }
        require(series.history.all { it.targetChapterLocalId in chapterIds }) {
            "Target backup contains history for an unknown chapter"
        }
        return TargetBackupSnapshot(
            series = TargetSeriesEntity(
                localId = series.localId,
                contentType = series.contentType,
                title = series.title,
                author = series.author,
                artist = series.artist,
                description = series.description,
                status = series.status,
                genresJson = json.encodeToString(series.genres),
                createdAt = series.createdAt,
                updatedAt = series.updatedAt,
            ),
            sourceReferences = series.sourceReferences.map { it.toEntity(series.localId) },
            libraryEntry = series.libraryEntry?.toEntity(series.localId),
            chapters = series.chapters.map { it.toEntity(series.localId) },
            chapterStates = series.chapterStates.map { it.toEntity() },
            history = series.history.map { it.toEntity() },
        )
    }

    private fun TargetSeriesSourceEntity.toBackup() = TargetBackupSourceReference(
        sourceId,
        externalId,
        url,
        revision,
        displayTitle,
        thumbnailUrl,
        sourceMetadataJson,
        lastSeenAt,
    )

    private fun TargetBackupSourceReference.toEntity(seriesId: String) = TargetSeriesSourceEntity(
        seriesId, sourceId, externalId, url, revision, displayTitle, thumbnailUrl, sourceMetadataJson, lastSeenAt,
    )

    private fun TargetLibraryEntryEntity.toBackup() = TargetBackupLibraryEntry(
        addedAt,
        librarySortPosition,
        updatePolicy,
        updateEnabled,
        lastCheckedAt,
        lastChangedAt,
    )

    private fun TargetBackupLibraryEntry.toEntity(seriesId: String) = TargetLibraryEntryEntity(
        seriesId,
        addedAt,
        librarySortPosition,
        updatePolicy,
        updateEnabled,
        lastCheckedAt,
        lastChangedAt,
    )

    private fun TargetChapterEntity.toBackup() = TargetBackupChapter(
        localId, sourceId, externalId, url, title, unitNumber, scanlator, sortKey, revision, publishedAt, fetchedAt,
    )

    private fun TargetBackupChapter.toEntity(seriesId: String) = TargetChapterEntity(
        localId = localId,
        seriesId = seriesId,
        sourceId = sourceId,
        externalId = externalId,
        url = url,
        title = title,
        unitNumber = unitNumber,
        scanlator = scanlator,
        sortKey = sortKey,
        revision = revision,
        publishedAt = publishedAt,
        fetchedAt = fetchedAt,
    )

    private fun TargetChapterStateEntity.toBackup() = TargetBackupChapterState(
        chapterId = chapterId,
        isRead = isRead,
        bookmarked = bookmarked,
        lastPageRead = lastPageRead,
    )

    private fun TargetBackupChapterState.toEntity() = TargetChapterStateEntity(
        chapterId = chapterId,
        isRead = isRead,
        bookmarked = bookmarked,
        lastPageRead = lastPageRead,
    )

    private fun TargetHistoryEntity.toBackup() = TargetBackupHistory(
        targetChapterLocalId = targetChapterLocalId,
        lastReadAt = lastReadAt,
        readDurationMs = readDurationMs,
    )

    private fun TargetBackupHistory.toEntity() = TargetHistoryEntity(
        targetChapterLocalId = targetChapterLocalId,
        lastReadAt = lastReadAt,
        readDurationMs = readDurationMs,
    )
}
