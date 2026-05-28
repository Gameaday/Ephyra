package ephyra.data.sourcing

import ephyra.core.common.di.IoDispatcher
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceEngine
import ephyra.domain.content.source.PaginationType
import ephyra.domain.content.source.ResponseType
import ephyra.domain.content.source.SourceProfile
import ephyra.source.api.ScriptableSourceEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Store-compliant scriptable content source engine.
 * Leverages the sandboxed QuickJS JavaScript engine to execute dynamically
 * loaded scraper scripts, completely isolating content resolution logic.
 */
@Singleton
class ScriptableContentSourceEngine @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val scraperUpdater: DynamicScraperUpdater,
    private val scriptEngine: ScriptableSourceEngine,
    private val preferenceStore: PreferenceStore,
    private val json: Json,
) : ContentSourceEngine {

    override suspend fun discover(baseUrl: String): SourceProfile = withContext(ioDispatcher) {
        val scriptName = getScraperNameForUrl(baseUrl)
            ?: throw IllegalArgumentException("No scraper script configured for $baseUrl")
        val scriptContent = scraperUpdater.getScraperScript(scriptName)
            ?: throw IllegalArgumentException("Scraper script $scriptName not found")

        val payload = json.encodeToString(baseUrl)
        val profileDto = try {
            val resultJson = scriptEngine.executeScraper(scriptContent, "discover", payload)
            json.decodeFromString<ScraperProfileDto>(resultJson)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "QuickJS Discovery execution failed for $baseUrl: ${e.localizedMessage}\n${e.stackTraceToString()}"
            }
            ScraperProfileDto(contentType = "UNKNOWN", displayName = "Failed Scraper ($baseUrl)")
        }

        SourceProfile(
            baseUrl = baseUrl,
            contentType = try {
                ContentType.valueOf(profileDto.contentType)
            } catch (e: Exception) {
                ContentType.UNKNOWN
            },
            displayName = profileDto.displayName ?: "QuickJS Scraper ($baseUrl)",
            verified = false,
            pagination = PaginationType.PAGE_BASED,
            responseType = ResponseType.JSON,
        )
    }

    override suspend fun search(profile: SourceProfile, query: String, page: Int): List<ContentItem> = withContext(
        ioDispatcher,
    ) {
        val scriptName = getScraperNameForUrl(profile.baseUrl) ?: return@withContext emptyList()
        val scriptContent = scraperUpdater.getScraperScript(scriptName) ?: return@withContext emptyList()

        val payloadDto = ScraperSearchPayload(query = query, page = page)
        val payload = json.encodeToString(payloadDto)

        val resultJson = try {
            scriptEngine.executeScraper(scriptContent, "search", payload)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        val items = json.decodeFromString<List<ScraperContentItemDto>>(resultJson)
        items.map { it.toDomain(profile.baseUrl.hashCode().toLong()) }
    }

    override suspend fun getItem(profile: SourceProfile, url: String): ContentItem = withContext(ioDispatcher) {
        val scriptName = getScraperNameForUrl(profile.baseUrl)
            ?: throw IllegalArgumentException("No scraper script configured for ${profile.baseUrl}")
        val scriptContent = scraperUpdater.getScraperScript(scriptName)
            ?: throw IllegalArgumentException("Scraper script $scriptName not found")

        val itemDto = try {
            val resultJson = scriptEngine.executeScraper(scriptContent, "getItem", url)
            json.decodeFromString<ScraperContentItemDto>(resultJson)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "QuickJS getItem execution failed for $url: ${e.localizedMessage}\n${e.stackTraceToString()}"
            }
            throw IllegalArgumentException("Scraper failed to resolve content at $url: ${e.localizedMessage}", e)
        }
        itemDto.toDomain(profile.baseUrl.hashCode().toLong())
    }

    override suspend fun getPopular(profile: SourceProfile, page: Int): List<ContentItem> {
        return search(profile, "", page)
    }

    override suspend fun getLatest(profile: SourceProfile, page: Int): List<ContentItem> {
        return search(profile, "", page)
    }

    private suspend fun getScraperNameForUrl(baseUrl: String): String? {
        val normalized = normalizeUrl(baseUrl)
        val mapped = preferenceStore.getString("baseUrl_scraper_mapping_$normalized", "").get()
        if (mapped.isNotBlank()) return mapped

        // Fallback: automatic mapping based on domain name
        // e.g. "https://mangadex.org" -> "mangadex_scraper.js"
        val domain = normalized.substringBefore(".").ifBlank { "custom" }
        return "${domain}_scraper.js"
    }

    private fun normalizeUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .trim()
    }
}

@Serializable
internal data class ScraperSearchPayload(
    val query: String,
    val page: Int,
)

@Serializable
internal data class ScraperProfileDto(
    val contentType: String,
    val displayName: String? = null,
)

@Serializable
internal data class ScraperContentItemDto(
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val thumbnailUrl: String? = null,
    val contentType: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun toDomain(sourceId: Long): ContentItem {
        return ContentItem(
            id = -1L,
            sourceId = sourceId,
            url = url,
            title = title,
            author = author,
            artist = artist,
            description = description,
            genres = genres,
            status = ContentStatus.fromString(status ?: "Unknown"),
            thumbnailUrl = thumbnailUrl,
            contentType = try {
                ContentType.valueOf(contentType ?: "MANGA")
            } catch (e: Exception) {
                ContentType.MANGA
            },
            metadata = metadata,
            initialized = author != null || description != null,
        )
    }
}
