

package ephyra.source.api

import ephyra.domain.content.model.ContentType

/** Stable identity for a source outside the legacy numeric source-id space. */
@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Source id must not be blank" }
    }
}

/** The product-visible kind of source implementation. */
enum class SourceKind {
    NATIVE,
    LOCAL,
    REPOSITORY,
    LEGACY_COMPATIBILITY,
}

/** Operations a source may explicitly support. */
enum class SourceCapability {
    SEARCH,
    DETAILS,
    UNITS,
    RESOURCES,
    CATALOG,
    POPULAR,
    LATEST,
    UPDATES,
    RECOMMENDATIONS,
    MIGRATION,
    COLLECTIONS,
    PROGRESS_SYNC,
}

/** Trust state is an observation of the source definition, not a UI preference. */
enum class SourceTrustLevel {
    OFFICIAL,
    VERIFIED,
    USER,
    UNKNOWN,
}

/** How a source is integrated into the target product. */
enum class SourceCompatibilityLevel {
    NATIVE,
    ADAPTER,
    LEGACY,
}

/** Immutable source identity and declared product capabilities. */
data class SourceDescriptor(
    val id: SourceId,
    val displayName: String,
    val kind: SourceKind,
    val revision: Long,
    val capabilities: Set<SourceCapability>,
    val trustLevel: SourceTrustLevel = SourceTrustLevel.UNKNOWN,
    val compatibilityLevel: SourceCompatibilityLevel = SourceCompatibilityLevel.NATIVE,
    val contentTypes: Set<ContentType> = emptySet(),
) {
    init {
        require(displayName.isNotBlank()) { "Source display name must not be blank" }
        require(revision > 0) { "Source revision must be positive" }
    }

    fun supports(capability: SourceCapability): Boolean = capability in capabilities
}

/** A source-independent reference to a content item. */
data class ContentReference(
    val url: String,
    val externalId: String? = null,
) {
    init {
        require(url.isNotBlank()) { "Content URL must not be blank" }
    }
}

/** A source-independent reference to a sequential content unit. */
data class UnitReference(
    val url: String,
    val externalId: String? = null,
) {
    init {
        require(url.isNotBlank()) { "Unit URL must not be blank" }
    }
}

/** A page of results with an optional continuation cursor. */
data class SourcePage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
}

/** Search request independent of legacy FilterList and manga DTOs. */
data class SourceSearchRequest(
    val query: String,
    val contentTypes: Set<ContentType> = emptySet(),
    val filters: Map<String, String> = emptyMap(),
    val cursor: String? = null,
)

/** Resource descriptor returned for a unit; media-specific decoding is a later pipeline step. */
data class SourceResource(
    val url: String,
    val kind: ResourceKind,
    val mimeType: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    /** Inline payload for local/archive sources whose resource is not addressable by a URL. */
    val inlineBytes: ByteArray? = null,
) {
    init {
        require(url.isNotBlank() || inlineBytes != null) {
            "A source resource requires a URL or inline bytes"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SourceResource) return false
        return url == other.url &&
            kind == other.kind &&
            mimeType == other.mimeType &&
            metadata == other.metadata &&
            (inlineBytes?.contentEquals(other.inlineBytes) ?: (other.inlineBytes == null))
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        result = 31 * result + (inlineBytes?.contentHashCode() ?: 0)
        return result
    }
}

enum class ResourceKind {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    OTHER,
}

/** Typed source outcome. Empty is valid and never implies failure or unsupported. */
sealed interface SourceResult<out T> {
    data class Success<T>(val value: T) : SourceResult<T>
    data object Empty : SourceResult<Nothing>
    data class Unsupported(val capability: SourceCapability) : SourceResult<Nothing>
    data class TransientFailure(val message: String, val cause: Throwable? = null) : SourceResult<Nothing>
    data class PermanentFailure(val message: String, val cause: Throwable? = null) : SourceResult<Nothing>
    data class RateLimited(val retryAfterMillis: Long? = null) : SourceResult<Nothing>
}

/** Source-protocol content item; persistence/domain mapping happens outside the gateway. */
data class SourceContentItem(
    val sourceId: SourceId,
    val externalId: String? = null,
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val thumbnailUrl: String? = null,
    val contentType: ContentType = ContentType.UNKNOWN,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(url.isNotBlank()) { "Content URL must not be blank" }
        require(title.isNotBlank()) { "Content title must not be blank" }
    }
}

/** Source-protocol sequential unit; persistence/domain mapping happens outside the gateway. */
data class SourceContentUnit(
    val sourceId: SourceId,
    val contentExternalId: String? = null,
    val externalId: String? = null,
    val url: String,
    val title: String,
    val number: Double,
    val dateUpload: Long = 0L,
    val scanlator: String? = null,
) {
    init {
        require(url.isNotBlank()) { "Unit URL must not be blank" }
        require(title.isNotBlank()) { "Unit title must not be blank" }
    }
}

/** A single capability-gated source boundary. */
interface SourceGateway {
    val descriptor: SourceDescriptor

    suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>>

    suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem>

    suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>>

    suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>>
}
