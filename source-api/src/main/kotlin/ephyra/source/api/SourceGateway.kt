

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
    /**
     * The source's own web address, when it has one.
     *
     * **Why this is here rather than left to the caller.** Opening a source in a web view,
     * offering "open in browser", and building a share link all need this, and each was
     * reaching through the legacy `HttpSource` to get it -- which is the dependency this
     * contract exists to remove. A caller that cannot ask the descriptor has no choice but to
     * keep the legacy reference alive.
     *
     * Null for a source with no web presence, such as a purely local library, which is why it
     * is nullable rather than defaulted to a placeholder.
     */
    val homeUrl: String? = null,
    /**
     * Whether requests to this source are exempt from the per-chapter request allowance.
     *
     * That decision was being made by checking whether the legacy source implemented
     * `UnmeteredSource`. It is a property of the source, so it belongs beside the other
     * declared facts rather than as a type test at the call site.
     */
    val unmetered: Boolean = false,
) {
    init {
        require(displayName.isNotBlank()) { "Source display name must not be blank" }
        require(revision > 0) { "Source revision must be positive" }
        require(homeUrl == null || homeUrl.isNotBlank()) { "Home URL must be blank or a real address" }
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

/**
 * A catalogue listing request: the "popular" and "latest" queries.
 *
 * Separate from [SourceSearchRequest] rather than a nullable `query` on it, because browsing popular
 * and searching for a term are different operations that happen to share a paging shape -- and a
 * nullable field would let a caller send a search request with no query at all.
 */
data class SourceCatalogueRequest(
    val contentTypes: Set<ContentType> = emptySet(),
    val cursor: String? = null,
)

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

    /**
     * The source's own popular listing.
     *
     * [SourceCapability.POPULAR] was declared in the first version of this contract with no way to
     * exercise it, so a caller who found a source advertising it had to reach for the legacy type to
     * do anything with it. A capability a consumer cannot call is a capability that pulls back the
     * dependency it was meant to remove.
     *
     * Defaults to [SourceResult.Unsupported] rather than being abstract, and that is the whole
     * design point: a gateway without a popular listing says so, and adding the capability does not
     * force every implementor to write a method it has no answer for. A gateway that *does* have one
     * overrides it -- `LegacySourceGateway` delegates to `getPopularManga` -- and then
     * [SourceDescriptor.supports] is a promise the type keeps.
     */
    suspend fun getPopular(request: SourceCatalogueRequest): SourceResult<SourcePage<SourceContentItem>> =
        SourceResult.Unsupported(SourceCapability.POPULAR)

    /**
     * The source's latest listing.
     *
     * Defaults to [SourceResult.Unsupported] so a gateway need not implement it to satisfy the
     * contract, which keeps a descriptor's advertised `LATEST` capability honest rather than
     * optimistic.
     */
    suspend fun getLatest(request: SourceCatalogueRequest): SourceResult<SourcePage<SourceContentItem>> =
        SourceResult.Unsupported(SourceCapability.LATEST)

    suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem>

    suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>>

    suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>>
}
