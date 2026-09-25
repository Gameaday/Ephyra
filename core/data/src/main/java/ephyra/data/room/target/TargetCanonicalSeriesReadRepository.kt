package ephyra.data.room.target

import androidx.room.withTransaction
import ephyra.domain.content.model.ContentType
import ephyra.domain.series.CanonicalAggregateIssue
import ephyra.domain.series.CanonicalLinkState
import ephyra.domain.series.CanonicalSeriesAggregate
import ephyra.domain.series.CanonicalSeriesAggregateResult
import ephyra.domain.series.CanonicalSeriesReadRepository
import ephyra.domain.series.DurableSeriesIdentity
import ephyra.domain.series.SeriesSourceRepresentation

/** Read-only projection of a target series and explicitly confirmed cross-source representations. */
class TargetCanonicalSeriesReadRepository(
    private val database: TargetDatabase,
) : CanonicalSeriesReadRepository {
    override suspend fun get(seriesId: String): CanonicalSeriesAggregateResult = database.withTransaction {
        val dao = database.targetSeriesDao()
        val series = dao.getSeries(seriesId)
            ?: return@withTransaction CanonicalSeriesAggregateResult.NotFound(seriesId)
        val contentType = runCatching { ContentType.valueOf(series.contentType) }.getOrElse {
            return@withTransaction CanonicalSeriesAggregateResult.Invalid(
                seriesId,
                "Unknown target content type ${series.contentType}",
            )
        }
        val base = dao.getSourceReferences(seriesId).map { it.toRepresentation(series.contentType) }
        if (base.isEmpty()) {
            return@withTransaction CanonicalSeriesAggregateResult.Invalid(
                seriesId,
                "Canonical series has no source representation",
            )
        }

        val issues = mutableListOf<CanonicalAggregateIssue>()
        val representations = linkedMapOf<String, SeriesSourceRepresentation>()
        base.forEach { representations[it.identity.stableKey] = it }
        dao.getLinksForSeriesAndState(seriesId, CanonicalLinkState.CONFIRMED.name).forEach { link ->
            val matches = resolveSources(link.sourceId, link.externalId, link.url)
            when {
                matches.isEmpty() -> issues += CanonicalAggregateIssue.MissingLinkedSource(
                    link.linkId,
                    "Confirmed link ${link.linkId} references a source that no longer exists",
                )
                matches.size > 1 -> issues += CanonicalAggregateIssue.DuplicateSourceIdentity(
                    link.sourceId,
                    "Confirmed link ${link.linkId} matches multiple source representations",
                )
                else -> {
                    val representation = matches.single().toRepresentation(link.contentType)
                    val existing = representations[representation.identity.stableKey]
                    if (existing != null && existing.sourceSeriesId != representation.sourceSeriesId) {
                        issues += CanonicalAggregateIssue.DuplicateSourceIdentity(
                            representation.identity.sourceId,
                            "Source ${representation.identity.sourceId} is represented by multiple target series",
                        )
                    } else if (existing == null) {
                        representations[representation.identity.stableKey] = representation
                    }
                }
            }
        }
        if (representations.isEmpty()) {
            return@withTransaction CanonicalSeriesAggregateResult.Invalid(
                seriesId,
                "Canonical aggregate has no usable source representation",
            )
        }
        CanonicalSeriesAggregateResult.Found(
            CanonicalSeriesAggregate(
                seriesId = seriesId,
                contentType = contentType,
                title = series.title,
                representations = representations.values.toList(),
                inLibrary = dao.getLibraryEntry(seriesId) != null,
                issues = issues,
            ),
        )
    }

    private suspend fun resolveSources(
        sourceId: String,
        externalId: String?,
        url: String,
    ): List<TargetSeriesSourceEntity> {
        val dao = database.targetSeriesDao()
        return if (externalId != null) {
            dao.getSeriesSourcesByExternalId(sourceId, externalId)
        } else {
            dao.getSeriesSourcesByUrl(sourceId, url)
        }
    }

    private fun TargetSeriesSourceEntity.toRepresentation(contentTypeName: String): SeriesSourceRepresentation =
        SeriesSourceRepresentation(
            sourceSeriesId = seriesId,
            identity = DurableSeriesIdentity(
                sourceId = sourceId,
                externalId = externalId,
                url = url,
                contentType = ContentType.valueOf(contentTypeName),
            ),
            displayTitle = displayTitle,
            thumbnailUrl = thumbnailUrl,
            revision = revision,
        )
}
