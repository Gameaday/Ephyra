package ephyra.domain.content.source

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import eu.kanade.tachiyomi.network.NetworkHelper
import ephyra.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device heuristic engine that analyzes DOM layouts to locate media resources,
 * search parameters, covers, descriptions, and sequential units (chapters/episodes) dynamically.
 * Caches discovered rules in a [SourceProfile] to avoid redundant network probes.
 */
@Singleton
class AdaptiveHeuristicEngine @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val networkHelper: NetworkHelper,
    private val profileCache: SourceProfileCache,
) : ContentSourceEngine {

    private val scope = CoroutineScope(ioDispatcher)

    override suspend fun discover(baseUrl: String): SourceProfile = withContext(ioDispatcher) {
        val request = Request.Builder().url(baseUrl).build()
        val response = networkHelper.client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Failed to load base URL: $baseUrl")

        val html = response.body.string()
        val doc = Jsoup.parse(html, baseUrl)

        // Infer content types based on text indicators on the homepage
        val inferredContentType = inferContentType(doc)

        // Dynamic search endpoint heuristics
        val searchPath = inferSearchEndpoint(doc) ?: "/?s={query}"
        val endpoints = mapOf(
            Endpoint.SEARCH to EndpointPattern(pathTemplate = searchPath),
            Endpoint.POPULAR to EndpointPattern(pathTemplate = "/"),
            Endpoint.LATEST to EndpointPattern(pathTemplate = "/"),
        )

        // Heuristically extract standard DOM selectors for item components
        val inferredSelectors = mutableMapOf<DataField, String>()

        // List container heuristics (e.g. divs with cards, grid columns, list wrappers)
        val gridSelector = doc.select(".grid, .list, .manga-list, .cards, .content").firstOrNull()?.tagName() ?: "div"
        inferredSelectors[DataField.ITEM_LIST] = "$gridSelector .item, $gridSelector .card, a.manga-card"
        inferredSelectors[DataField.ITEM_TITLE] = "h3, h2, .title, .name"
        inferredSelectors[DataField.ITEM_URL] = "a[href]"
        inferredSelectors[DataField.ITEM_THUMBNAIL] = "img[src]"

        SourceProfile(
            baseUrl = baseUrl,
            contentType = inferredContentType,
            endpoints = endpoints,
            responseType = ResponseType.HTML,
            pagination = PaginationType.PAGE_BASED,
            selectors = inferredSelectors,
            displayName = "Heuristically Discovered (${doc.title()})",
            verified = true,
        )
    }

    override suspend fun search(profile: SourceProfile, query: String, page: Int): List<ContentItem> = withContext(
        ioDispatcher,
    ) {
        val searchPattern = profile.endpoints[Endpoint.SEARCH]?.pathTemplate ?: "/?s={query}"
        val searchUrl = profile.baseUrl.removeSuffix("/") + searchPattern
            .replace("{query}", query)
            .replace("{page}", page.toString())

        val request = Request.Builder().url(searchUrl).build()
        val response = try {
            networkHelper.client.newCall(request).execute()
        } catch (e: Exception) {
            // One-off network/connection issue — ignore re-discovery
            return@withContext emptyList()
        }

        if (!response.isSuccessful) return@withContext emptyList()

        val doc = Jsoup.parse(response.body.string(), profile.baseUrl)
        val itemSelector = profile.selectors?.get(DataField.ITEM_LIST) ?: "a.manga-card"

        val elements = doc.select(itemSelector)
        if (elements.isEmpty() && doc.select("body").isNotEmpty()) {
            // Intelligent layout/structure failure discovery — trigger silent background discovery
            scope.launch {
                try {
                    val freshProfile = discover(profile.baseUrl)
                    profileCache.save(freshProfile)
                } catch (e: Exception) {
                    // Ignore background discovery exceptions
                }
            }
        }

        elements.map { element ->
            val title = element.select(profile.selectors?.get(DataField.ITEM_TITLE) ?: ".title").text()
            val urlElement = element.select("a").firstOrNull() ?: element
            val url = urlElement.absUrl("href")
            val thumbnail = element.select("img").firstOrNull()?.absUrl("src")

            ContentItem(
                id = -1L,
                sourceId = profile.baseUrl.hashCode().toLong(),
                url = url,
                title = title.ifBlank { "Untitled Content" },
                author = null,
                artist = null,
                description = null,
                genres = emptyList(),
                status = ContentStatus.Unknown,
                thumbnailUrl = thumbnail,
                contentType = profile.contentType,
                initialized = false,
            )
        }
    }

    override suspend fun getItem(profile: SourceProfile, url: String): ContentItem = withContext(ioDispatcher) {
        val request = Request.Builder().url(url).build()
        val response = try {
            networkHelper.client.newCall(request).execute()
        } catch (e: Exception) {
            throw IOException("Network error: ${e.message}", e)
        }
        if (!response.isSuccessful) throw IOException("Failed to load details: $url")

        val doc = Jsoup.parse(response.body.string(), url)

        // Semantic heuristics to extract details
        val title = doc.select("h1, .manga-title, .title").text().ifBlank { doc.title() }
        val description = doc.select(".description, #description, .summary, p.entry-summary").text()
        val author = doc.select(".author, a[href*=author]").firstOrNull()?.text()
        val cover = doc.select(".cover img, img.manga-cover, img.thumbnail").firstOrNull()?.absUrl("src")

        // If core details look empty/unparsed, trigger background recovery
        if (title.isBlank() && doc.select("body").isNotEmpty()) {
            scope.launch {
                try {
                    val freshProfile = discover(profile.baseUrl)
                    profileCache.save(freshProfile)
                } catch (e: Exception) {
                    // Ignore background discovery exceptions
                }
            }
        }

        ContentItem(
            id = -1L,
            sourceId = profile.baseUrl.hashCode().toLong(),
            url = url,
            title = title,
            author = author,
            artist = null,
            description = description,
            genres = emptyList(),
            status = ContentStatus.Ongoing,
            thumbnailUrl = cover,
            contentType = profile.contentType,
            initialized = true,
        )
    }

    override suspend fun getPopular(profile: SourceProfile, page: Int): List<ContentItem> {
        return search(profile, "", page)
    }

    override suspend fun getLatest(profile: SourceProfile, page: Int): List<ContentItem> {
        return search(profile, "", page)
    }

    private fun inferContentType(doc: Document): ContentType {
        val pageText = doc.text().lowercase()
        return when {
            pageText.contains("novel") -> ContentType.NOVEL
            pageText.contains("book") -> ContentType.BOOK
            pageText.contains("anime") || pageText.contains("video") -> ContentType.ANIME
            pageText.contains("audiobook") -> ContentType.AUDIO
            else -> ContentType.MANGA
        }
    }

    private fun inferSearchEndpoint(doc: Document): String? {
        val form = doc.select("form[action*=search], form:has(input[name=s]), form:has(input[name=q])").firstOrNull()
        if (form != null) {
            val action = form.attr("action")
            val inputName = form.select("input[name]").firstOrNull()?.attr("name") ?: "s"
            return "$action?$inputName={query}"
        }
        return null
    }
}
