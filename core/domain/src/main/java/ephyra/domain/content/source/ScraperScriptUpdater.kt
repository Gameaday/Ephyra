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
}
