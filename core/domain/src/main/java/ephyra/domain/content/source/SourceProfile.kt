package ephyra.domain.content.source

import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType

/**
 * Describes how to interact with a specific content source (website/API).
 *
 * Produced by [ContentSourceEngine.discover] and cached persistently so
 * the heuristic discovery only runs once per source URL.
 */
data class SourceProfile(
    /** The base URL of the source (e.g. "https://mangadex.org"). */
    val baseUrl: String,

    /** The content type this source primarily serves. */
    val contentType: ContentType,

    /** The type of source this profile represents. */
    val sourceType: SourceType = SourceType.REMOTE_EXTENSION,

    /** Whether this source is enabled for content discovery. */
    val enabled: Boolean = true,

    /** Known API endpoints and their URL patterns. */
    val endpoints: Map<Endpoint, EndpointPattern> = emptyMap(),

    /** How the source's responses are formatted. */
    val responseType: ResponseType = ResponseType.AUTO,

    /** How pagination works. */
    val pagination: PaginationType = PaginationType.PAGE_BASED,

    /** CSS selectors for HTML-based sources (null for JSON/API sources). */
    val selectors: Map<DataField, String>? = null,

    /** JSONPath expressions for JSON-based sources (null for HTML sources). */
    val jsonPath: Map<DataField, String>? = null,

    /** Custom HTTP headers required by this source. */
    val headers: Map<String, String> = emptyMap(),

    /** Authentication required to access this source. */
    val authType: AuthType = AuthType.NONE,

    /** Estimated rate limit (minimum milliseconds between requests). */
    val rateLimitMs: Long = 0L,

    /** The source's display name (human-readable). */
    val displayName: String = baseUrl,

    /** Whether this profile was verified to work on last use. */
    val verified: Boolean = false,

    /** Timestamp of last successful health check. */
    val lastHealthCheck: Long = 0,

    /** Timestamp of last successful discovery/update. */
    val lastUpdated: Long = 0,

    /** Number of consecutive failures. */
    val failureCount: Int = 0,

    /** Optional repository ID if this profile represents a local repository. */
    val repositoryId: String? = null,
)

/**
 * The type of content source.
 *
 * `JS_SCRAPER` and `HEURISTIC` are both retired. `JS_SCRAPER` went with the JS runtime
 * (`ADR-0013`); `HEURISTIC` went because auto-discovering a site's DOM structure never worked well
 * enough to ship (`ADR-0015`). `fromString` maps both retired names onto [REMOTE_EXTENSION] so a
 * profile persisted by an older build resolves to a real engine instead of silently becoming the
 * default.
 */
enum class SourceType {
    /** A source provided by an extension APK, from a remote or private repository, or local. */
    REMOTE_EXTENSION,

    /** Local/network media repository (Jellyfin-style). Not yet implemented. */
    REPOSITORY,

    ;

    companion object {
        @Deprecated("Use REMOTE_EXTENSION instead", ReplaceWith("SourceType.REMOTE_EXTENSION"))
        val LEGACY_EXTENSION: SourceType get() = REMOTE_EXTENSION

        fun fromString(value: String?): SourceType = when (value?.trim()?.uppercase()) {
            // HEURISTIC is folded into REMOTE_EXTENSION rather than dropped: an install that profiled
            // a site would otherwise fall through to the default and change behaviour with no error.
            "REMOTE_EXTENSION", "LEGACY_EXTENSION", "JS_SCRAPER", "HEURISTIC" -> REMOTE_EXTENSION
            "REPOSITORY" -> REPOSITORY
            else -> REMOTE_EXTENSION
        }
    }
}

/** Known endpoint types for a content source. */
enum class Endpoint {
    SEARCH,
    POPULAR,
    LATEST,
    ITEM_DETAIL,
    CHAPTERS,
    PAGES,
}

/** URL pattern for a single endpoint. */
data class EndpointPattern(
    /** URL path template, e.g. "/api/manga?q={query}&page={page}". */
    val pathTemplate: String,

    /** HTTP method. */
    val method: HttpMethod = HttpMethod.GET,

    /** Expected response content type. */
    val responseType: ResponseType = ResponseType.AUTO,

    /** Body template for POST requests (null for GET). */
    val bodyTemplate: String? = null,
)

/** HTTP methods supported for endpoint calls. */
enum class HttpMethod { GET, POST, PUT, DELETE }

/** How the source formats its responses. */
enum class ResponseType {
    /** Heuristic engine should auto-detect. */
    AUTO,

    /** JSON API (most modern sources). */
    JSON,

    /** HTML page (requires CSS selectors for extraction). */
    HTML,

    /** RSS/Atom feed. */
    RSS,

    /** GraphQL API. */
    GRAPHQL,
}

/** Pagination strategy used by the source. */
enum class PaginationType {
    /** page=N parameter in URL. */
    PAGE_BASED,

    /** cursor/offset parameter. */
    CURSOR_BASED,

    /** Infinite scroll (no explicit pagination). */
    INFINITE_SCROLL,

    /** No pagination (single page only). */
    NONE,
}

/** Data fields that can be extracted from source responses. */
enum class DataField {
    ITEM_LIST,
    ITEM_TITLE,
    ITEM_URL,
    ITEM_THUMBNAIL,
    ITEM_DESCRIPTION,
    ITEM_AUTHOR,
    ITEM_ARTIST,
    ITEM_GENRES,
    ITEM_STATUS,
    ITEM_CONTENT_TYPE,
    TOTAL_PAGES,
    DETAIL_TITLE,
    DETAIL_DESCRIPTION,
    DETAIL_AUTHOR,
    DETAIL_ARTIST,
    DETAIL_GENRES,
    DETAIL_STATUS,
    DETAIL_THUMBNAIL,
    CHAPTER_LIST,
    CHAPTER_TITLE,
    CHAPTER_NUMBER,
    CHAPTER_DATE,
    PAGE_LIST,
    PAGE_URL,
    NEXT_PAGE,
    NEXT_CURSOR,
}

/** Authentication type required by the source. */
enum class AuthType {
    NONE,
    BASIC,
    TOKEN,
    OAUTH,
    COOKIE,
}
