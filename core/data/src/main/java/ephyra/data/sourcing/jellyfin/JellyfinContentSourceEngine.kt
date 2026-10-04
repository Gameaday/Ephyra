package ephyra.data.sourcing.jellyfin

import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentUnit
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.ContentSourceEngine
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType

/**
 * Binds [SourceType.REPOSITORY] — the type `EngineId.REPOSITORY` was reserved for — by declaring
 * it in [handles]; the registry in
 * [ephyra.domain.content.source.ContentSourceOrchestrator] does the rest, per `ADR-0015`.
 *
 * **Why a thin adapter rather than a second client.** The engine contract is profile-based
 * (`SourceProfile` → `ContentItem`), while Jellyfin is naturally a
 * [UnifiedContentSource][ephyra.domain.content.source.UnifiedContentSource]. Rather than
 * duplicating wire-format knowledge, the engine delegates every operation to
 * [JellyfinContentSource] and maps its catalog/chapter/page results into the engine's models.
 * That keeps one implementation of the Jellyfin API surface (the tracker's `JellyfinApi` DTO
 * contract) with two projections of it.
 *
 * **Ids are deliberately synthetic (`-1L`).** Engine results are pre-persistence: the caller
 * (orchestrator → ingest) assigns real database ids. Persisting a synthetic id would collide
 * across items, so the stable identifiers are the Jellyfin item ids carried in `url` and
 * `metadata`.
 */
class JellyfinContentSourceEngine(
    private val source: JellyfinContentSource,
) : ContentSourceEngine {

    /**
     * [SourceType.REPOSITORY] only. That type exists specifically for Jellyfin-style media
     * repositories and nothing else serves it today, so the routing is unambiguous.
     */
    override val handles: Set<SourceType> = setOf(SourceType.REPOSITORY)

    /**
     * Discovery for a Jellyfin server is trivial by construction — unlike scraped websites there
     * is nothing to probe: endpoints are fixed by the API, auth is a token, and responses are
     * JSON. The profile therefore records the known shape plus endpoint patterns so a cached
     * profile is self-describing, and is marked verified: the same configuration gate that the
     * engine's data operations apply.
     */
    override suspend fun discover(baseUrl: String): SourceProfile = SourceProfile(
        baseUrl = baseUrl,
        contentType = source.supportedTypes.first(),
        sourceType = SourceType.REPOSITORY,
        displayName = source.name,
        responseType = SourceProfile.ResponseType.JSON,
        pagination = SourceProfile.PaginationType.PAGE_BASED,
        authType = SourceProfile.AuthType.TOKEN,
        endpoints = mapOf(
            SourceProfile.Endpoint.SEARCH to SourceProfile.EndpointPattern(
                pathTemplate = "/Users/{userId}/Items?searchTerm={query}&Recursive=true&IncludeItemTypes=Series",
            ),
            SourceProfile.Endpoint.POPULAR to SourceProfile.EndpointPattern(
                pathTemplate = "/Users/{userId}/Items?SortBy=CommunityRating&SortOrder=Descending",
            ),
            SourceProfile.Endpoint.LATEST to SourceProfile.EndpointPattern(
                pathTemplate = "/Users/{userId}/Items?SortBy=DateCreated&SortOrder=Descending",
            ),
            SourceProfile.Endpoint.ITEM_DETAIL to SourceProfile.EndpointPattern(
                pathTemplate = "/Users/{userId}/Items/{itemId}",
            ),
            SourceProfile.Endpoint.CHAPTERS to SourceProfile.EndpointPattern(
                pathTemplate = "/Users/{userId}/Items?ParentId={itemId}",
            ),
            SourceProfile.Endpoint.PAGES to SourceProfile.EndpointPattern(
                pathTemplate = "/Items/{itemId}/Download",
            ),
        ),
        verified = true,
        lastUpdated = System.currentTimeMillis(),
    )

    override suspend fun search(profile: SourceProfile, query: String, page: Int): List<ContentItem> =
        source.getCatalog(page, FilterSet(query = query)).map { it.toContentItem() }

    override suspend fun getPopular(profile: SourceProfile, page: Int): List<ContentItem> =
        source.getCatalog(page, FilterSet(sortOrder = FilterSet.SortOrder.POPULAR))
            .map { it.toContentItem() }

    override suspend fun getLatest(profile: SourceProfile, page: Int): List<ContentItem> =
        source.getCatalog(page, FilterSet(sortOrder = FilterSet.SortOrder.LATEST))
            .map { it.toContentItem() }

    /**
     * Detail lookup by URL. The unified catalog response already carries full metadata, so this is
     * a scoped re-search rather than a second endpoint — the orchestrator treats an empty result
     * as a health failure either way.
     */
    override suspend fun getItem(profile: SourceProfile, url: String): ContentItem {
        val itemId = url.substringAfterLast('/')
        val entry = source.getCatalog(1, FilterSet(query = itemId))
            .firstOrNull { it.url == url }
            ?: error("Jellyfin item not found for url: $url")
        return entry.toContentItem()
    }

    override suspend fun getChapters(profile: SourceProfile, url: String): List<ContentUnit> =
        source.getChapterManifest(url).map { it.toContentUnit() }

    override suspend fun getPages(profile: SourceProfile, url: String): List<String> =
        source.loadPages(url).mapNotNull { page ->
            when (page) {
                is ContentPage.ImagePage -> page.imageUrl
                is ContentPage.StreamPage -> page.streamUrl
                is ContentPage.TextPage -> null
            }
        }

    // -- Mapping helpers --

    private fun CatalogEntry.toContentItem(): ContentItem =
        ContentItem.placeholder(
            url = url,
            title = title,
            // The legacy numeric source space has no Jellyfin id; persistence assigns the real
            // source linkage via the canonical ingest path. The metadata map carries the server
            // item id, which is the stable identifier across sessions.
            sourceId = -1L,
            contentType = type,
        ).copy(
            author = author,
            description = description,
            genres = genres,
            thumbnailUrl = coverUrl,
            metadata = mapOf(META_JELLYFIN_ITEM_ID to key.substringAfterLast('/')),
        )

    private fun ChapterInfo.toContentUnit(): ContentUnit = ContentUnit(
        id = -1L,
        contentItemId = -1L,
        url = url,
        title = title,
        number = number,
        dateUpload = dateUpload,
        progress = 0L,
        totalLength = 0L,
        lastRead = 0L,
        scanlator = scanlator,
    )

    companion object {
        /** Metadata key carrying the Jellyfin item id on engine-produced [ContentItem]s. */
        const val META_JELLYFIN_ITEM_ID = "jellyfin_item_id"
    }
}

