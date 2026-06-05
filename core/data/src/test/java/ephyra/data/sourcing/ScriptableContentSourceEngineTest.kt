package ephyra.data.sourcing

import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.SourceProfile
import ephyra.source.api.ScriptableSourceEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScriptableContentSourceEngineTest {

    private val scraperUpdater = mockk<DynamicScraperUpdater>()
    private val scriptEngine = mockk<ScriptableSourceEngine>()
    private val preferenceStore = mockk<PreferenceStore>()
    private val json = Json { ignoreUnknownKeys = true }

    private val engine = ScriptableContentSourceEngine(
        ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        scraperUpdater = scraperUpdater,
        scriptEngine = scriptEngine,
        preferenceStore = preferenceStore,
        json = json,
    )

    @Test
    fun `discover resolves mangadex scraper and parses profile successfully`() = runBlocking {
        val baseUrl = "https://mangadex.org"

        // Mock preference mapping
        val mockPref = mockk<ephyra.core.common.preference.Preference<String>>()
        every { preferenceStore.getString("baseUrl_scraper_mapping_mangadex.org", "") } returns mockPref
        coEvery { mockPref.get() } returns ""

        // Mock script loading
        coEvery { scraperUpdater.getScraperScript("mangadex_scraper.js") } returns "mock script content"

        // Mock execution
        val mockProfileJson = """{"contentType": "MANGA", "displayName": "MangaDex"}"""
        coEvery { scriptEngine.executeScraper(any(), "discover", any()) } returns mockProfileJson

        val profile = engine.discover(baseUrl)

        assertNotNull(profile)
        assertEquals(baseUrl, profile.baseUrl)
        assertEquals(ContentType.MANGA, profile.contentType)
        assertEquals("MangaDex", profile.displayName)
    }

    @Test
    fun `search format payloads and maps items successfully`() = runBlocking {
        val profile = SourceProfile(
            baseUrl = "https://mangadex.org",
            contentType = ContentType.MANGA,
            displayName = "MangaDex",
        )

        // Mock preference mapping
        val mockPref = mockk<ephyra.core.common.preference.Preference<String>>()
        every { preferenceStore.getString("baseUrl_scraper_mapping_mangadex.org", "") } returns mockPref
        coEvery { mockPref.get() } returns ""

        // Mock script loading
        coEvery { scraperUpdater.getScraperScript("mangadex_scraper.js") } returns "mock script content"

        // Mock execution
        val mockItemsJson = """
            [
                {
                    "url": "https://mangadex.org/title/manga1",
                    "title": "One Piece",
                    "thumbnailUrl": "https://uploads.mangadex.org/covers/manga1/cover.jpg",
                    "status": "Ongoing",
                    "contentType": "MANGA"
                }
            ]
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "search", any()) } returns mockItemsJson

        val items = engine.search(profile, "One Piece", 1)

        assertEquals(1, items.size)
        assertEquals("One Piece", items[0].title)
        assertEquals("https://mangadex.org/title/manga1", items[0].url)
        assertEquals(ContentStatus.Ongoing, items[0].status)
    }

    @Test
    fun `getItem maps details successfully`() = runBlocking {
        val profile = SourceProfile(
            baseUrl = "https://mangadex.org",
            contentType = ContentType.MANGA,
            displayName = "MangaDex",
        )

        // Mock preference mapping
        val mockPref = mockk<ephyra.core.common.preference.Preference<String>>()
        every { preferenceStore.getString("baseUrl_scraper_mapping_mangadex.org", "") } returns mockPref
        coEvery { mockPref.get() } returns ""

        // Mock script loading
        coEvery { scraperUpdater.getScraperScript("mangadex_scraper.js") } returns "mock script content"

        // Mock execution
        val mockItemJson = """
            {
                "url": "https://mangadex.org/title/manga1",
                "title": "One Piece",
                "description": "Luffy sets sail to find the One Piece.",
                "thumbnailUrl": "https://uploads.mangadex.org/covers/manga1/cover.jpg",
                "status": "Ongoing",
                "contentType": "MANGA",
                "author": "Eiichiro Oda"
            }
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "getItem", any()) } returns mockItemJson

        val item = engine.getItem(profile, "https://mangadex.org/title/manga1")

        assertNotNull(item)
        assertEquals("One Piece", item.title)
        assertEquals("Eiichiro Oda", item.author)
        assertEquals("Luffy sets sail to find the One Piece.", item.description)
        assertEquals(ContentStatus.Ongoing, item.status)
    }
}
