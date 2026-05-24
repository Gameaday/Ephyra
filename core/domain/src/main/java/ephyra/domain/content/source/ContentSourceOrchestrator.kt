package ephyra.domain.content.source

import ephyra.domain.content.model.ContentItem

/**
 * Central orchestrator for resolving content from URLs.
 *
 * Implements the "try known → fall back to heuristic → report failure" pipeline.
 * The app core calls this single class; it never touches engines directly.
 *
 * This is the key to swappable sourcing: you can replace the heuristic engine
 * with WASM, AI, or anything else without touching the rest of the app.
 *
 * Usage:
 * ```
 * val results = orchestrator.search("https://mangadex.org", "One Piece", 1)
 * val detail = orchestrator.getItem(profile, "https://mangadex.org/title/...")
 * ```
 */
class ContentSourceOrchestrator(
    private val profileCache: SourceProfileCache,
    private val heuristicEngine: ContentSourceEngine,
    private val knownEngines: Map<String, ContentSourceEngine> = emptyMap(),
) {
    /**
     * Search for content items matching [query] on the source at [baseUrl].
     *
     * Resolution order:
     * 1. Known engine for this URL (hardcoded adapter, if registered)
     * 2. Cached profile + heuristic engine
     * 3. Fresh heuristic discovery + heuristic engine
     */
    suspend fun search(baseUrl: String, query: String, page: Int = 1): List<ContentItem> {
        val profile = resolveProfile(baseUrl)
        val engine = resolveEngine(baseUrl)
        return engine.search(profile, query, page)
    }

    /**
     * Fetch full details for a single content item URL.
     */
    suspend fun getItem(baseUrl: String, itemUrl: String): ContentItem {
        val profile = resolveProfile(baseUrl)
        val engine = resolveEngine(baseUrl)
        return engine.getItem(profile, itemUrl)
    }

    /**
     * Fetch popular/trending items from a source.
     */
    suspend fun getPopular(baseUrl: String, page: Int = 1): List<ContentItem> {
        val profile = resolveProfile(baseUrl)
        val engine = resolveEngine(baseUrl)
        return engine.getPopular(profile, page)
    }

    /**
     * Fetch latest/updated items from a source.
     */
    suspend fun getLatest(baseUrl: String, page: Int = 1): List<ContentItem> {
        val profile = resolveProfile(baseUrl)
        val engine = resolveEngine(baseUrl)
        return engine.getLatest(profile, page)
    }

    /**
     * Force re-discovery of a source (clears cached profile).
     */
    suspend fun rediscover(baseUrl: String): SourceProfile {
        profileCache.invalidate(baseUrl)
        val profile = heuristicEngine.discover(baseUrl)
        profileCache.save(profile)
        return profile
    }

    // ── Private helpers ──────────────────────────────────────────

    private suspend fun resolveProfile(baseUrl: String): SourceProfile {
        // Check cache first
        val cached = profileCache.get(baseUrl)
        if (cached != null) return cached

        // Fresh discovery
        val profile = heuristicEngine.discover(baseUrl)
        profileCache.save(profile)
        return profile
    }

    private fun resolveEngine(baseUrl: String): ContentSourceEngine {
        return knownEngines[normalizeUrl(baseUrl)] ?: heuristicEngine
    }

    private fun normalizeUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .trim()
    }
}
