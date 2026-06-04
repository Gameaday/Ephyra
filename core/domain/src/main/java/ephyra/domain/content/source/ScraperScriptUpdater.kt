package ephyra.domain.content.source

import java.io.File

/**
 * Domain-facing contract for managing sandboxed scraper scripts.
 */
interface ScraperScriptUpdater {
    fun importLocalScraperScript(filename: String, scriptContent: String): File

    suspend fun downloadScraper(githubUrl: String, filename: String): File

    suspend fun checkForUpdates(filename: String): Boolean

    fun getScraperScript(filename: String): String?

    fun listScrapers(): List<String>

    /**
     * Removes a scraper script from the sandbox and cleans up associated metadata.
     * @param filename The scraper script filename to remove.
     * @return true if the scraper was removed, false if it didn't exist.
     */
    fun removeScraper(filename: String): Boolean

    /**
     * Renames a scraper script and updates all associated mappings.
     * @param oldName The current filename.
     * @param newName The new filename.
     * @return true if renamed successfully, false if old doesn't exist or new already exists.
     */
    fun renameScraper(oldName: String, newName: String): Boolean

    /**
     * Gets metadata for a scraper script.
     * @param filename The scraper script filename.
     * @return ScraperMetadata if found, null otherwise.
     */
    fun getScraperMetadata(filename: String): ScraperMetadata?

    /**
     * Exports the full script content for backup/sharing.
     * @param filename The scraper script filename.
     * @return The script content, or null if not found.
     */
    fun exportScraper(filename: String): String?
}

/**
 * Metadata for a sandboxed scraper script.
 */
data class ScraperMetadata(
    val filename: String,
    val sourceUrl: String?, // GitHub URL for auto-updates, null if locally imported
    val lastUpdated: Long, // Timestamp of last download/update
    val lastChecked: Long, // Timestamp of last update check
    val hasUpdates: Boolean, // Whether an update is available
    val contentHash: String, // SHA-256 hash of script content for change detection
    val version: String? = null, // Optional version string from script
    val description: String? = null, // Optional description from script
)
