package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.source.registry.SourceRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes a custom source (heuristic profile or repository).
 */
@Singleton
class RemoveCustomSource @Inject constructor(
    private val orchestrator: ContentSourceOrchestrator,
    private val sourceRegistry: SourceRegistry,
    private val preferenceStore: PreferenceStore,
) {

    /**
     * Removes a custom source completely (its profile and the profiled-domain entry, so it cannot
     * resurrect on next launch).
     */
    suspend fun removeSource(baseUrl: String): Result<Unit> {
        return try {
            val target = normalizeUrl(baseUrl)
            val profile = orchestrator.getAllProfiles().firstOrNull { normalizeUrl(it.baseUrl) == target }
                ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))

            // Clear any scraper mapping left by a build that still had them. Deleting the key is
            // enough — nothing reads it now, but a stale one would resurrect a mapping if the
            // mechanism ever returns.
            val mappingKey = "baseUrl_scraper_mapping_$target"
            preferenceStore.getString(mappingKey, "").delete()

            // Erase the origin through the registry (SRC-011): the registry owns the
            // profiled-domains set, and `unregister` removes every spelling of the
            // origin — and only the origin — so no second spelling can resurrect it.
            sourceRegistry.unregister(baseUrl)

            // Invalidate the cached profile
            orchestrator.invalidateProfile(baseUrl)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Disables a source without removing it (keeps configuration for later re-enabling).
     */
    suspend fun disableSource(baseUrl: String): Result<SourceProfile> {
        return orchestrator.setSourceEnabled(baseUrl, false)
    }

    private fun normalizeUrl(url: String): String {
        return url
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .lowercase()
    }
}
