package ephyra.data.sourcing

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import ephyra.domain.extension.service.ExtensionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperManagementTest {

    private val orchestrator = mockk<ContentSourceOrchestrator>()
    private val scraperUpdater = mockk<ScraperScriptUpdater>()
    private val preferenceStore = mockk<PreferenceStore>()
    private val extensionManager = mockk<ExtensionManager>()

    private val addCustomSource = AddCustomSource(
        orchestrator = orchestrator,
        scraperUpdater = scraperUpdater,
        preferenceStore = preferenceStore,
        extensionManager = extensionManager,
    )

    private val removeCustomSource = RemoveCustomSource(
        orchestrator = orchestrator,
        scraperUpdater = scraperUpdater,
        preferenceStore = preferenceStore,
    )

    private val updateCustomSource = UpdateCustomSource(
        orchestrator = orchestrator,
        scraperUpdater = scraperUpdater,
        preferenceStore = preferenceStore,
    )

    @Test
    fun `addJsScraper downloads and registers scraper successfully`() = runBlocking {
        val githubUrl = "https://github.com/user/mangadex"
        val filename = "mangadex_scraper.js"

        coEvery { scraperUpdater.downloadScraper(githubUrl, filename) } returns mockk()
        coEvery { orchestrator.discover(any()) } returns mockk()
        coEvery { orchestrator.setSourceType(any(), any(), any()) } returns mockk()

        val result = addCustomSource.addJsScraper(githubUrl, filename)

        assertTrue(result is Result.Success)
        coVerify {
            scraperUpdater.downloadScraper(githubUrl, filename)
            orchestrator.discover("https://mangadex.com")
            orchestrator.setSourceType("https://mangadex.com", SourceType.JS_SCRAPER, filename)
        }
    }

    @Test
    fun `removeSource removes scraper file and mapping successfully`() = runBlocking {
        val baseUrl = "https://mangadex.org"
        val scraperFilename = "mangadex_scraper.js"

        val mockProfile = SourceProfile(
            baseUrl = baseUrl,
            contentType = ephyra.domain.content.model.ContentType.MANGA,
            sourceType = SourceType.JS_SCRAPER,
            scraperFilename = scraperFilename,
        )

        coEvery { orchestrator.getAllProfiles() } returns listOf(mockProfile)
        every { scraperUpdater.removeScraper(scraperFilename) } returns true
        coEvery { orchestrator.invalidateProfile(baseUrl) } returns mockk()

        // Mock preference mapping clean up
        val mockPref = mockk<ephyra.core.common.preference.Preference<String>>()
        every { preferenceStore.getString("baseUrl_scraper_mapping_mangadex.org", "") } returns mockPref
        every { mockPref.delete() } returns Unit

        val result = removeCustomSource.removeSource(baseUrl)

        assertTrue(result is Result.Success)
        coVerify {
            scraperUpdater.removeScraper(scraperFilename)
            orchestrator.invalidateProfile(baseUrl)
        }
    }

    @Test
    fun `updateScraper checks and triggers download updates successfully`() = runBlocking {
        val baseUrl = "https://mangadex.org"
        val scraperFilename = "mangadex_scraper.js"

        val mockProfile = SourceProfile(
            baseUrl = baseUrl,
            contentType = ephyra.domain.content.model.ContentType.MANGA,
            sourceType = SourceType.JS_SCRAPER,
            scraperFilename = scraperFilename,
        )

        coEvery { orchestrator.getAllProfiles() } returns listOf(mockProfile)
        coEvery { scraperUpdater.checkForUpdates(scraperFilename) } returns true
        coEvery { orchestrator.rediscover(baseUrl) } returns mockProfile

        val result = updateCustomSource.checkAndUpdateScraper(baseUrl)

        assertTrue(result is Result.Success)
        coVerify {
            scraperUpdater.checkForUpdates(scraperFilename)
            orchestrator.rediscover(baseUrl)
        }
    }
}
