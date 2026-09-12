package ephyra.data.sourcing

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit
import ephyra.domain.content.source.AdaptiveHeuristicEngine
import ephyra.domain.content.source.ContentSourceEngine
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.DataField
import ephyra.domain.content.source.Endpoint
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.content.source.SourceType
import ephyra.source.api.ScriptableSourceEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * End-to-end multi-method integration tests proving reliability across all 4 sourcing methods:
 *
 * Method 1: Standalone [ParsedHttpSource] transpiled JS scrapers (e.g. MangaDex, ReadManga)
 * Method 2: Multi-source theme scrapers with constructor delegation (e.g. MangaThemesia, Madara)
 * Method 3: Autonomous DOM heuristic discovery without extensions ([AdaptiveHeuristicEngine])
 * Method 4: Unified self-healing fallback ladder ([ContentSourceOrchestrator])
 */
class MultiMethodSourceIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun findTranspilerJs(): File {
        var currentDir: File? = File(".").canonicalFile
        while (currentDir != null) {
            val candidate = File(currentDir, "app/src/main/assets/transpiler.js")
            if (candidate.exists()) return candidate
            currentDir = currentDir.parentFile
        }
        throw IllegalStateException("transpiler.js asset file not found in parent hierarchy")
    }

    private fun transpileWithNode(kotlinSource: String): String {
        val transpilerFile = findTranspilerJs()
        val tempJsFile = File.createTempFile("test_transpile", ".js")
        try {
            val runnerScript = """
                const fs = require('fs');
                const transpilerCode = fs.readFileSync(${json.encodeToString(transpilerFile.absolutePath)}, 'utf8');
                const fn = new Function('module', 'exports', transpilerCode + '\nreturn transpile;');
                const transpile = fn({}, {});
                const result = transpile(${json.encodeToString(kotlinSource)});
                process.stdout.write(result);
            """.trimIndent()
            tempJsFile.writeText(runnerScript)

            val process = ProcessBuilder("node", tempJsFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IllegalStateException("Node transpiler failed with exit code $exitCode:\n$output")
            }
            return output
        } finally {
            tempJsFile.delete()
        }
    }

    private fun createMockPreferenceStore(): PreferenceStore {
        val store = mockk<PreferenceStore>(relaxed = true)
        val stringPref = mockk<Preference<String>>(relaxed = true)
        val longPref = mockk<Preference<Long>>(relaxed = true)
        val boolPref = mockk<Preference<Boolean>>(relaxed = true)
        val setPref = mockk<Preference<Set<String>>>(relaxed = true)

        every { store.getString(any(), any()) } returns stringPref
        every { store.getLong(any(), any()) } returns longPref
        every { store.getBoolean(any(), any()) } returns boolPref
        every { store.getStringSet(any(), any()) } returns setPref

        coEvery { stringPref.get() } returns ""
        coEvery { longPref.get() } returns 0L
        coEvery { boolPref.get() } returns true
        coEvery { setPref.get() } returns emptySet()

        return store
    }

    // =========================================================================
    // Method 1: Standalone ParsedHttpSource Transpiled JS Scrapers
    // =========================================================================

    @Test
    fun `method1 - standalone ParsedHttpSource transpilation and end-to-end mapping for MangaDex`() = runBlocking {
        val kotlinSource = """
            package eu.kanade.tachiyomi.extension.en.mangadex

            import eu.kanade.tachiyomi.source.model.SManga
            import eu.kanade.tachiyomi.source.model.SChapter
            import eu.kanade.tachiyomi.source.model.Page
            import eu.kanade.tachiyomi.source.online.ParsedHttpSource
            import org.jsoup.nodes.Document
            import org.jsoup.nodes.Element

            class MangaDex : ParsedHttpSource() {
                override val name = "MangaDex"
                override val baseUrl = "https://mangadex.org"
                override val lang = "en"

                override fun popularMangaSelector() = "div.manga-card"
                override fun popularMangaFromElement(element: Element): SManga {
                    val manga = SManga.create()
                    manga.title = element.select("h2.title").text()
                    manga.url = element.select("a").attr("href")
                    manga.thumbnail_url = element.select("img").attr("src")
                    return manga
                }

                override fun searchMangaSelector() = "div.search-card"
                override fun searchMangaFromElement(element: Element): SManga {
                    val manga = SManga.create()
                    manga.title = element.select("h3").text()
                    manga.url = element.select("a").attr("href")
                    return manga
                }

                override fun chapterListSelector() = "div.chapter-row"
                override fun chapterFromElement(element: Element): SChapter {
                    val chapter = SChapter.create()
                    chapter.name = element.select(".ch-title").text()
                    chapter.url = element.select("a").attr("href")
                    return chapter
                }

                override fun pageListParse(document: Document): List<Page> {
                    return document.select("div.reader-page img").mapIndexed { i, img ->
                        Page(i, "", img.attr("src"))
                    }
                }
            }
        """.trimIndent()

        // 1. Prove transpilation generates valid, well-formed JavaScript contract
        val transpiledJs = transpileWithNode(kotlinSource)
        assertTrue(transpiledJs.contains("function discover("))
        assertTrue(transpiledJs.contains("function search("))
        assertTrue(transpiledJs.contains("function getItem("))
        assertTrue(transpiledJs.contains("function getChapters("))
        assertTrue(transpiledJs.contains("function getPages("))
        assertTrue(transpiledJs.contains("baseUrl = \"https://mangadex.org\""))

        // 2. Test ScriptableContentSourceEngine execution and model conversion
        val scraperUpdater = mockk<DynamicScraperUpdater>()
        val scriptEngine = mockk<ScriptableSourceEngine>()
        val prefStore = createMockPreferenceStore()
        val engine = ScriptableContentSourceEngine(
            ioDispatcher = Dispatchers.Unconfined,
            scraperUpdater = scraperUpdater,
            scriptEngine = scriptEngine,
            preferenceStore = prefStore,
            json = json,
        )

        coEvery { scraperUpdater.getScraperScript(any()) } returns transpiledJs
        val mockSearchResults = """
            [
                {
                    "url": "https://mangadex.org/title/one-piece",
                    "title": "One Piece",
                    "thumbnailUrl": "https://mangadex.org/covers/op.jpg",
                    "status": "Ongoing",
                    "contentType": "MANGA"
                }
            ]
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "search", any()) } returns mockSearchResults

        val profile = SourceProfile(
            baseUrl = "https://mangadex.org",
            contentType = ContentType.MANGA,
            displayName = "MangaDex",
            sourceType = SourceType.JS_SCRAPER,
        )

        val results = engine.search(profile, "One Piece", 1)
        assertEquals(1, results.size)
        assertEquals("One Piece", results[0].title)
        assertEquals("https://mangadex.org/title/one-piece", results[0].url)
        assertEquals(ContentStatus.Ongoing, results[0].status)
    }

    @Test
    fun `method1 - standalone ParsedHttpSource with distinct CSS selectors for ReadManga`() = runBlocking {
        val kotlinSource = """
            package eu.kanade.tachiyomi.extension.en.readmanga

            import eu.kanade.tachiyomi.source.online.ParsedHttpSource

            class ReadManga : ParsedHttpSource() {
                override val name = "ReadManga"
                override val baseUrl = "https://readmanga.app"
                override val lang = "en"

                override fun popularMangaSelector() = "div.tile"
                override fun searchMangaSelector() = "div.tile"
                override fun chapterListSelector() = "table.table-hover tr.item"
            }
        """.trimIndent()

        val transpiledJs = transpileWithNode(kotlinSource)
        assertTrue(transpiledJs.contains("baseUrl = \"https://readmanga.app\""))
        assertTrue(transpiledJs.contains("div.tile"))
        assertTrue(transpiledJs.contains("table.table-hover tr.item"))

        val scraperUpdater = mockk<DynamicScraperUpdater>()
        val scriptEngine = mockk<ScriptableSourceEngine>()
        val prefStore = createMockPreferenceStore()
        val engine = ScriptableContentSourceEngine(
            ioDispatcher = Dispatchers.Unconfined,
            scraperUpdater = scraperUpdater,
            scriptEngine = scriptEngine,
            preferenceStore = prefStore,
            json = json,
        )

        coEvery { scraperUpdater.getScraperScript(any()) } returns transpiledJs
        val mockChaptersResult = """
            [
                {
                    "url": "https://readmanga.app/ch/1",
                    "title": "Chapter 1: The Beginning",
                    "number": 1.0,
                    "dateUpload": 1700000000000
                },
                {
                    "url": "https://readmanga.app/ch/2",
                    "title": "Chapter 2: The Journey",
                    "number": 2.0,
                    "dateUpload": 1700086400000
                }
            ]
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "getChapters", any()) } returns mockChaptersResult

        val profile = SourceProfile(
            baseUrl = "https://readmanga.app",
            contentType = ContentType.MANGA,
            displayName = "ReadManga",
            sourceType = SourceType.JS_SCRAPER,
        )

        val units = engine.getChapters(profile, "https://readmanga.app/manga/sample")
        assertEquals(2, units.size)
        assertEquals("Chapter 1: The Beginning", units[0].title)
        assertEquals(1.0, units[0].number, 0.001)
        assertEquals("Chapter 2: The Journey", units[1].title)
        assertEquals(2.0, units[1].number, 0.001)
    }

    // =========================================================================
    // Method 2: Multi-Source Theme Scraper (Theme Inheritance)
    // =========================================================================

    @Test
    fun `method2 - multi-source MangaThemesia transpilation and selector injection for AsuraScans`() = runBlocking {
        val kotlinSource = """
            package eu.kanade.tachiyomi.multisrc.mangathemesia

            class AsuraScans : MangaThemesia("Asura Scans", "https://asuracomic.net", "en")
        """.trimIndent()

        val transpiledJs = transpileWithNode(kotlinSource)

        // Verify constructor parameters extracted
        assertTrue(transpiledJs.contains("baseUrl = \"https://asuracomic.net\""))
        assertTrue(transpiledJs.contains("Asura Scans"))

        // Verify MangaThemesia theme-injected selectors
        assertTrue(transpiledJs.contains(".bsx"))
        assertTrue(transpiledJs.contains("/manga/?page=") && transpiledJs.contains("&order=popular"))
        assertTrue(transpiledJs.contains("#readerarea img"))

        // Verify ScriptableContentSourceEngine execution with AsuraScans theme profile
        val scraperUpdater = mockk<DynamicScraperUpdater>()
        val scriptEngine = mockk<ScriptableSourceEngine>()
        val prefStore = createMockPreferenceStore()
        val engine = ScriptableContentSourceEngine(
            ioDispatcher = Dispatchers.Unconfined,
            scraperUpdater = scraperUpdater,
            scriptEngine = scriptEngine,
            preferenceStore = prefStore,
            json = json,
        )

        coEvery { scraperUpdater.getScraperScript(any()) } returns transpiledJs
        val mockItems = """
            [
                {
                    "url": "https://asuracomic.net/series/return-of-the-mount-hua-sect",
                    "title": "Return of the Mount Hua Sect",
                    "thumbnailUrl": "https://asuracomic.net/covers/mount-hua.jpg",
                    "status": "Ongoing",
                    "contentType": "MANGA"
                }
            ]
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "search", any()) } returns mockItems

        val profile = SourceProfile(
            baseUrl = "https://asuracomic.net",
            contentType = ContentType.MANGA,
            displayName = "Asura Scans",
            sourceType = SourceType.JS_SCRAPER,
        )

        val results = engine.search(profile, "Mount Hua", 1)
        assertEquals(1, results.size)
        assertEquals("Return of the Mount Hua Sect", results[0].title)
        assertEquals("https://asuracomic.net/series/return-of-the-mount-hua-sect", results[0].url)
    }

    @Test
    fun `method2 - multi-source Madara transpilation and selector injection for Manhwa18`() = runBlocking {
        val kotlinSource = """
            package eu.kanade.tachiyomi.multisrc.madara

            class Manhwa18 : Madara("Manhwa18", "https://manhwa18.net", "en")
        """.trimIndent()

        val transpiledJs = transpileWithNode(kotlinSource)

        // Verify constructor parameters extracted
        assertTrue(transpiledJs.contains("baseUrl = \"https://manhwa18.net\""))
        assertTrue(transpiledJs.contains("Manhwa18"))

        // Verify Madara theme-injected selectors
        assertTrue(transpiledJs.contains("div.page-item-detail"))
        assertTrue(transpiledJs.contains("post_type=wp-manga"))
        assertTrue(transpiledJs.contains(".page-break img"))

        val scraperUpdater = mockk<DynamicScraperUpdater>()
        val scriptEngine = mockk<ScriptableSourceEngine>()
        val prefStore = createMockPreferenceStore()
        val engine = ScriptableContentSourceEngine(
            ioDispatcher = Dispatchers.Unconfined,
            scraperUpdater = scraperUpdater,
            scriptEngine = scriptEngine,
            preferenceStore = prefStore,
            json = json,
        )

        coEvery { scraperUpdater.getScraperScript(any()) } returns transpiledJs
        val mockPages = """
            [
                "https://manhwa18.net/media/ch1/01.jpg",
                "https://manhwa18.net/media/ch1/02.jpg",
                "https://manhwa18.net/media/ch1/03.jpg"
            ]
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "getPages", any()) } returns mockPages

        val profile = SourceProfile(
            baseUrl = "https://manhwa18.net",
            contentType = ContentType.MANGA,
            displayName = "Manhwa18",
            sourceType = SourceType.JS_SCRAPER,
        )

        val pages = engine.getPages(profile, "https://manhwa18.net/manga/sample/ch-1")
        assertEquals(3, pages.size)
        assertEquals("https://manhwa18.net/media/ch1/01.jpg", pages[0])
    }

    // =========================================================================
    // Method 3: Heuristic Autonomous Discovery (AdaptiveHeuristicEngine)
    // =========================================================================

    @Test
    fun `method3 - autonomous discovery extracts novel portal layout without extensions`() = runBlocking {
        val baseUrl = "https://example-novel-hub.org"
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><title>Novel Hub - Read Web Novels Online</title></head>
            <body>
                <form action="/search" method="get">
                    <input name="q" type="text" placeholder="Search web novels...">
                </form>
                <div class="list">
                    <div class="item">
                        <a href="/novel/the-primal-hunter">
                            <h2>The Primal Hunter</h2>
                            <img src="/covers/hunter.jpg" />
                        </a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val networkHelper = mockk<NetworkHelper>()
        val profileCache = mockk<SourceProfileCache>()
        val httpClient = mockk<OkHttpClient>()
        val mockCall = mockk<Call>()

        every { networkHelper.client } returns httpClient
        every { httpClient.newCall(any()) } returns mockCall

        val dummyRequest = Request.Builder().url(baseUrl).build()
        val dummyResponse = Response.Builder()
            .request(dummyRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(htmlContent.toResponseBody())
            .build()
        every { mockCall.execute() } returns dummyResponse

        val heuristicEngine = AdaptiveHeuristicEngine(
            ioDispatcher = Dispatchers.Unconfined,
            networkHelper = networkHelper,
            profileCache = profileCache,
        )

        val profile = heuristicEngine.discover(baseUrl)
        assertNotNull(profile)
        assertEquals(baseUrl, profile.baseUrl)
        assertEquals(ContentType.NOVEL, profile.contentType)
        assertEquals("/search?q={query}", profile.endpoints[Endpoint.SEARCH]?.pathTemplate)
        assertTrue(profile.selectors?.get(DataField.ITEM_LIST)?.contains(".item") == true)
    }

    @Test
    fun `method3 - autonomous discovery extracts manga reader pages without preset selectors`() = runBlocking {
        val chapterUrl = "https://example-reader.org/manga/solo-leveling/chapter-1"
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><title>Solo Leveling - Chapter 1</title></head>
            <body>
                <div class="reader-container">
                    <img class="page-img" src="https://example-reader.org/img/p1.jpg" />
                    <img class="page-img" src="https://example-reader.org/img/p2.jpg" />
                    <img class="page-img" src="https://example-reader.org/img/p3.jpg" />
                    <img class="page-img" src="https://example-reader.org/img/p4.jpg" />
                </div>
            </body>
            </html>
        """.trimIndent()

        val networkHelper = mockk<NetworkHelper>()
        val profileCache = mockk<SourceProfileCache>()
        val httpClient = mockk<OkHttpClient>()
        val mockCall = mockk<Call>()

        every { networkHelper.client } returns httpClient
        every { httpClient.newCall(any()) } returns mockCall

        val dummyRequest = Request.Builder().url(chapterUrl).build()
        val dummyResponse = Response.Builder()
            .request(dummyRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(htmlContent.toResponseBody())
            .build()
        every { mockCall.execute() } returns dummyResponse

        val heuristicEngine = AdaptiveHeuristicEngine(
            ioDispatcher = Dispatchers.Unconfined,
            networkHelper = networkHelper,
            profileCache = profileCache,
        )

        val profile = SourceProfile(
            baseUrl = "https://example-reader.org",
            contentType = ContentType.MANGA,
            displayName = "Example Reader",
        )

        val pages = heuristicEngine.getPages(profile, chapterUrl)
        assertEquals(4, pages.size)
        assertEquals("https://example-reader.org/img/p1.jpg", pages[0])
        assertEquals("https://example-reader.org/img/p4.jpg", pages[3])
    }

    // =========================================================================
    // Method 4: Unified Self-Healing Fallback Ladder (ContentSourceOrchestrator)
    // =========================================================================

    @Test
    fun `method4 - orchestrator rescues empty search results via heuristic fallback`() = runBlocking {
        val prefStore = createMockPreferenceStore()
        val profileCache = mockk<SourceProfileCache>()
        val primaryEngine = mockk<ContentSourceEngine>()
        val heuristicEngine = mockk<AdaptiveHeuristicEngine>()

        val orchestrator = ContentSourceOrchestrator(
            profileCache = profileCache,
            heuristicEngine = heuristicEngine,
            scriptEngine = primaryEngine,
            preferenceStore = prefStore,
        )

        val profile = SourceProfile(
            baseUrl = "https://redesigned-site.com",
            contentType = ContentType.MANGA,
            sourceType = SourceType.JS_SCRAPER,
            enabled = true,
        )

        coEvery { profileCache.get("https://redesigned-site.com") } returns profile
        coEvery { profileCache.save(any()) } returns Unit

        // Primary scraper returns empty list due to website redesign changing CSS selectors
        coEvery { primaryEngine.search(profile, "Naruto", 1) } returns emptyList()

        // Adaptive heuristic engine rescues the search by parsing DOM clusters
        val rescuedItem = ContentItem(
            id = -1L,
            sourceId = 1L,
            url = "https://redesigned-site.com/manga/naruto",
            title = "Naruto (Heuristic Rescue)",
            author = "Masashi Kishimoto",
            artist = null,
            description = "Rescued via DOM heuristics",
            genres = listOf("Action", "Adventure"),
            status = ContentStatus.Completed,
            thumbnailUrl = "https://redesigned-site.com/covers/naruto.jpg",
            contentType = ContentType.MANGA,
        )
        coEvery { heuristicEngine.search(profile, "Naruto", 1) } returns listOf(rescuedItem)

        val result = orchestrator.search("https://redesigned-site.com", "Naruto", 1)
        assertTrue("Search should succeed via fallback", result is Result.Success)
        val items = (result as Result.Success).data
        assertEquals(1, items.size)
        assertEquals("Naruto (Heuristic Rescue)", items[0].title)
    }

    @Test
    fun `method4 - orchestrator rescues getItem when primary scraper throws network error`() = runBlocking {
        val prefStore = createMockPreferenceStore()
        val profileCache = mockk<SourceProfileCache>()
        val primaryEngine = mockk<ContentSourceEngine>()
        val heuristicEngine = mockk<AdaptiveHeuristicEngine>()

        val orchestrator = ContentSourceOrchestrator(
            profileCache = profileCache,
            heuristicEngine = heuristicEngine,
            scriptEngine = primaryEngine,
            preferenceStore = prefStore,
        )

        val profile = SourceProfile(
            baseUrl = "https://blocked-source.com",
            contentType = ContentType.MANGA,
            sourceType = SourceType.JS_SCRAPER,
            enabled = true,
        )

        coEvery { profileCache.get("https://blocked-source.com") } returns profile
        coEvery { profileCache.save(any()) } returns Unit

        // Primary scraper fails with an exception
        coEvery {
            primaryEngine.getItem(profile, "https://blocked-source.com/item/1")
        } throws IllegalStateException("Scraper failed to parse item metadata")

        // Heuristic fallback provides the item
        val rescuedItem = ContentItem(
            id = -1L,
            sourceId = 1L,
            url = "https://blocked-source.com/item/1",
            title = "Rescued Item",
            author = null,
            artist = null,
            description = "Rescued",
            genres = emptyList(),
            status = ContentStatus.Ongoing,
            thumbnailUrl = null,
            contentType = ContentType.MANGA,
        )
        coEvery { heuristicEngine.getItem(profile, "https://blocked-source.com/item/1") } returns rescuedItem

        val result = orchestrator.getItem("https://blocked-source.com", "https://blocked-source.com/item/1")
        assertTrue("getItem should succeed via fallback", result is Result.Success)
        val item = (result as Result.Success).data
        assertEquals("Rescued Item", item.title)
    }

    @Test
    fun `method4 - orchestrator rescues getChapters and getPages via heuristic fallback`() = runBlocking {
        val prefStore = createMockPreferenceStore()
        val profileCache = mockk<SourceProfileCache>()
        val primaryEngine = mockk<ContentSourceEngine>()
        val heuristicEngine = mockk<AdaptiveHeuristicEngine>()

        val orchestrator = ContentSourceOrchestrator(
            profileCache = profileCache,
            heuristicEngine = heuristicEngine,
            scriptEngine = primaryEngine,
            preferenceStore = prefStore,
        )

        val profile = SourceProfile(
            baseUrl = "https://theme-source.org",
            contentType = ContentType.MANGA,
            sourceType = SourceType.JS_SCRAPER,
            enabled = true,
        )

        coEvery { profileCache.get("https://theme-source.org") } returns profile
        coEvery { profileCache.save(any()) } returns Unit

        // 1. Chapters fallback
        coEvery { primaryEngine.getChapters(profile, any()) } returns emptyList()
        val rescuedUnits = listOf(
            ContentUnit(
                id = -1L,
                contentItemId = -1L,
                url = "https://theme-source.org/ch1",
                title = "Rescued Chapter 1",
                number = 1.0,
                dateUpload = 0L,
                progress = 0L,
                totalLength = 0L,
                lastRead = 0L,
            ),
        )
        coEvery { heuristicEngine.getChapters(profile, any()) } returns rescuedUnits

        val chaptersResult = orchestrator.getChapters("https://theme-source.org", "https://theme-source.org/item")
        assertTrue(chaptersResult is Result.Success)
        assertEquals(1, (chaptersResult as Result.Success).data.size)
        assertEquals("Rescued Chapter 1", chaptersResult.data[0].title)

        // 2. Pages fallback
        coEvery { primaryEngine.getPages(profile, any()) } throws RuntimeException("Primary pages failed")
        val rescuedPages = listOf("https://theme-source.org/img1.jpg", "https://theme-source.org/img2.jpg")
        coEvery { heuristicEngine.getPages(profile, any()) } returns rescuedPages

        val pagesResult = orchestrator.getPages("https://theme-source.org", "https://theme-source.org/ch1")
        assertTrue(pagesResult is Result.Success)
        assertEquals(2, (pagesResult as Result.Success).data.size)
        assertEquals("https://theme-source.org/img1.jpg", pagesResult.data[0])
    }
}
