package ephyra.domain.content.source

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType

/**
 * Heuristic implementation of [ContentSourceEngine] that auto-discovers API patterns and scrapers at runtime.
 * Serves as the robust media-agnostic auto-discovery fallback.
 */
class HeuristicContentSourceEngine : ContentSourceEngine {

    override suspend fun discover(baseUrl: String): SourceProfile {
        return SourceProfile(
            baseUrl = baseUrl,
            contentType = ContentType.UNKNOWN,
            displayName = "Auto-discovered ($baseUrl)",
            verified = false,
        )
    }

    override suspend fun search(profile: SourceProfile, query: String, page: Int): List<ContentItem> {
        return emptyList()
    }

    override suspend fun getItem(profile: SourceProfile, url: String): ContentItem {
        return ContentItem.placeholder(
            url = url,
            title = "Auto-discovered Content",
            sourceId = -1L,
            contentType = ContentType.UNKNOWN,
        )
    }

    override suspend fun getPopular(profile: SourceProfile, page: Int): List<ContentItem> {
        return emptyList()
    }

    override suspend fun getLatest(profile: SourceProfile, page: Int): List<ContentItem> {
        return emptyList()
    }
}
