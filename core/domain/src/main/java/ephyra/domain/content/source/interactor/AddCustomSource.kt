package ephyra.domain.content.source.interactor

import ephyra.core.common.util.Result
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.service.SourceManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds a custom source (JS scraper, heuristic profile, or repository).
 */
@Singleton
class AddCustomSource @Inject constructor(
    private val orchestrator: ContentSourceOrchestrator,
    private val scraperUpdater: ScraperScriptUpdater,
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
) {

    /**
     * Adds a JavaScript scraper from a GitHub URL.
     */
    suspend fun addJsScraper(githubUrl: String, filename: String): Result<SourceProfile> {
        return try {
            // Download the scraper
            scraperUpdater.downloadScraper(githubUrl, filename)

            // Create a basic profile - the orchestrator will discover it on first use
            val baseUrl = extractBaseUrlFromGithub(githubUrl)
            val profile = SourceProfile(
                baseUrl = baseUrl,
                contentType = ephyra.domain.content.model.ContentType.MANGA,
                sourceType = SourceType.JS_SCRAPER,
                enabled = true,
                displayName = filename.removeSuffix(".js").replace("_", " ").capitalize(),
                scraperFilename = filename,
            )

            // Save profile to cache
            orchestrator.discover(baseUrl) // This will save to cache
            orchestrator.setSourceType(baseUrl, SourceType.JS_SCRAPER, filename)

            Result.Success(profile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Imports a local JavaScript scraper script.
     */
    suspend fun importJsScraper(filename: String, scriptContent: String): Result<SourceProfile> {
        return try {
            scraperUpdater.importLocalScraperScript(filename, scriptContent)

            val baseUrl = "custom://${filename.removeSuffix(".js")}"
            val profile = SourceProfile(
                baseUrl = baseUrl,
                contentType = ephyra.domain.content.model.ContentType.MANGA,
                sourceType = SourceType.JS_SCRAPER,
                enabled = true,
                displayName = filename.removeSuffix(".js").replace("_", " ").capitalize(),
                scraperFilename = filename,
            )

            orchestrator.discover(baseUrl)
            orchestrator.setSourceType(baseUrl, SourceType.JS_SCRAPER, filename)

            Result.Success(profile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Adds a heuristic profile for a website (will auto-discover on first use).
     */
    suspend fun addHeuristicProfile(baseUrl: String, displayName: String? = null): Result<SourceProfile> {
        return try {
            val profile = SourceProfile(
                baseUrl = baseUrl,
                contentType = ephyra.domain.content.model.ContentType.MANGA,
                sourceType = SourceType.HEURISTIC,
                enabled = true,
                displayName = displayName ?: baseUrl,
            )

            orchestrator.discover(baseUrl)

            Result.Success(profile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Links a base URL to a specific scraper script.
     */
    suspend fun linkScraperToUrl(baseUrl: String, scraperFilename: String): Result<SourceProfile> {
        return try {
            val normalized = normalizeUrl(baseUrl)
            val profile = orchestrator.getAllProfiles().firstOrNull { it.baseUrl == baseUrl }
                ?: SourceProfile(
                    baseUrl = baseUrl,
                    contentType = ephyra.domain.content.model.ContentType.MANGA,
                    sourceType = SourceType.JS_SCRAPER,
                    enabled = true,
                    displayName = baseUrl,
                    scraperFilename = scraperFilename,
                )

            val updated = profile.copy(
                sourceType = SourceType.JS_SCRAPER,
                scraperFilename = scraperFilename,
                enabled = true,
            )

            // Save the mapping
            val mappingKey = "baseUrl_scraper_mapping_$normalized"
            sourceManager.sourcePreferences.preferenceStore.getString(mappingKey, "").set(scraperFilename)

            orchestrator.setSourceType(baseUrl, SourceType.JS_SCRAPER, scraperFilename)

            Result.Success(updated)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun extractBaseUrlFromGithub(githubUrl: String): String {
        // Try to infer base URL from GitHub repo URL
        // e.g., https://github.com/user/mangadex-scraper -> https://mangadex.org
        val repoName = githubUrl.substringAfterLast("/").removeSuffix(".js").removeSuffix("-scraper")
        return "https://$repoName.com" // Fallback, will be overridden by user mapping
    }

    private fun normalizeUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .trim()
    }
}
