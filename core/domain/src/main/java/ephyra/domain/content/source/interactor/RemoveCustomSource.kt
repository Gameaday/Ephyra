package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.SourceProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes a custom source (JS scraper, heuristic profile, or repository).
 */
@Singleton
class RemoveCustomSource @Inject constructor(
    private val orchestrator: ContentSourceOrchestrator,
    private val scraperUpdater: ScraperScriptUpdater,
    private val preferenceStore: PreferenceStore,
) {

    /**
     * Removes a custom source completely (scraper script, mappings, and profile).
     */
    suspend fun removeSource(baseUrl: String): Result<Unit> {
        return try {
            val profile = orchestrator.getAllProfiles().firstOrNull { it.baseUrl == baseUrl }
                ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))

            // Remove scraper script if it's a JS scraper
            if (profile.sourceType == ephyra.domain.content.source.SourceType.JS_SCRAPER) {
                profile.scraperFilename?.let { filename ->
                    scraperUpdater.removeScraper(filename)
                }
            }

            // Remove URL-to-scraper mapping
            val normalized = normalizeUrl(baseUrl)
            val mappingKey = "baseUrl_scraper_mapping_$normalized"
            preferenceStore.getString(mappingKey, "").delete()

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

    /**
     * Removes only the scraper mapping for a URL, keeping the heuristic profile.
     */
    suspend fun unlinkScraper(baseUrl: String): Result<SourceProfile> {
        return try {
            val profile = orchestrator.getAllProfiles().firstOrNull { it.baseUrl == baseUrl }
                ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))

            // Remove URL-to-scraper mapping
            val normalized = normalizeUrl(baseUrl)
            val mappingKey = "baseUrl_scraper_mapping_$normalized"
            preferenceStore.getString(mappingKey, "").delete()

            // Update profile to heuristic type
            val updated = profile.copy(
                sourceType = ephyra.domain.content.source.SourceType.HEURISTIC,
                scraperFilename = null,
            )

            orchestrator.setSourceType(baseUrl, ephyra.domain.content.source.SourceType.HEURISTIC, null)

            Result.Success(updated)
        } catch (e: Exception) {
            Result.Error(e)
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
