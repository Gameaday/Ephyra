package ephyra.data.sourcing

import android.app.Application
import android.content.res.AssetManager
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.extension.model.Extension
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

    private val transpiler = LegacyExtensionTranspiler(
        context = context,
        jsEngine = jsEngine,
        networkHelper = networkHelper,
        scraperUpdater = scraperUpdater,
        addCustomSource = addCustomSource,
        removeCustomSource = removeCustomSource,
    )

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
}
