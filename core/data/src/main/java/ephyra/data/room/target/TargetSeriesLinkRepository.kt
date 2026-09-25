package ephyra.data.room.target

import androidx.room.withTransaction
import ephyra.domain.series.CanonicalLinkCandidate
import ephyra.domain.series.CanonicalLinkRepository
import ephyra.domain.series.CanonicalLinkState
import ephyra.domain.series.CanonicalSeriesLink
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Isolated Room implementation of the explicitly reviewed canonical-link lifecycle. */
class TargetSeriesLinkRepository(
    private val database: TargetDatabase,
    private val json: Json = Json,
    private val now: () -> Long = System::currentTimeMillis,
) : CanonicalLinkRepository {

    override suspend fun find(id: String): CanonicalSeriesLink? = database.targetSeriesDao().getLink(id)?.toDomain()

    override suspend fun propose(candidate: CanonicalLinkCandidate): CanonicalSeriesLink? = database.withTransaction {
        val dao = database.targetSeriesDao()
        val series = dao.getSeries(candidate.canonicalSeriesId) ?: return@withTransaction null
        val canonicalSource = dao.getSourceReference(series.localId, candidate.canonicalSourceIdentity.sourceId)
            ?: return@withTransaction null
        val linkedSource = resolveSource(candidate.sourceIdentity) ?: return@withTransaction null
        require(linkedSource.seriesId != series.localId)
        require(canonicalSource.externalId == candidate.canonicalSourceIdentity.externalId)
        require(canonicalSource.url == candidate.canonicalSourceIdentity.url)

        val id = linkId(candidate)
        dao.getLink(id)?.toDomain()?.let { return@withTransaction it }
        val link = CanonicalSeriesLink(
            id = id,
            candidate = candidate,
            createdAtMillis = now(),
        )
        dao.insertLinkIfMissing(link.toEntity())
        dao.getLink(id)?.toDomain()
    }

    override suspend fun confirm(id: String, nowMillis: Long): CanonicalSeriesLink? = transition(id) {
        it.confirm(nowMillis)
    }

    override suspend fun reject(id: String, nowMillis: Long): CanonicalSeriesLink? = transition(id) {
        it.reject(nowMillis)
    }

    override suspend fun revoke(id: String, nowMillis: Long): CanonicalSeriesLink? = transition(id) {
        it.revoke(nowMillis)
    }

    private suspend fun resolveSource(
        identity: ephyra.domain.series.DurableSeriesIdentity,
    ): TargetSeriesSourceEntity? {
        val externalId = identity.externalId
        return if (externalId != null) {
            database.targetSeriesDao().getSeriesByExternalId(identity.sourceId, externalId)
        } else {
            database.targetSeriesDao().getSeriesByUrl(identity.sourceId, identity.url)
        }
    }

    private suspend fun transition(
        id: String,
        update: (CanonicalSeriesLink) -> CanonicalSeriesLink,
    ): CanonicalSeriesLink? = database.withTransaction {
        val dao = database.targetSeriesDao()
        val current = dao.getLink(id)?.toDomain() ?: return@withTransaction null
        val updated = update(current)
        dao.upsertLink(updated.toEntity())
        updated
    }

    private fun linkId(candidate: CanonicalLinkCandidate): String {
        val key = "${candidate.canonicalSeriesId}\u0000${candidate.sourceIdentity.stableKey}"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return "link:" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun CanonicalSeriesLink.toEntity() = TargetSeriesLinkEntity(
        linkId = id,
        canonicalSeriesId = candidate.canonicalSeriesId,
        canonicalSourceId = candidate.canonicalSourceIdentity.sourceId,
        canonicalExternalId = candidate.canonicalSourceIdentity.externalId,
        canonicalUrl = candidate.canonicalSourceIdentity.url,
        canonicalContentType = candidate.canonicalSourceIdentity.contentType.name,
        sourceId = candidate.sourceIdentity.sourceId,
        externalId = candidate.sourceIdentity.externalId,
        url = candidate.sourceIdentity.url,
        contentType = candidate.sourceIdentity.contentType.name,
        confidence = candidate.confidence,
        evidenceJson = json.encodeToString(candidate.evidence),
        state = state.name,
        revision = revision,
        createdAt = createdAtMillis,
        decidedAt = decidedAtMillis,
    )

    private fun TargetSeriesLinkEntity.toDomain(): CanonicalSeriesLink = CanonicalSeriesLink(
        id = linkId,
        candidate = CanonicalLinkCandidate(
            canonicalSeriesId = canonicalSeriesId,
            canonicalSourceIdentity = ephyra.domain.series.DurableSeriesIdentity(
                sourceId = canonicalSourceId,
                externalId = canonicalExternalId,
                url = canonicalUrl,
                contentType = ephyra.domain.content.model.ContentType.valueOf(canonicalContentType),
            ),
            sourceIdentity = ephyra.domain.series.DurableSeriesIdentity(
                sourceId = sourceId,
                externalId = externalId,
                url = url,
                contentType = ephyra.domain.content.model.ContentType.valueOf(contentType),
            ),
            confidence = confidence,
            evidence = json.decodeFromString(candidateEvidenceSerializer, evidenceJson),
        ),
        state = CanonicalLinkState.valueOf(state),
        revision = revision,
        createdAtMillis = createdAt,
        decidedAtMillis = decidedAt,
    )

    private companion object {
        private val candidateEvidenceSerializer =
            kotlinx.serialization.serializer<List<ephyra.domain.series.CanonicalLinkEvidence>>()
    }
}
