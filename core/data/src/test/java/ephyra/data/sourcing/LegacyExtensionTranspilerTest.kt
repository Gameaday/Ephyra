package ephyra.data.sourcing

import android.app.Application
import android.content.res.AssetManager
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.extension.model.Extension
import ephyra.source.api.ScriptableSourceEngine
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LegacyExtensionTranspilerTest {

    private val context = mockk<Application>()
    private val jsEngine = mockk<JavaScriptEngine>()
    private val networkHelper = mockk<NetworkHelper>()
    private val scraperUpdater = mockk<DynamicScraperUpdater>()
    private val addCustomSource = mockk<AddCustomSource>()
    private val removeCustomSource = mockk<RemoveCustomSource>()
    private val preferenceStore = mockk<PreferenceStore>()

    private val transpiler = LegacyExtensionTranspiler(
        context = context,
        jsEngine = jsEngine,
        networkHelper = networkHelper,
        scraperUpdater = scraperUpdater,
        addCustomSource = addCustomSource,
        removeCustomSource = removeCustomSource,
        preferenceStore = preferenceStore,
    )

    init {
        val mockPrefLong = mockk<ephyra.core.common.preference.Preference<Long>>()
        val mockPrefString = mockk<ephyra.core.common.preference.Preference<String>>()
        every { preferenceStore.getLong(any(), any()) } returns mockPrefLong
        every { mockPrefLong.set(any()) } returns Unit
        every { preferenceStore.getString(any(), any()) } returns mockPrefString
        every { mockPrefString.set(any()) } returns Unit
    }

    @Test
    fun `transpileAndInstall fetches kotlin files transpiles them and maps them`() = runBlocking {
        val extension = Extension.Available(
            name = "MangaDex",
            pkgName = "eu.kanade.tachiyomi.extension.en.mangadex",
            versionName = "1.4.0",
            versionCode = 14L,
            libVersion = 1.4,
            lang = "en",
            isNsfw = false,
            sources = listOf(
                Extension.Available.Source(
                    id = 12345L,
                    lang = "en",
                    name = "MangaDex",
                    baseUrl = "https://mangadex.org",
                ),
            ),
            apkName = "mangadex.apk",
            iconUrl = "https://mangadex.org/icon.png",
            repoUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
        )

        // Mock assets loading for transpiler.js
        val assetManager = mockk<AssetManager>()
        every { context.assets } returns assetManager
        val mockJs = "function transpile() { return 'mock_transpiled_code'; }"
        every { assetManager.open("transpiler.js") } returns ByteArrayInputStream(mockJs.toByteArray())

        // Mock HTTP client response for Kotlin file
        val httpClient = mockk<OkHttpClient>()
        every { networkHelper.client } returns httpClient
        val mockCall = mockk<Call>()
        every { httpClient.newCall(any()) } returns mockCall
        val mockResponse = mockk<Response>()
        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<okhttp3.Callback>()
            callback.onResponse(mockCall, mockResponse)
        }
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        val mockKotlinSource = "class MangaDex : HttpSource() { override val name = \"MangaDex\" }"
        every { mockResponse.body } returns mockKotlinSource.toResponseBody(null)

        // Mock QuickJS evaluation
        coEvery { jsEngine.evaluate<String>(any()) } returns "mock_generated_scraper_js"

        // Mock scraper installation
        every { scraperUpdater.importLocalScraperScript(any(), any()) } returns mockk()

        // Mock source linking
        coEvery { addCustomSource.linkScraperToUrl(any(), any()) } returns mockk()

        val success = transpiler.transpileAndInstall(extension)

        assertTrue(success)

        coVerify {
            scraperUpdater.importLocalScraperScript("mangadex_scraper.js", "mock_generated_scraper_js")
            addCustomSource.linkScraperToUrl("https://mangadex.org", "mangadex_scraper.js")
        }
    }

    @Test
    fun `transpileAndInstall with selectedUrls links only selected and unlinks deselected`() = runBlocking {
        val extension = Extension.Available(
            name = "MultiSource",
            pkgName = "eu.kanade.tachiyomi.extension.en.multisource",
            versionName = "1.0.0",
            versionCode = 1L,
            libVersion = 1.0,
            lang = "en",
            isNsfw = false,
            sources = listOf(
                Extension.Available.Source(
                    id = 1L,
                    lang = "en",
                    name = "Source A",
                    baseUrl = "https://sourceA.com",
                ),
                Extension.Available.Source(
                    id = 2L,
                    lang = "en",
                    name = "Source B",
                    baseUrl = "https://sourceB.com",
                ),
            ),
            apkName = "multisource.apk",
            iconUrl = "https://multisource.com/icon.png",
            repoUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
        )

        // Mock assets loading for transpiler.js
        val assetManager = mockk<AssetManager>()
        every { context.assets } returns assetManager
        val mockJs = "function transpile() { return 'mock_transpiled_code'; }"
        every { assetManager.open("transpiler.js") } returns ByteArrayInputStream(mockJs.toByteArray())

        // Mock HTTP client response for Kotlin file
        val httpClient = mockk<OkHttpClient>()
        every { networkHelper.client } returns httpClient
        val mockCall = mockk<Call>()
        every { httpClient.newCall(any()) } returns mockCall
        val mockResponse = mockk<Response>()
        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<okhttp3.Callback>()
            callback.onResponse(mockCall, mockResponse)
        }
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        val mockKotlinSource = "class MultiSource : HttpSource() { override val name = \"MultiSource\" }"
        every { mockResponse.body } returns mockKotlinSource.toResponseBody(null)

        // Mock QuickJS evaluation
        coEvery { jsEngine.evaluate<String>(any()) } returns "mock_generated_scraper_js"

        // Mock scraper installation
        every { scraperUpdater.importLocalScraperScript(any(), any()) } returns mockk()

        // Mock source linking and unlinking
        coEvery { addCustomSource.linkScraperToUrl(any(), any()) } returns mockk()
        coEvery { removeCustomSource.removeSource(any()) } returns mockk()

        val success = transpiler.transpileAndInstall(
            extension = extension,
            selectedUrls = setOf("https://sourceA.com"),
        )

        assertTrue(success)

        coVerify {
            scraperUpdater.importLocalScraperScript("multisource_scraper.js", "mock_generated_scraper_js")
            addCustomSource.linkScraperToUrl("https://sourceA.com", "multisource_scraper.js")
            removeCustomSource.removeSource("https://sourceB.com")
        }
    }

    @Test
    fun `testTranspilationSearchAndSelectionFlow`() = runBlocking {
        val extension = Extension.Available(
            name = "TestExtension",
            pkgName = "eu.kanade.tachiyomi.extension.en.testextension",
            versionName = "1.0.0",
            versionCode = 1L,
            libVersion = 1.0,
            lang = "en",
            isNsfw = false,
            sources = listOf(
                Extension.Available.Source(
                    id = 999L,
                    lang = "en",
                    name = "TestExtension",
                    baseUrl = "https://testextension.com",
                ),
            ),
            apkName = "testextension.apk",
            iconUrl = "https://testextension.com/icon.png",
            repoUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
        )

        // Mock assets loading for transpiler.js
        val assetManager = mockk<AssetManager>()
        every { context.assets } returns assetManager
        val mockJs = "function transpile() { return 'mock_transpiled_code'; }"
        every { assetManager.open("transpiler.js") } returns ByteArrayInputStream(mockJs.toByteArray())

        // Mock HTTP client response for Kotlin file
        val httpClient = mockk<OkHttpClient>()
        every { networkHelper.client } returns httpClient
        val mockCall = mockk<Call>()
        every { httpClient.newCall(any()) } returns mockCall
        val mockResponse = mockk<Response>()
        every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<okhttp3.Callback>()
            callback.onResponse(mockCall, mockResponse)
        }
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        val mockKotlinSource = "class TestExtension : HttpSource() { override val name = \"TestExtension\" }"
        every { mockResponse.body } returns mockKotlinSource.toResponseBody(null)

        // Mock QuickJS evaluation
        coEvery { jsEngine.evaluate<String>(any()) } returns "mock_generated_scraper_js"

        // Mock scraper installation
        val mockScraperUpdater = mockk<DynamicScraperUpdater>()
        every { mockScraperUpdater.importLocalScraperScript(any(), any()) } returns mockk()

        // Mock preference mapping
        val mockPreferenceStore = mockk<PreferenceStore>()
        val mockPrefLong = mockk<ephyra.core.common.preference.Preference<Long>>()
        val mockPrefString = mockk<ephyra.core.common.preference.Preference<String>>()
        every {
            mockPreferenceStore.getLong(
                "transpiled_extension_versioncode_eu.kanade.tachiyomi.extension.en.testextension",
                any(),
            )
        } returns mockPrefLong
        every { mockPrefLong.set(any()) } returns Unit
        every {
            mockPreferenceStore.getString(
                "transpiled_extension_versionname_eu.kanade.tachiyomi.extension.en.testextension",
                any(),
            )
        } returns mockPrefString
        every { mockPrefString.set(any()) } returns Unit
        every {
            mockPreferenceStore.getString(
                "transpiled_extension_filename_eu.kanade.tachiyomi.extension.en.testextension",
                any(),
            )
        } returns mockPrefString
        every { mockPrefString.set(any()) } returns Unit

        coEvery { addCustomSource.linkScraperToUrl(any(), any()) } returns mockk()

        // Construct transpiler with mock preference store
        val transpilerWithPrefs = LegacyExtensionTranspiler(
            context = context,
            jsEngine = jsEngine,
            networkHelper = networkHelper,
            scraperUpdater = mockScraperUpdater,
            addCustomSource = addCustomSource,
            removeCustomSource = removeCustomSource,
            preferenceStore = mockPreferenceStore,
        )

        val success = transpilerWithPrefs.transpileAndInstall(extension)
        assertTrue(success)

        // Now mock the script execution using ScriptableContentSourceEngine
        val scriptEngine = mockk<ScriptableSourceEngine>()
        val scriptEnginePref = mockk<ephyra.core.common.preference.Preference<String>>()
        every { mockPreferenceStore.getString("baseUrl_scraper_mapping_testextension.com", "") } returns
            scriptEnginePref
        coEvery { scriptEnginePref.get() } returns "testextension_scraper.js"
        coEvery { mockScraperUpdater.getScraperScript("testextension_scraper.js") } returns "mock_generated_scraper_js"

        // 1. Mock Search
        val mockItemsJson = """
            [
                {
                    "url": "https://testextension.com/title/manga1",
                    "title": "Manga Test",
                    "thumbnailUrl": "https://testextension.com/cover.jpg",
                    "status": "Ongoing",
                    "contentType": "MANGA"
                }
            ]
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "search", any()) } returns mockItemsJson

        val sourceEngine = ScriptableContentSourceEngine(
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            scraperUpdater = mockScraperUpdater,
            scriptEngine = scriptEngine,
            preferenceStore = mockPreferenceStore,
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )

        val profile = SourceProfile(
            baseUrl = "https://testextension.com",
            contentType = ContentType.MANGA,
            displayName = "TestExtension",
        )

        val searchItems = sourceEngine.search(profile, "Manga Test", 1)
        assertEquals(1, searchItems.size)
        assertEquals("Manga Test", searchItems[0].title)

        // 2. Mock Item Detail Selection
        val mockItemJson = """
            {
                "url": "https://testextension.com/title/manga1",
                "title": "Manga Test",
                "description": "Mocked description.",
                "thumbnailUrl": "https://testextension.com/cover.jpg",
                "status": "Ongoing",
                "contentType": "MANGA",
                "author": "Mock Author"
            }
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "getItem", any()) } returns mockItemJson

        val selectedItem = sourceEngine.getItem(profile, "https://testextension.com/title/manga1")
        assertNotNull(selectedItem)
        assertEquals("Mock Author", selectedItem.author)

        // 3. Mock Reading pages
        val mockPagesJson = """
            [
                "https://testextension.com/page1.jpg",
                "https://testextension.com/page2.jpg"
            ]
        """.trimIndent()
        coEvery { scriptEngine.executeScraper(any(), "getPages", any()) } returns mockPagesJson

        val pages = sourceEngine.getPages(profile, "https://testextension.com/chapter1")
        assertEquals(2, pages.size)
        assertEquals("https://testextension.com/page1.jpg", pages[0])
    }
}
