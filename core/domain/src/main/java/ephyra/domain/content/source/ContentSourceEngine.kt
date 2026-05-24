package ephyra.domain.content.source

import ephyra.domain.content.model.ContentItem

/**
 * Sealed abstraction over how content is resolved from URLs.
 *
 * The app core never knows which engine is active. Each implementation
 * (heuristic, known-adapter, WASM, etc.) is completely isolated behind
 * this interface. This allows:
 *
 * 1. **Known adapter** — hand-tuned implementation for a specific source
 * 2. **HeuristicEngine** — auto-discovers interaction patterns at runtime
 * 3. **WasmEngine** — sandboxed JavaScript/WASM execution for complex sources
 *
 * All engines share the same contract: URL → SourceProfile → ContentItem.
 *
 * @see SourceProfile
 * @see ContentSourceOrchestrator
 */
sealed interface ContentSourceEngine {

    /**
     * Discover how to interact with a content source at [baseUrl].
     * Returns a [SourceProfile] describing endpoints, patterns, and selectors.
     *
     * This is called once per source URL and the result is cached persistently.
     */
    suspend fun discover(baseUrl: String): SourceProfile

    /**
     * Perform a search against a previously discovered source.
     *
     * @param profile The cached [SourceProfile] describing the source's API.
     * @param query The user's search query.
     * @param page Page number (1-based, for paginated results).
     * @return A list of matching [ContentItem]s.
     */
    suspend fun search(profile: SourceProfile, query: String, page: Int): List<ContentItem>

    /**
     * Fetch full details for a single content item.
     *
     * @param profile The cached [SourceProfile] describing the source's API.
     * @param url The specific content item's URL.
     * @return The fully populated [ContentItem].
     */
    suspend fun getItem(profile: SourceProfile, url: String): ContentItem

    /**
     * Fetch popular/trending items from a source.
     *
     * @param profile The cached [SourceProfile].
     * @param page Page number.
     * @return A list of [ContentItem]s.
     */
    suspend fun getPopular(profile: SourceProfile, page: Int): List<ContentItem>

    /**
     * Fetch the latest/updated items from a source.
     *
     * @param profile The cached [SourceProfile].
     * @param page Page number.
     * @return A list of [ContentItem]s.
     */
    suspend fun getLatest(profile: SourceProfile, page: Int): List<ContentItem>
}
