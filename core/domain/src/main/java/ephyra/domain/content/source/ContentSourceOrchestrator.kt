package ephyra.domain.content.source

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.core.common.util.getOrThrow
import ephyra.domain.content.model.ContentItem

/**
 * Central orchestrator for resolving content from URLs, implementing [RemoteSource].
 *
 * Implements the "try known → fall back to heuristic → report failure" pipeline.
 * The app core calls this single class; it never touches engines directly.
 * All return values are explicitly wrapped in [Result] structures for Clean UDF execution.
 */
class ContentSourceOrchestrator(
    private val profileCache: SourceProfileCache,
    private val heuristicEngine: ContentSourceEngine,
    private val scriptEngine: ContentSourceEngine,
    private val preferenceStore: PreferenceStore,
) : RemoteSource {

    override suspend fun discover(baseUrl: String): Result<SourceProfile> {
        return try {
            profileCache.invalidate(baseUrl)
            val engine = resolveEngine(baseUrl)
            val profile = engine.discover(baseUrl)
            profileCache.save(profile)
            Result.Success(profile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun search(baseUrl: String, query: String, page: Int): Result<List<ContentItem>> {
        return try {
            val profile = resolveProfile(baseUrl)
            val engine = resolveEngine(baseUrl)
            val items = engine.search(profile, query, page)
            Result.Success(items)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getItem(baseUrl: String, itemUrl: String): Result<ContentItem> {
        return try {
            val profile = resolveProfile(baseUrl)
            val engine = resolveEngine(baseUrl)
            val item = engine.getItem(profile, itemUrl)
            Result.Success(item)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getPopular(baseUrl: String, page: Int): Result<List<ContentItem>> {
        return try {
            val profile = resolveProfile(baseUrl)
            val engine = resolveEngine(baseUrl)
            val items = engine.getPopular(profile, page)
            Result.Success(items)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getLatest(baseUrl: String, page: Int): Result<List<ContentItem>> {
        return try {
            val profile = resolveProfile(baseUrl)
            val engine = resolveEngine(baseUrl)
            val items = engine.getLatest(profile, page)
            Result.Success(items)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Force re-discovery of a source (clears cached profile).
     * Kept for backward compatibility with existing screen models.
     */
    suspend fun rediscover(baseUrl: String): SourceProfile = discover(baseUrl).getOrThrow()

    // ── Private helpers ──────────────────────────────────────────

    private suspend fun resolveProfile(baseUrl: String): SourceProfile {
        val cached = profileCache.get(baseUrl)
        if (cached != null) return cached

        val engine = resolveEngine(baseUrl)
        val profile = engine.discover(baseUrl)
        profileCache.save(profile)
        return profile
    }

    private suspend fun resolveEngine(baseUrl: String): ContentSourceEngine {
        val normalized = normalizeUrl(baseUrl)
        val mapped = preferenceStore.getString("baseUrl_scraper_mapping_$normalized", "").get()
        return if (mapped.isNotBlank()) {
            scriptEngine
        } else {
            heuristicEngine
        }
    }

    private fun normalizeUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .trim()
    }
}
