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
)

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
