package ephyra.domain.series

import ephyra.domain.content.model.ContentType

/**
 * Durable identity for a series in the target library model.
 *
 * Source identity is deliberately string-based. Numeric source IDs belong to the legacy Room
 * compatibility model and must not become the target identity contract.
 */
data class DurableSeriesIdentity(
    val sourceId: String,
    val externalId: String? = null,
    val url: String,
    val contentType: ContentType = ContentType.MANGA,
) {
    init {
        require(sourceId.isNotBlank()) { "Series source id must not be blank" }
        require(url.isNotBlank()) { "Series URL must not be blank" }
        require(externalId?.isNotBlank() != false) { "Series external id must not be blank when present" }
    }

    /** Stable identity key; external IDs win because source URLs may change. */
    val stableKey: String
        get() = buildString {
            append(sourceId)
            append('\u0000')
            append(externalId?.let { "external:$it" } ?: "url:$url")
        }
}

/** Source metadata captured for a durable series record. */
data class DurableSeriesSnapshot(
    val identity: DurableSeriesIdentity,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val thumbnailUrl: String? = null,
    val sourceRevision: Long = 1L,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(title.isNotBlank()) { "Series title must not be blank" }
        require(sourceRevision > 0) { "Series source revision must be positive" }
    }
}

/** The persisted record returned by the target repository. */
data class DurableSeriesRecord(
    val localId: String,
    val snapshot: DurableSeriesSnapshot,
    val inLibrary: Boolean,
)

sealed interface SeriesUpsertResult {
    data class Created(val record: DurableSeriesRecord) : SeriesUpsertResult
    data class Updated(val record: DurableSeriesRecord) : SeriesUpsertResult
    data class Unchanged(val record: DurableSeriesRecord) : SeriesUpsertResult
    data class Conflict(
        val existing: DurableSeriesRecord,
        val incoming: DurableSeriesSnapshot,
    ) : SeriesUpsertResult
}

sealed interface SeriesRefreshResult {
    data class Refreshed(val record: DurableSeriesRecord) : SeriesRefreshResult
    data class Unchanged(val record: DurableSeriesRecord) : SeriesRefreshResult
    data class NotFound(val identity: DurableSeriesIdentity) : SeriesRefreshResult
    data class Conflict(
        val record: DurableSeriesRecord,
        val incoming: DurableSeriesSnapshot,
    ) : SeriesRefreshResult
}

/**
 * Target persistence boundary. Implementations must be idempotent by [DurableSeriesIdentity.stableKey],
 * preserve user-owned state, and never perform library membership changes implicitly.
 */
interface SeriesRepository {
    suspend fun find(identity: DurableSeriesIdentity): DurableSeriesRecord?

    suspend fun upsert(snapshot: DurableSeriesSnapshot): SeriesUpsertResult

    suspend fun setLibraryMembership(localId: String, inLibrary: Boolean): DurableSeriesRecord?

    suspend fun refresh(
        identity: DurableSeriesIdentity,
        incoming: DurableSeriesSnapshot,
    ): SeriesRefreshResult
}
