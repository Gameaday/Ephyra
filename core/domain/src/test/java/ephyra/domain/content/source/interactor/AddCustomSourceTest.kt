package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.core.common.util.getOrThrow
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.service.ExtensionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [AddCustomSource] — the "add a content source" pipeline.
 *
 * These run without the Android/Robolectric runtime, so a regression in source
 * registration is caught in seconds rather than after a full app build.
 */
class AddCustomSourceTest {

    private val orchestrator = mockk<ContentSourceOrchestrator>()
    private val scraperUpdater = mockk<ScraperScriptUpdater>()
    private val preferenceStore = mockk<PreferenceStore>()
    private val extensionManager = mockk<ExtensionManager>()

    private val interactor = AddCustomSource(
        orchestrator = orchestrator,
        scraperUpdater = scraperUpdater,
        preferenceStore = preferenceStore,
        extensionManager = extensionManager,
    )

    @Test
    fun `addJsScraper downloads and registers the scraper`() = runTest {
        coEvery { scraperUpdater.downloadScraper(any(), any()) } returns mockk()
        coEvery { orchestrator.discover(any()) } returns mockk()
        coEvery { orchestrator.setSourceType(any(), any(), any()) } returns mockk()

        val result = interactor.addJsScraper("https://github.com/user/mangadex", "mangadex_scraper.js")

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { scraperUpdater.downloadScraper(any(), any()) }
        coVerify(exactly = 1) { orchestrator.discover(any()) }
        coVerify(exactly = 1) { orchestrator.setSourceType(any(), SourceType.JS_SCRAPER, "mangadex_scraper.js") }
    }

    @Test
    fun `addJsScraper returns Error on download failure`() = runTest {
        coEvery { scraperUpdater.downloadScraper(any(), any()) } throws IllegalStateException("network down")

        val result = interactor.addJsScraper("https://github.com/user/mangadex", "mangadex_scraper.js")

        assertTrue(result is Result.Error)
    }

    @Test
    fun `importJsScraper imports a local script and registers it`() = runTest {
        coEvery { scraperUpdater.importLocalScraperScript(any(), any()) } returns mockk()
        coEvery { orchestrator.discover(any()) } returns mockk()
        coEvery { orchestrator.setSourceType(any(), any(), any()) } returns mockk()

        val result = interactor.importJsScraper("custom.js", "content")

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { scraperUpdater.importLocalScraperScript("custom.js", "content") }
    }

    @Test
    fun `addHeuristicProfile discovers and returns a heuristic profile`() = runTest {
        coEvery { orchestrator.discover(any()) } returns Result.Success(
            SourceProfile(
                baseUrl = "https://manga.example",
                contentType = ContentType.MANGA,
                sourceType = SourceType.HEURISTIC,
                enabled = true,
                displayName = "Manga",
            ),
        )

        val result = interactor.addHeuristicProfile("https://manga.example", "Manga")

        assertTrue(result is Result.Success)
        assertEquals("https://manga.example", result.getOrThrow().baseUrl)
        coVerify(exactly = 1) { orchestrator.discover("https://manga.example") }
    }
}
