package ephyra.domain.series

import ephyra.domain.content.model.ContentType

/** One durable source representation available for a canonical series. */
data class SeriesSourceRepresentation(
    val sourceSeriesId: String,
    val identity: DurableSeriesIdentity,
    val displayTitle: String,
    val thumbnailUrl: String?,
    val revision: Long,
) {
    init {
        require(sourceSeriesId.isNotBlank()) { "Source series id must not be blank" }
        require(displayTitle.isNotBlank()) { "Source display title must not be blank" }
        require(revision > 0L) { "Source representation revision must be positive" }
    }
}

/** A non-fatal inconsistency discovered while projecting a canonical aggregate. */
sealed interface CanonicalAggregateIssue {
    val message: String

    data class MissingLinkedSource(val linkId: String, override val message: String) : CanonicalAggregateIssue
    data class DuplicateSourceIdentity(val sourceId: String, override val message: String) : CanonicalAggregateIssue
    data class InvalidLink(val linkId: String, override val message: String) : CanonicalAggregateIssue
}

/**
 * Read-only projection of one canonical series and all explicitly confirmed source
 * representations. Source selection remains an explicit product decision.
 */
data class CanonicalSeriesAggregate(
    val seriesId: String,
    val contentType: ContentType,
    val title: String,
    val representations: List<SeriesSourceRepresentation>,
    val inLibrary: Boolean,
    val issues: List<CanonicalAggregateIssue> = emptyList(),
) {
    init {
        require(seriesId.isNotBlank()) { "Canonical series id must not be blank" }
        require(title.isNotBlank()) { "Canonical series title must not be blank" }
        require(representations.isNotEmpty()) { "Canonical aggregate requires at least one source representation" }
        require(representations.map { it.identity.stableKey }.distinct().size == representations.size) {
            "Canonical aggregate source identities must be unique"
        }
    }
}

sealed interface CanonicalSeriesAggregateResult {
    data class Found(val aggregate: CanonicalSeriesAggregate) : CanonicalSeriesAggregateResult
    data class NotFound(val seriesId: String) : CanonicalSeriesAggregateResult
    data class Invalid(val seriesId: String, val reason: String) : CanonicalSeriesAggregateResult
}

/** Read-only boundary; this interface cannot merge, confirm, revoke, or select sources. */
interface CanonicalSeriesReadRepository {
    suspend fun get(seriesId: String): CanonicalSeriesAggregateResult
}
