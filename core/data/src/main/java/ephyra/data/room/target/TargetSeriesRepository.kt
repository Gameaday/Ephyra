package ephyra.data.room.target

import androidx.room.withTransaction
import ephyra.domain.content.model.ContentType
import ephyra.domain.series.DurableSeriesIdentity
import ephyra.domain.series.DurableSeriesRecord
import ephyra.domain.series.DurableSeriesSnapshot
import ephyra.domain.series.SeriesRefreshResult
import ephyra.domain.series.SeriesRepository
import ephyra.domain.series.SeriesUpsertResult
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Isolated target-series repository. It is not wired to the production database.
 *
 * This first repository slice intentionally projects one source representation into one durable
 * series. It does not yet merge multiple source representations into a canonical series; that
 * requires a separate identity-linking policy and must not be inferred from matching titles.
 */
class TargetSeriesRepository(
    private val database: TargetDatabase,
    private val json: Json = Json,
    private val now: () -> Long = System::currentTimeMillis,
) : SeriesRepository {

    override suspend fun find(identity: DurableSeriesIdentity): DurableSeriesRecord? {
        val source = resolve(identity) ?: return null
        return record(source.seriesId, source)
    }

    override suspend fun upsert(snapshot: DurableSeriesSnapshot): SeriesUpsertResult {
        val existing = resolve(snapshot.identity)
        if (existing == null) {
            val localId = newLocalId(snapshot.identity)
            database.withTransaction { writeSourceOwnedMetadata(localId, snapshot) }
            return SeriesUpsertResult.Created(requireNotNull(find(snapshot.identity)))
        }
        return refreshExisting(snapshot, existing)
    }

    override suspend fun setLibraryMembership(localId: String, inLibrary: Boolean): DurableSeriesRecord? {
        return database.withTransaction {
            val series = database.targetSeriesDao().getSeries(localId) ?: return@withTransaction null
            val source =
                database.targetSeriesDao().getSourceReferences(localId).firstOrNull() ?: return@withTransaction null
            if (inLibrary) {
                database.targetSeriesDao().insertLibraryEntryIfMissing(
                    TargetLibraryEntryEntity(
                        seriesId = localId,
                        addedAt = now(),
                        librarySortPosition = null,
                        updatePolicy = "DEFAULT",
                        updateEnabled = true,
                        lastCheckedAt = null,
                        lastChangedAt = null,
                    ),
                )
            } else {
                database.targetSeriesDao().deleteLibraryEntry(localId)
            }
            record(localId, source)
        }
    }

    override suspend fun refresh(
        identity: DurableSeriesIdentity,
        incoming: DurableSeriesSnapshot,
    ): SeriesRefreshResult {
        val existing = resolve(identity) ?: return SeriesRefreshResult.NotFound(identity)
        return when (val result = refreshExisting(incoming, existing)) {
            is SeriesUpsertResult.Conflict -> SeriesRefreshResult.Conflict(result.existing, result.incoming)
            is SeriesUpsertResult.Created -> SeriesRefreshResult.Refreshed(result.record)
            is SeriesUpsertResult.Updated -> SeriesRefreshResult.Refreshed(result.record)
            is SeriesUpsertResult.Unchanged -> SeriesRefreshResult.Unchanged(result.record)
        }
    }

    private suspend fun refreshExisting(
        incoming: DurableSeriesSnapshot,
        existing: TargetSeriesSourceEntity,
    ): SeriesUpsertResult {
        val current = requireNotNull(record(existing.seriesId, existing))
        if (incoming.sourceRevision < existing.revision) {
            return SeriesUpsertResult.Conflict(current, incoming)
        }
        if (incoming.sourceRevision == existing.revision) {
            return if (incoming == current.snapshot) {
                SeriesUpsertResult.Unchanged(current)
            } else {
                SeriesUpsertResult.Conflict(current, incoming)
            }
        }
        database.withTransaction { writeSourceOwnedMetadata(existing.seriesId, incoming) }
        val updatedSource = requireNotNull(resolve(incomingIdentity(existing, incoming)))
        val updated = requireNotNull(record(existing.seriesId, updatedSource))
        return SeriesUpsertResult.Updated(updated)
    }

    private suspend fun writeSourceOwnedMetadata(localId: String, snapshot: DurableSeriesSnapshot) {
        val dao = database.targetSeriesDao()
        val existing = dao.getSeries(localId)
        dao.upsertSeries(
            TargetSeriesEntity(
                localId = localId,
                contentType = snapshot.identity.contentType.name,
                title = snapshot.title,
                author = snapshot.author,
                artist = snapshot.artist,
                description = snapshot.description,
                status = snapshot.status,
                genresJson = json.encodeToString(snapshot.genres),
                createdAt = existing?.createdAt ?: now(),
                updatedAt = now(),
            ),
        )
        dao.upsertSourceReference(
            TargetSeriesSourceEntity(
                seriesId = localId,
                sourceId = snapshot.identity.sourceId,
                externalId = snapshot.identity.externalId,
                url = snapshot.identity.url,
                revision = snapshot.sourceRevision,
                displayTitle = snapshot.title,
                thumbnailUrl = snapshot.thumbnailUrl,
                sourceMetadataJson = json.encodeToString(snapshot.metadata),
                lastSeenAt = now(),
            ),
        )
    }

    private suspend fun resolve(identity: DurableSeriesIdentity): TargetSeriesSourceEntity? {
        val dao = database.targetSeriesDao()
        val externalId = identity.externalId
        return if (externalId != null) {
            dao.getSeriesByExternalId(identity.sourceId, externalId)
        } else {
            dao.getSeriesByUrl(identity.sourceId, identity.url)
        }
    }

    private suspend fun record(localId: String, source: TargetSeriesSourceEntity): DurableSeriesRecord? {
        val series = database.targetSeriesDao().getSeries(localId) ?: return null
        val snapshot = DurableSeriesSnapshot(
            identity = DurableSeriesIdentity(
                sourceId = source.sourceId,
                externalId = source.externalId,
                url = source.url,
                contentType = runCatching { ContentType.valueOf(series.contentType) }.getOrDefault(ContentType.UNKNOWN),
            ),
            title = series.title,
            author = series.author,
            artist = series.artist,
            description = series.description,
            genres = runCatching { json.decodeFromString<List<String>>(series.genresJson) }.getOrDefault(emptyList()),
            status = series.status,
            thumbnailUrl = source.thumbnailUrl,
            sourceRevision = source.revision,
            metadata = runCatching {
                json.decodeFromString<Map<String, String>>(source.sourceMetadataJson.orEmpty())
            }.getOrDefault(emptyMap()),
        )
        return DurableSeriesRecord(
            localId = localId,
            snapshot = snapshot,
            inLibrary = database.targetSeriesDao().getLibraryEntry(localId) != null,
        )
    }

    private fun incomingIdentity(existing: TargetSeriesSourceEntity, incoming: DurableSeriesSnapshot) =
        DurableSeriesIdentity(
            sourceId = incoming.identity.sourceId,
            externalId = incoming.identity.externalId ?: existing.externalId,
            url = incoming.identity.url,
            contentType = incoming.identity.contentType,
        )

    private fun newLocalId(identity: DurableSeriesIdentity): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.stableKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "series:$digest"
    }
}
