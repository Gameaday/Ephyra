package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.core.common.util.getOrThrow
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [UpdateCustomSource] — refreshing and reconfiguring
 * content sources.
 */
class UpdateCustomSourceTest {

    private val orchestrator = mockk<ContentSourceOrchestrator>(relaxed = true)
    private val scraperUpdater = mockk<ScraperScriptUpdater>()
    private val preferenceStore = mockk<PreferenceStore>()

    private val interactor = UpdateCustomSource(
        orchestrator = orchestrator,
        scraperUpdater = scraperUpdater,
        preferenceStore = preferenceStore,
    )

    private val baseUrl = "https://mangadex.org"

    private fun jsProfile() = SourceProfile(
        baseUrl = baseUrl,
        contentType = ContentType.MANGA,
        sourceType = SourceType.JS_SCRAPER,
        enabled = true,
        scraperFilename = "mangadex_scraper.js",
        displayName = "MangaDex",
    )

    @Test
    fun `checkAndUpdateScraper rediscover after update check`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns listOf(jsProfile())
        coEvery { scraperUpdater.checkForUpdates("mangadex_scraper.js") } returns true
        coEvery { orchestrator.rediscover(baseUrl) } returns jsProfile()

        val result = interactor.checkAndUpdateScraper(baseUrl)

        assertTrue(result is Result.Success)
        coVerify { scraperUpdater.checkForUpdates("mangadex_scraper.js") }
        coVerify { orchestrator.rediscover(baseUrl) }
    }

    @Test
    fun `checkAndUpdateScraper returns Error when source not found`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns emptyList()

        val result = interactor.checkAndUpdateScraper(baseUrl)

        assertTrue(result is Result.Error)
    }

    @Test
    fun `updateScraperScript imports new content and rediscovers`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns listOf(jsProfile())
        coEvery { scraperUpdater.importLocalScraperScript(any(), any()) } returns mockk()
        coEvery { orchestrator.rediscover(baseUrl) } returns jsProfile()

        val result = interactor.updateScraperScript(baseUrl, "new content")

        assertTrue(result is Result.Success)
        coVerify { scraperUpdater.importLocalScraperScript("mangadex_scraper.js", "new content") }
    }

    @Test
    fun `renameScraper renames and remaps the scraper`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns listOf(jsProfile())
        every { scraperUpdater.renameScraper("mangadex_scraper.js", "md2.js") } returns true

        val mockPref = mockk<Preference<String>>(relaxed = true)
        every { preferenceStore.getString(any(), any()) } returns mockPref

        val result = interactor.renameScraper(baseUrl, "md2.js")

        assertTrue(result is Result.Success)
        coVerify { scraperUpdater.renameScraper("mangadex_scraper.js", "md2.js") }
    }

    @Test
    fun `renameScraper returns Error when rename fails`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns listOf(jsProfile())
        every { scraperUpdater.renameScraper(any(), any()) } returns false

        val result = interactor.renameScraper(baseUrl, "md2.js")

        assertTrue(result is Result.Error)
    }

    @Test
    fun `forceRediscover delegates to the orchestrator`() = runTest {
        coEvery { orchestrator.rediscover(baseUrl) } returns jsProfile()

        val result = interactor.forceRediscover(baseUrl)

        assertTrue(result is Result.Success)
        assertEquals(baseUrl, result.getOrThrow().baseUrl)
    }
}
