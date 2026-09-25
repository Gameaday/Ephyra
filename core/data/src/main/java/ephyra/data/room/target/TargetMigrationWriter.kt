package ephyra.data.room.target

import androidx.room.withTransaction
import ephyra.domain.series.LegacySeriesMigrationPlan
import kotlinx.serialization.json.Json

/** Writes a pure migration plan into the isolated target schema in one transaction. */
class TargetMigrationWriter(
    private val database: TargetDatabase,
    private val json: Json = Json,
) {
    suspend fun write(plan: LegacySeriesMigrationPlan) {
        database.withTransaction {
            val dao = database.targetSeriesDao()
            val snapshot = plan.snapshot
            val sourceIdentity = snapshot.identity
            val series = TargetSeriesEntity(
                localId = plan.targetLocalId,
                contentType = sourceIdentity.contentType.name,
                title = snapshot.title,
                author = snapshot.author,
                artist = snapshot.artist,
                description = snapshot.description,
                status = snapshot.status,
                genresJson = json.encodeToString(snapshot.genres),
                createdAt = 0L,
                updatedAt = 0L,
            )
            dao.upsertSeries(series)
            dao.upsertSourceReference(
                TargetSeriesSourceEntity(
                    seriesId = plan.targetLocalId,
                    sourceId = sourceIdentity.sourceId,
                    externalId = sourceIdentity.externalId,
                    url = sourceIdentity.url,
                    revision = snapshot.sourceRevision,
                    displayTitle = snapshot.title,
                    thumbnailUrl = snapshot.thumbnailUrl,
                    sourceMetadataJson = json.encodeToString(snapshot.metadata),
                    lastSeenAt = 0L,
                ),
            )
            if (plan.inLibrary) {
                dao.insertLibraryEntryIfMissing(
                    TargetLibraryEntryEntity(
                        seriesId = plan.targetLocalId,
                        addedAt = plan.libraryAddedAt,
                        librarySortPosition = null,
                        updatePolicy = "DEFAULT",
                        updateEnabled = true,
                        lastCheckedAt = null,
                        lastChangedAt = null,
                    ),
                )
            }
            plan.chapters.forEach { chapter ->
                dao.upsertChapter(
                    TargetChapterEntity(
                        localId = chapter.targetLocalId,
                        seriesId = plan.targetLocalId,
                        sourceId = chapter.sourceId,
                        externalId = null,
                        url = chapter.url,
                        title = chapter.title,
                        unitNumber = chapter.chapterNumber,
                        scanlator = chapter.scanlator,
                        sortKey = chapter.sourceOrder.toString(),
                        revision = chapter.revision,
                        publishedAt = chapter.dateUpload,
                        fetchedAt = chapter.dateFetch,
                    ),
                )
                dao.insertChapterStateIfMissing(
                    TargetChapterStateEntity(
                        chapterId = chapter.targetLocalId,
                        isRead = chapter.read,
                        bookmarked = chapter.bookmark,
                        lastPageRead = chapter.lastPageRead,
                    ),
                )
            }
            plan.history.forEach { history ->
                dao.insertHistoryIfMissing(
                    TargetHistoryEntity(
                        targetChapterLocalId = history.targetChapterLocalId,
                        lastReadAt = history.lastReadAtMillis,
                        readDurationMs = history.readDurationMillis,
                    ),
                )
            }
        }
    }
}
