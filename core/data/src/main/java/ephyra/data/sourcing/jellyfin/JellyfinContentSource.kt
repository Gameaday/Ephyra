package ephyra.data.sourcing.jellyfin

import ephyra.data.track.jellyfin.JellyfinApi
import ephyra.data.track.jellyfin.JellyfinCredentials
import ephyra.data.track.jellyfin.JellyfinItem
import ephyra.data.track.jellyfin.JellyfinItemsResponse
import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.UnifiedContentSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Pillar 2: Remote APIs — Jellyfin as a browse/search content source (Phase 3).
 *
 * Implements [UnifiedContentSource] against a *configured* Jellyfin server, mirroring how
 * [ephyra.data.sourcing.opds.OpdsContentSource] wraps OPDS. The server, user, token, and preferred
 * library all come from [JellyfinCredentials] — the same values the Jellyfin tracker wrote at
 * login — so there is no second setup flow and no reach into tracker internals.
 *
 * **Reuse over re-implementation.** URL shapes and cover-URL construction are delegated to
 * [JellyfinApi] (constructed locally with a no-op track id purely for its pure URL helpers), and
 * request/response handling uses the same `GET`/`awaitSuccess`/`parseAs` pipeline as the tracker
 * client. That keeps the two call sites on one wire-format definition.
 *
 * **Degraded-but-legible unconfigured state.** When credentials are missing, every operation
 * returns empty results rather than throwing: an unconfigured server must look like "no source"
 * in a catalog list, not like a broken one (`ADR-0015`'s legibility rule applied one layer up).
 */
class JellyfinContentSource(
    private val credentials: JellyfinCredentials,
    baseClient: OkHttpClient,
    private val json: Json,
) : UnifiedContentSource {

    override val id: String = ID

    /**
     * The server's own display name (captured at tracker login), falling back to "Jellyfin" so
     * the source is still identifiable in a picker before the first successful connect.
     */
    override val name: String = credentials.serverName().ifBlank { "Jellyfin" }

    /**
     * Manga and books only. Jellyfin also serves video/audio, but the reading pipeline this
     * source feeds renders pages — declaring those types here would let the browse UI offer
     * content it cannot display.
     */
    override val supportedTypes: Set<ContentType> = setOf(ContentType.MANGA, ContentType.BOOK)

    /** Authenticated client; see [JellyfinTokenInterceptor] (bottom of this file) for the design. */
    private val client: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(JellyfinTokenInterceptor(credentials))
        .build()

    /** Pure URL helpers borrowed from the tracker API. The tracker id is unused by the helpers. */
    private val api = JellyfinApi(0L, client, json)

    // -- Catalog (browse + search) --

    override suspend fun getCatalog(page: Int, filter: FilterSet): List<CatalogEntry> =
        withContext(Dispatchers.IO) {
            if (!credentials.isConfigured()) return@withContext emptyList()
            val serverUrl = credentials.serverUrl()
            val url = "$serverUrl/Users/${credentials.userId()}/Items".toHttpUrl()
                .newBuilder()
                .apply {
                    addQueryParameter("Recursive", "true")
                    addQueryParameter("IncludeItemTypes", "Series")
                    addQueryParameter(
                        "Fields",
                        "Overview,Genres,CommunityRating,ProductionYear,Studios,Tags,DateCreated",
                    )
                    addQueryParameter("EnableImageTypes", "Primary,Thumb,Backdrop")
                    addQueryParameter("Limit", PAGE_SIZE.toString())
                    addQueryParameter(
                        "StartIndex",
                        ((page.coerceAtLeast(1) - 1) * PAGE_SIZE).toString(),
                    )
                    when (filter.sortOrder) {
                        FilterSet.SortOrder.POPULAR -> {
                            addQueryParameter("SortBy", "CommunityRating")
                            addQueryParameter("SortOrder", "Descending")
                        }
                        FilterSet.SortOrder.LATEST -> {
                            addQueryParameter("SortBy", "DateCreated")
                            addQueryParameter("SortOrder", "Descending")
                        }
                        FilterSet.SortOrder.ALPHABETICAL -> {
                            addQueryParameter("SortBy", "SortName")
                            addQueryParameter("SortOrder", "Ascending")
                        }
                    }
                    if (filter.query.isNotBlank()) {
                        addQueryParameter("searchTerm", filter.query)
                    }
                    val libraryId = credentials.libraryId()
                    if (libraryId.isNotBlank()) {
                        // Scoped to the user's chosen library for accuracy when several exist.
                        addQueryParameter("ParentId", libraryId)
                    }
                }
                .build()

            fetchItems(url).map { it.toCatalogEntry(serverUrl) }
        }

    // -- Chapter manifest (volumes → chapters) --

    override suspend fun getChapterManifest(entryKey: String): List<ChapterInfo> =
        withContext(Dispatchers.IO) {
            if (!credentials.isConfigured()) return@withContext emptyList()
            val serverUrl = credentials.serverUrl()
            val itemId = entryKey.substringAfterLast('/')

            val url = "$serverUrl/Users/${credentials.userId()}/Items".toHttpUrl()
                .newBuilder()
                .apply {
                    addQueryParameter("ParentId", itemId)
                    addQueryParameter("Fields", "Path,DateCreated,IndexNumber")
                    addQueryParameter("SortBy", "ParentIndexNumber,IndexNumber")
                    addQueryParameter("SortOrder", "Ascending")
                }
                .build()

            val children = fetchItems(url)
            if (children.isEmpty()) {
                // A series with no children on the server is still readable as a single unit —
                // Jellyfin models single-volume publications exactly this way.
                listOf(
                    ChapterInfo(
                        key = "$serverUrl/Items/$itemId",
                        title = "Chapter 1",
                        number = 1.0,
                        url = "$serverUrl/Items/$itemId",
                    ),
                )
            } else {
                children.mapIndexed { index, child ->
                    child.toChapterInfo(serverUrl, fallbackNumber = (index + 1).toDouble())
                }
            }
        }

    // -- Pages --

    override suspend fun loadPage(chapterKey: String, pageIndex: Int): ContentPage {
        val pages = loadPages(chapterKey)
        return pages.getOrNull(pageIndex) ?: ContentPage.ImagePage(index = pageIndex)
    }

    override suspend fun loadPages(chapterKey: String): List<ContentPage> =
        withContext(Dispatchers.IO) {
            if (!credentials.isConfigured()) return@withContext emptyList()
            val serverUrl = credentials.serverUrl()
            val itemId = chapterKey.substringAfterLast('/')

            val item = fetchItem(serverUrl, itemId) ?: return@withContext emptyList()
            val downloadUrl = api.getItemDownloadUrl(serverUrl, itemId)
            val container = item.mediaSources?.firstOrNull()?.container?.lowercase()

            // **Single-page v1, deliberately.** Core Jellyfin has no endpoint that enumerates the
            // pages *inside* an archive item — page-level serving needs a reader plugin, which is
            // a per-server variable, not a contract we can rely on. Instead, the chapter is handed
            // to the pipeline as one addressable resource: an image container maps to an
            // [ContentPage.ImagePage] whose URL is the `/Download` endpoint, and everything else
            // (cbz/cbr/epub/pdf) becomes a [ContentPage.StreamPage] pointing at the same URL,
            // where the existing download/extract pipeline takes over. Follow-up: page-level
            // enumeration against servers that expose a reader plugin.
            val page = when (container) {
                "jpg", "jpeg", "png", "webp", "gif", "avif" ->
                    ContentPage.ImagePage(index = 0, imageUrl = downloadUrl)
                else ->
                    ContentPage.StreamPage(index = 0, streamUrl = downloadUrl)
            }
            listOf(page)
        }

    // -- Internal mapping helpers (pure, JVM-testable) --

    /**
     * Maps a series-level [JellyfinItem] to a [CatalogEntry]. Exposed `internal` so tests can
     * exercise the mapping with a captured JSON payload instead of a live server.
     */
    internal fun JellyfinItem.toCatalogEntry(serverUrl: String): CatalogEntry = CatalogEntry(
        key = "$serverUrl/Items/$id",
        title = name,
        url = "$serverUrl/Items/$id",
        coverUrl = if (hasImage()) api.buildCoverUrl(serverUrl, this) else null,
        type = ContentType.MANGA,
        description = overview,
        author = getCreators().joinToString(", ").ifBlank { null },
        genres = genres.orEmpty(),
    )

    internal fun JellyfinItem.toChapterInfo(
        serverUrl: String,
        fallbackNumber: Double,
    ): ChapterInfo = ChapterInfo(
        key = "$serverUrl/Items/$id",
        title = name.ifBlank { "Chapter ${indexNumber ?: fallbackNumber.toInt()}" },
        number = indexNumber?.toDouble() ?: fallbackNumber,
        dateUpload = parseEpochMillis(dateCreated),
        url = "$serverUrl/Items/$id",
    )

    private suspend fun fetchItems(url: HttpUrl): List<JellyfinItem> {
        val response = client.newCall(GET(url.toString())).awaitSuccess()
        return with(json) { response.parseAs<JellyfinItemsResponse>() }.items
    }

    /** Single-item fetch; Jellyfin returns the bare item object (not an items envelope) here. */
    private suspend fun fetchItem(serverUrl: String, itemId: String): JellyfinItem? {
        val response = client.newCall(GET("$serverUrl/Users/${credentials.userId()}/Items/$itemId"))
            .awaitSuccess()
        return with(json) { response.parseAs<JellyfinItem>() }
    }

    /** ISO-8601 → epoch millis; unparsable or absent stamps degrade to 0 (unknown), never throw. */
    private fun parseEpochMillis(isoTimestamp: String?): Long =
        isoTimestamp?.let { stamp ->
            runCatching { java.time.Instant.parse(stamp).toEpochMilli() }.getOrDefault(0L)
        } ?: 0L

    companion object {
        /** Stable source identity; also persisted inside source references. */
        const val ID = "jellyfin"

        /** Items per catalog page. Jellyfin's own web client uses 24 for grids. */
        internal const val PAGE_SIZE = 24
    }
}

/**
 * Adds the Jellyfin auth header for the content-source client.
 *
 * Mirrors [ephyra.data.track.jellyfin.JellyfinInterceptor] but reads the token from
 * [JellyfinCredentials] rather than the tracker instance, so the sourcing stack has no
 * dependency on `Application`-bound tracker machinery and stays unit-testable on the JVM.
 */
private class JellyfinTokenInterceptor(
    private val credentials: JellyfinCredentials,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = credentials.accessToken()
        val request = if (token.isBlank()) {
            // No token yet: pass the request through unchanged so the server's 401 surfaces —
            // the honest failure — instead of an anonymous request that would look configured.
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("X-Emby-Token", token)
                .build()
        }
        return chain.proceed(request)
    }
}
