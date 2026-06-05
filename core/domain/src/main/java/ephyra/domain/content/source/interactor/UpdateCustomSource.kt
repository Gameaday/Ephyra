package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Updates an existing custom source (JS scraper, heuristic profile, or repository).
 */
@Singleton
class UpdateCustomSource @Inject constructor(
    private val orchestrator: ContentSourceOrchestrator,
    private val scraperUpdater: ScraperScriptUpdater,
    private val preferenceStore: PreferenceStore,
) {

    /**
     * Checks for updates to a JS scraper and applies them if available.
     */
    suspend fun checkAndUpdateScraper(baseUrl: String): Result<SourceProfile> {
        return try {
            val profile = orchestrator.getAllProfiles().firstOrNull { it.baseUrl == baseUrl }
                ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))

            val scraperFilename = profile.scraperFilename
                ?: return Result.Error(IllegalArgumentException("No scraper filename for: $baseUrl"))

            val updated = scraperUpdater.checkForUpdates(scraperFilename)
            val freshProfile = orchestrator.rediscover(baseUrl)

            Result.Success(freshProfile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Updates the scraper script content directly.
     */
    suspend fun updateScraperScript(baseUrl: String, newScriptContent: String): Result<SourceProfile> {
        return try {
            val profile = orchestrator.getAllProfiles().firstOrNull { it.baseUrl == baseUrl }
                ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))

            val scraperFilename = profile.scraperFilename
                ?: return Result.Error(IllegalArgumentException("No scraper filename for: $baseUrl"))

            // Import the new script content (overwrites existing)
            scraperUpdater.importLocalScraperScript(scraperFilename, newScriptContent)

            val freshProfile = orchestrator.rediscover(baseUrl)
            Result.Success(freshProfile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Updates the display name of a source.
     */
    suspend fun updateDisplayName(baseUrl: String, newName: String): Result<SourceProfile> {
        return try {
            val profile = orchestrator.getAllProfiles().firstOrNull { it.baseUrl == baseUrl }
                ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))

            val updated = profile.copy(displayName = newName)
            orchestrator.setSourceType(baseUrl, profile.sourceType, profile.scraperFilename)
            // Note: We'd need a way to persist displayName changes - for now just return updated
            Result.Success(updated)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Renames a scraper script.
     */
    suspend fun renameScraper(baseUrl: String, newFilename: String): Result<SourceProfile> {
        return try {
            val profile = orchestrator.getAllProfiles().firstOrNull { it.baseUrl == baseUrl }
                ?: return Result.Error(IllegalArgumentException("Source not found: $baseUrl"))

            val oldFilename = profile.scraperFilename
                ?: return Result.Error(IllegalArgumentException("No scraper filename for: $baseUrl"))

            val renamed = scraperUpdater.renameScraper(oldFilename, newFilename)
            if (!renamed) {
                return Result.Error(IllegalStateException("Failed to rename scraper"))
            }

            // Update mapping
            val normalized = normalizeUrl(baseUrl)
            val mappingKey = "baseUrl_scraper_mapping_$normalized"
            preferenceStore.getString(mappingKey, "").set(newFilename)

            val updated = profile.copy(scraperFilename = newFilename)
            orchestrator.setSourceType(baseUrl, profile.sourceType, newFilename)

            Result.Success(updated)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Forces re-discovery of a heuristic profile.
     */
    suspend fun forceRediscover(baseUrl: String): Result<SourceProfile> {
        return try {
            val profile = orchestrator.rediscover(baseUrl)
            Result.Success(profile)
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
