package ephyra.domain.content.source

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentUnit

/**
 * Abstraction over how content is resolved from a base URL — reshaped profile-free by
 * `ADR-0017`, which deleted the profile path (`SourceProfile`, the cache, the orchestrator)
 * this contract used to be written against. The engine set is deliberately empty until
 * Jellyfin lands; the contract survives so the first real implementer shapes it, not the
 * memory of a removed path.
 *
 * The methods are addressed by `baseUrl` rather than a profile object: a profile was a cached
 * *answer* about a source, and passing the answer back into every call was how the profile
 * path stayed reachable (and kept a second copy of existence) long after nothing wrote it.
 */
interface ContentSourceEngine {

    /**
     * The [SourceType]s this engine serves.
     *
     * Overlap is tolerated: the first engine registered for a type wins.
     */
    val handles: Set<SourceType>
        get() = emptySet()

    /** Perform a search against a source. @param page Page number (1-based, for paginated results). */
    suspend fun search(baseUrl: String, query: String, page: Int): List<ContentItem>

    /** Fetch full details for a single content item. */
    suspend fun getItem(baseUrl: String, url: String): ContentItem

    /** Fetch popular/trending items from a source. */
    suspend fun getPopular(baseUrl: String, page: Int): List<ContentItem>

    /** Fetch the latest/updated items from a source. */
    suspend fun getLatest(baseUrl: String, page: Int): List<ContentItem>

    /** Fetch sequential content units (chapters/episodes/tracks) for a content item URL. */
    suspend fun getChapters(baseUrl: String, url: String): List<ContentUnit>

    /** Fetch the page images or media resource paths for a unit URL. */
    suspend fun getPages(baseUrl: String, url: String): List<String>
}
