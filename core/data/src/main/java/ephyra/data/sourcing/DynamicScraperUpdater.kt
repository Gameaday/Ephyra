package ephyra.data.sourcing

import android.content.Context
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.content.source.ScraperMetadata
import ephyra.domain.content.source.ScraperScriptUpdater
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads, verifies, and updates dynamic scraper scripts from user-supplied GitHub URLs.
 * Integrates directly with our Play Store-compliant sandboxed JavaScript engine.
 */
@Singleton
class DynamicScraperUpdater @Inject constructor(
    private val context: Context,
    private val networkHelper: NetworkHelper,
    private val preferenceStore: PreferenceStore,
    private val json: Json,
) : ScraperScriptUpdater {
    private val sandboxDir = File(context.filesDir, "scraper_sandbox").apply {
        if (!exists()) mkdirs()
    }

    private val metadataKeyPrefix = "scraper_meta_"

    init {
        try {
            val assetManager = context.assets
            val filename = "mangadex_scraper.js"
            val targetFile = File(sandboxDir, filename)
            if (!targetFile.exists()) {
                assetManager.open("scrapers/$filename").use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val content = targetFile.readText()
                saveMetadata(
                    ScraperMetadata(
                        filename = filename,
                        sourceUrl = "https://github.com/Gameaday/Ephyra/blob/" +
                            "main/app/src/main/assets/scrapers/mangadex_scraper.js",
                        lastUpdated = System.currentTimeMillis(),
                        lastChecked = System.currentTimeMillis(),
                        hasUpdates = false,
                        contentHash = computeHash(content),
                        version = extractVersion(content),
                        description = extractDescription(content),
                    ),
                )
            }
        } catch (e: Exception) {
            // Ignore asset extraction failures (e.g. during headless unit tests)
        }
    }

    /**
     * Natively imports a local script from device storage into the secure sandbox.
     * Complies fully with Play Store security guidelines for sandboxed script execution.
     */
    override fun importLocalScraperScript(filename: String, scriptContent: String): File {
        if (scriptContent.isBlank()) {
            throw IllegalArgumentException("Script content cannot be empty")
        }
        val targetFile = File(sandboxDir, filename)
        targetFile.writeText(scriptContent)

        // Mark as locally imported (empty source URL) and save metadata
        preferenceStore.getString("scraper_url_$filename", "").delete()
        saveMetadata(
            ScraperMetadata(
                filename = filename,
                sourceUrl = null,
                lastUpdated = System.currentTimeMillis(),
                lastChecked = 0,
                hasUpdates = false,
                contentHash = computeHash(scriptContent),
                version = extractVersion(scriptContent),
                description = extractDescription(scriptContent),
            ),
        )
        return targetFile
    }

    /**
     * Downloads a raw JS scraper script from GitHub and saves it locally in the sandbox.
     */
    override suspend fun downloadScraper(githubUrl: String, filename: String): File {
        val rawUrl = convertToRawUrl(githubUrl)
        val request = Request.Builder().url(rawUrl).build()
        val response = networkHelper.client.newCall(request).awaitSuccess()

        val scriptContent = response.body.string()
        if (scriptContent.isBlank()) {
            throw Exception("Downloaded script is empty")
        }

        val targetFile = File(sandboxDir, filename)
        targetFile.writeText(scriptContent)

        // Cache the GitHub URL for future checks
        preferenceStore.getString("scraper_url_$filename", "").set(githubUrl)
        saveMetadata(
            ScraperMetadata(
                filename = filename,
                sourceUrl = githubUrl,
                lastUpdated = System.currentTimeMillis(),
                lastChecked = System.currentTimeMillis(),
                hasUpdates = false,
                contentHash = computeHash(scriptContent),
                version = extractVersion(scriptContent),
                description = extractDescription(scriptContent),
            ),
        )

        return targetFile
    }

    /**
     * Checks for script updates on GitHub and applies them if there are changes.
     */
    override suspend fun checkForUpdates(filename: String): Boolean {
        val sourceUrl = preferenceStore.getString("scraper_url_$filename", "").get()
        if (sourceUrl.isBlank()) return false

        try {
            val rawUrl = convertToRawUrl(sourceUrl)
            val request = Request.Builder().url(rawUrl).build()
            val response = networkHelper.client.newCall(request).awaitSuccess()
            val newContent = response.body.string()

            if (newContent.isBlank()) return false

            val currentHash = getMetadata(filename)?.contentHash
            val newHash = computeHash(newContent)

            if (currentHash != newHash) {
                val targetFile = File(sandboxDir, filename)
                targetFile.writeText(newContent)

                val metadata = getMetadata(filename)
                saveMetadata(
                    metadata?.copy(
                        lastUpdated = System.currentTimeMillis(),
                        lastChecked = System.currentTimeMillis(),
                        hasUpdates = false,
                        contentHash = newHash,
                        version = extractVersion(newContent),
                        description = extractDescription(newContent),
                    ) ?: ScraperMetadata(
                        filename = filename,
                        sourceUrl = sourceUrl,
                        lastUpdated = System.currentTimeMillis(),
                        lastChecked = System.currentTimeMillis(),
                        hasUpdates = false,
                        contentHash = newHash,
                        version = extractVersion(newContent),
                        description = extractDescription(newContent),
                    ),
                )
                return true
            } else {
                // Update last checked timestamp
                val metadata = getMetadata(filename)
                metadata?.let { m ->
                    saveMetadata(m.copy(lastChecked = System.currentTimeMillis(), hasUpdates = false))
                }
                return false
            }
        } catch (e: Exception) {
            // Log warning or record exception
            return false
        }
    }

    /**
     * Reads a scraper script from the sandbox local storage.
     */
    override fun getScraperScript(filename: String): String? {
        val file = File(sandboxDir, filename)
        return if (file.exists()) file.readText() else null
    }

    /**
     * Lists all scraper filenames stored in the sandbox.
     */
    override fun listScrapers(): List<String> {
        val files = sandboxDir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.name.endsWith(".js") }.map { it.name }.sorted()
    }

    /**
     * Removes a scraper script from the sandbox and cleans up associated metadata.
     */
    override fun removeScraper(filename: String): Boolean {
        val file = File(sandboxDir, filename)
        val deleted = if (file.exists()) file.delete() else false

        if (deleted) {
            preferenceStore.getString("scraper_url_$filename", "").delete()
            preferenceStore.getString("$metadataKeyPrefix$filename", "").delete()
        }
        return deleted
    }

    /**
     * Renames a scraper script and updates all associated mappings.
     */
    override fun renameScraper(oldName: String, newName: String): Boolean {
        val oldFile = File(sandboxDir, oldName)
        val newFile = File(sandboxDir, newName)

        if (!oldFile.exists() || newFile.exists()) return false

        val renamed = oldFile.renameTo(newFile)
        if (renamed) {
            // Update URL mapping
            val sourceUrl = preferenceStore.getString("scraper_url_$oldName", "").getSync()
            preferenceStore.getString("scraper_url_$oldName", "").delete()
            if (sourceUrl.isNotBlank()) {
                preferenceStore.getString("scraper_url_$newName", "").set(sourceUrl)
            }

            // Update metadata
            val metadata = getMetadata(oldName)
            preferenceStore.getString("$metadataKeyPrefix$oldName", "").delete()
            metadata?.let { m ->
                saveMetadata(m.copy(filename = newName))
            }
        }
        return renamed
    }

    /**
     * Gets metadata for a scraper script.
     */
    override fun getScraperMetadata(filename: String): ScraperMetadata? {
        return getMetadata(filename)
    }

    /**
     * Exports the full script content for backup/sharing.
     */
    override fun exportScraper(filename: String): String? {
        return getScraperScript(filename)
    }

    private fun saveMetadata(metadata: ScraperMetadata) {
        preferenceStore.getString("$metadataKeyPrefix${metadata.filename}", "")
            .set(json.encodeToString(metadata))
    }

    private fun getMetadata(filename: String): ScraperMetadata? {
        val jsonStr = preferenceStore.getString("$metadataKeyPrefix$filename", "").getSync()
        return if (jsonStr.isNotBlank()) {
            try {
                json.decodeFromString<ScraperMetadata>(jsonStr)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun computeHash(content: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(content.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            content.hashCode().toString()
        }
    }

    private fun extractVersion(script: String): String? {
        // Try to extract version from common patterns like:
        // // @version 1.0.0
        // const VERSION = "1.0.0"
        // version: "1.0.0"
        val patterns = listOf(
            """@version\s+([^\s\n]+)""".toRegex(),
            """version\s*[:=]\s*["']([^"']+)["']""".toRegex(),
            """VERSION\s*=\s*["']([^"']+)["']""".toRegex(),
        )
        for (pattern in patterns) {
            pattern.find(script)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    private fun extractDescription(script: String): String? {
        // Try to extract description from common patterns like:
        // // @description ...
        // // @name ...
        val patterns = listOf(
            """@description\s+([^\n]+)""".toRegex(),
            """@name\s+([^\n]+)""".toRegex(),
        )
        for (pattern in patterns) {
            pattern.find(script)?.groupValues?.getOrNull(1)?.let { return it.trim() }
        }
        return null
    }

    private fun convertToRawUrl(url: String): String {
        return if (url.contains("github.com") && !url.contains("raw.githubusercontent.com")) {
            url
                .replace("github.com", "raw.githubusercontent.com")
                .replace("/blob/", "/")
        } else {
            url
        }
    }
}
