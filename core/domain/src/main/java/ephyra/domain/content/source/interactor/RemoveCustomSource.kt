package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes a custom source (heuristic profile or repository).
 */
@Singleton
class RemoveCustomSource @Inject constructor(
    private val orchestrator: ContentSourceOrchestrator,
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

            // Remove the domain from the profiled-domains set. Without this the
            // profile cache rebuilds the profile on next launch and the "removed"
            // source resurrects.
            val domainsKey = "profiled_domains_list"
            val domains = preferenceStore.getStringSet(domainsKey, emptySet()).get()
            preferenceStore.getStringSet(domainsKey, emptySet()).set(
                domains.filterNot { normalizeUrl(it) == target }.toSet(),
            )

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
