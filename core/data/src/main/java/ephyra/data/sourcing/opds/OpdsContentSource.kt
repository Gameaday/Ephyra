package ephyra.data.sourcing.opds

import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.UnifiedContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pillar 2: Remote APIs (OPDS & Remote Catalog Client).
 *
 * Implements [UnifiedContentSource] for OPDS 1.2 (Atom XML) and OPDS 2.0 (JSON-LD/HAL),
 * unifying self-hosted catalogs like Jellyfin, Kavita, and Calibre-web under a single contract.
 */
class OpdsContentSource(
    override val id: String,
    override val name: String,
    val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) : UnifiedContentSource {

    override val supportedTypes: Set<ContentType> = setOf(
        ContentType.BOOK,
        ContentType.MANGA,
        ContentType.NOVEL,
    )

    private val xmlDocBuilder by lazy {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Throwable) {
                // Ignore unsupported parser features
            }
        }.newDocumentBuilder()
    }

    override suspend fun getCatalog(page: Int, filter: FilterSet): List<CatalogEntry> = withContext(Dispatchers.IO) {
        val targetUrl = resolveCatalogUrl(page, filter)
        val bodyString = fetchString(targetUrl)

        if (bodyString.trimStart().startsWith("{")) {
            parseOpds2JsonCatalog(bodyString)
        } else {
            parseOpds1XmlCatalog(bodyString)
        }
    }

    override suspend fun getChapterManifest(entryKey: String): List<ChapterInfo> = withContext(Dispatchers.IO) {
        val targetUrl = resolveUrl(entryKey)
        val bodyString = runCatching { fetchString(targetUrl) }.getOrNull()

        if (bodyString != null && (bodyString.contains("<feed") || bodyString.trimStart().startsWith("{"))) {
            // It's a sub-catalog / series feed with multiple chapters or volumes
            val entries = if (bodyString.trimStart().startsWith("{")) {
                parseOpds2JsonCatalog(bodyString)
            } else {
                parseOpds1XmlCatalog(bodyString)
            }
            entries.mapIndexed { index, entry ->
                ChapterInfo(
                    key = entry.url,
                    title = entry.title,
                    number = (index + 1).toDouble(),
                    url = entry.url,
                )
            }
        } else {
            // Single-volume publication
            listOf(
                ChapterInfo(
                    key = entryKey,
                    title = "Complete",
                    number = 1.0,
                    url = entryKey,
                ),
            )
        }
    }

    override suspend fun loadPage(chapterKey: String, pageIndex: Int): ContentPage {
        val pages = loadPages(chapterKey)
        return pages.getOrNull(pageIndex) ?: ContentPage.ImagePage(index = pageIndex)
    }

    override suspend fun loadPages(chapterKey: String): List<ContentPage> = withContext(Dispatchers.IO) {
        val targetUrl = resolveUrl(chapterKey)
        val bodyString = runCatching { fetchString(targetUrl) }.getOrNull()

        if (bodyString != null && bodyString.contains("<entry")) {
            // Detailed entry XML: extract acquisition or image stream links
            val doc = synchronized(xmlDocBuilder) {
                xmlDocBuilder.parse(InputSource(StringReader(bodyString)))
            }
            val links = doc.getElementsByTagName("link")
            val imageLinks = mutableListOf<String>()
            for (i in 0 until links.length) {
                val link = links.item(i) as? Element ?: continue
                val rel = link.getAttribute("rel")
                val href = link.getAttribute("href")
                val type = link.getAttribute("type")
                if (rel.contains("image") || rel.contains("acquisition") || type.startsWith("image/")) {
                    imageLinks.add(resolveUrl(href))
                }
            }
            if (imageLinks.isNotEmpty()) {
                return@withContext imageLinks.mapIndexed { index, url ->
                    ContentPage.ImagePage(index = index, imageUrl = url)
                }
            }
        }

        // Default single page / direct acquisition URL
        listOf(
            ContentPage.ImagePage(
                index = 0,
                imageUrl = targetUrl,
            ),
        )
    }

    private fun resolveCatalogUrl(page: Int, filter: FilterSet): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanQuery = filter.query.trim()
        return if (cleanQuery.isNotEmpty()) {
            "${base}search?q=$cleanQuery&page=$page"
        } else if (page > 1) {
            "$base?page=$page"
        } else {
            base
        }
    }

    private fun resolveUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val baseClean = baseUrl.trimEnd('/')
        val pathClean = path.trimStart('/')
        return "$baseClean/$pathClean"
    }

    private fun fetchString(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("OPDS HTTP error: ${response.code} for $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    internal fun parseOpds1XmlCatalog(xmlString: String): List<CatalogEntry> {
        val doc = synchronized(xmlDocBuilder) {
            xmlDocBuilder.parse(InputSource(StringReader(xmlString)))
        }
        val entries = doc.getElementsByTagName("entry")
        val result = mutableListOf<CatalogEntry>()

        for (i in 0 until entries.length) {
            val entryNode = entries.item(i)
            if (entryNode.nodeType != Node.ELEMENT_NODE) continue
            val entry = entryNode as Element

            val id = entry.getElementsByTagName("id").item(0)?.textContent?.trim() ?: "entry-$i"
            val title = entry.getElementsByTagName("title").item(0)?.textContent?.trim() ?: "Untitled"
            val summary = entry.getElementsByTagName("summary").item(0)?.textContent?.trim()
                ?: entry.getElementsByTagName("content").item(0)?.textContent?.trim()
            val author = entry.getElementsByTagName("author").item(0)?.let { authorNode ->
                (authorNode as? Element)?.getElementsByTagName("name")?.item(0)?.textContent?.trim()
            }

            var coverUrl: String? = null
            var acquisitionUrl: String? = null
            var subfeedUrl: String? = null

            val links = entry.getElementsByTagName("link")
            for (j in 0 until links.length) {
                val link = links.item(j) as? Element ?: continue
                val rel = link.getAttribute("rel")
                val href = link.getAttribute("href")

                when {
                    rel.contains("image") || rel.contains("thumbnail") || rel.contains("cover") -> {
                        if (coverUrl == null) coverUrl = resolveUrl(href)
                    }
                    rel.contains("acquisition") -> {
                        if (acquisitionUrl == null) acquisitionUrl = resolveUrl(href)
                    }
                    rel == "subsection" || rel.contains("crawlable") -> {
                        if (subfeedUrl == null) subfeedUrl = resolveUrl(href)
                    }
                }
            }

            val finalUrl = acquisitionUrl ?: subfeedUrl ?: resolveUrl(id)
            result.add(
                CatalogEntry(
                    key = finalUrl,
                    title = title,
                    url = finalUrl,
                    coverUrl = coverUrl,
                    type = ContentType.BOOK,
                    description = summary,
                    author = author,
                ),
            )
        }

        return result
    }

    internal fun parseOpds2JsonCatalog(jsonString: String): List<CatalogEntry> {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val publications = root["publications"]?.jsonArray ?: return emptyList()

        return publications.mapNotNull { pubElement ->
            val pubObj = pubElement.jsonObject
            val metadata = pubObj["metadata"]?.jsonObject ?: return@mapNotNull null
            val title = metadata["title"]?.jsonPrimitive?.content ?: "Untitled"
            val description = metadata["description"]?.jsonPrimitive?.content
            val author = metadata["author"]?.jsonPrimitive?.content
                ?: metadata["author"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content

            val images = pubObj["images"]?.jsonArray
            val coverUrl = images?.firstOrNull()?.jsonObject?.get("href")?.jsonPrimitive?.content?.let(::resolveUrl)

            val links = pubObj["links"]?.jsonArray
            val acquisitionUrl = links?.firstOrNull { link ->
                link.jsonObject["rel"]?.jsonPrimitive?.content?.contains("acquisition") == true
            }?.jsonObject?.get("href")?.jsonPrimitive?.content?.let(::resolveUrl)

            val url = acquisitionUrl ?: coverUrl ?: baseUrl

            CatalogEntry(
                key = url,
                title = title,
                url = url,
                coverUrl = coverUrl,
                type = ContentType.BOOK,
                description = description,
                author = author,
            )
        }
    }
}
