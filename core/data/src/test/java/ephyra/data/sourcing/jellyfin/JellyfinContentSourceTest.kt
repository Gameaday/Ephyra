package ephyra.data.sourcing.jellyfin

import ephyra.data.track.jellyfin.JellyfinCredentials
import ephyra.data.track.jellyfin.JellyfinItemsResponse
import ephyra.domain.content.model.FilterSet
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only parsing/mapping tests for [JellyfinContentSource], mirroring
 * [ephyra.data.sourcing.opds.OpdsContentSourceTest]'s approach: exercise the pure mapping
 * helpers against captured Jellyfin JSON payloads rather than mocking a live server. No Android
 * framework classes are touched; an unconfigured fake credential exercises the degraded paths,
 * which return before any network call is built.
 */
class JellyfinContentSourceTest {

    private val serverUrl = "https://jellyfin.example.com"

    /** Fully configured — required for the mapping helpers, irrelevant for the pure mappings. */
    private val configured = object : JellyfinCredentials {
        override fun serverUrl() = serverUrl
        override fun userId() = "user-1"
        override fun serverName() = "Home Server"
        override fun libraryId() = "lib-1"
        override fun accessToken() = "token-1"
        override fun isConfigured() = true
    }

    private val unconfigured = object : JellyfinCredentials {
        override fun serverUrl() = ""
        override fun userId() = ""
        override fun serverName() = ""
        override fun libraryId() = ""
        override fun accessToken() = ""
        override fun isConfigured() = false
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun configuredSource() = JellyfinContentSource(configured, OkHttpClient(), json)

    @Test
    fun `source metadata conforms to contract`() {
        val source = configuredSource()
        assertEquals("jellyfin", source.id)
        assertEquals("Home Server", source.name)
        assertTrue(source.supportedTypes.contains(ephyra.domain.content.model.ContentType.MANGA))
        assertTrue(source.supportedTypes.contains(ephyra.domain.content.model.ContentType.BOOK))
    }

    @Test
    fun `unconfigured server falls back to generic name`() {
        val source = JellyfinContentSource(unconfigured, OkHttpClient(), json)
        assertEquals("Jellyfin", source.name)
    }

    @Test
    fun `toCatalogEntry maps a Jellyfin series payload accurately`() {
        val payload = """
            {
              "Items": [
                {
                  "Id": "42",
                  "Name": "Dungeon Meshi",
                  "Type": "Series",
                  "Overview": "Delicious monsters, delicious meals.",
                  "Genres": ["Fantasy", "Comedy"],
                  "ProductionYear": 2014,
                  "ImageTags": { "Primary": "abc123" },
                  "Studios": [ { "Name": "Ryoko Kui" } ],
                  "RecursiveItemCount": 97
                }
              ],
              "TotalRecordCount": 1
            }
        """.trimIndent()

        val source = configuredSource()
        val response = json.decodeFromString<JellyfinItemsResponse>(payload)
        val entry = with(source) { response.items.single().toCatalogEntry(serverUrl) }

        assertEquals("$serverUrl/Items/42", entry.key)
        assertEquals("$serverUrl/Items/42", entry.url)
        assertEquals("Dungeon Meshi", entry.title)
        assertEquals("Ryoko Kui", entry.author)
        assertEquals("Delicious monsters, delicious meals.", entry.description)
        assertEquals(listOf("Fantasy", "Comedy"), entry.genres)
        assertEquals("$serverUrl/Items/42/Images/Primary?maxWidth=400&quality=90", entry.coverUrl)
    }

    @Test
    fun `toCatalogEntry omits cover when the item has no images`() {
        val payload = """
            {
              "Items": [
                { "Id": "43", "Name": "Bare Series", "Type": "Series" }
              ],
              "TotalRecordCount": 1
            }
        """.trimIndent()

        val source = configuredSource()
        val response = json.decodeFromString<JellyfinItemsResponse>(payload)
        val entry = with(source) { response.items.single().toCatalogEntry(serverUrl) }

        assertNull(entry.coverUrl)
        assertNull(entry.author)
    }

    @Test
    fun `toChapterInfo uses IndexNumber and parses DateCreated`() {
        val payload = """
            {
              "Items": [
                {
                  "Id": "100",
                  "Name": "Volume 1",
                  "IndexNumber": 1,
                  "DateCreated": "2023-05-06T07:08:09.000Z"
                },
                { "Id": "101", "Name": "Volume 2" }
              ],
              "TotalRecordCount": 2
            }
        """.trimIndent()

        val source = configuredSource()
        val response = json.decodeFromString<JellyfinItemsResponse>(payload)

        val first = with(source) { response.items[0].toChapterInfo(serverUrl, fallbackNumber = 1.0) }
        assertEquals(1.0, first.number, 0.0)
        assertEquals("Volume 1", first.title)
        assertEquals("$serverUrl/Items/100", first.key)
        assertTrue(first.dateUpload > 0L)

        val second = with(source) { response.items[1].toChapterInfo(serverUrl, fallbackNumber = 2.0) }
        assertEquals(2.0, second.number, 0.0)
    }

    @Test
    fun `unconfigured server returns empty results without network access`() = runTest {
        val source = JellyfinContentSource(unconfigured, OkHttpClient(), json)

        assertTrue(source.getCatalog(1, FilterSet()).isEmpty())
        assertTrue(source.getChapterManifest("$serverUrl/Items/42").isEmpty())
        assertTrue(source.loadPages("$serverUrl/Items/42").isEmpty())
    }
}
