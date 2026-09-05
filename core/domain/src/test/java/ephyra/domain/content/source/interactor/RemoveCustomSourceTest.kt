package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [RemoveCustomSource] — removing a content source.
 */
class RemoveCustomSourceTest {

    private val orchestrator = mockk<ContentSourceOrchestrator>(relaxed = true)
    private val scraperUpdater = mockk<ScraperScriptUpdater>()
    private val preferenceStore = mockk<PreferenceStore>()

    private val interactor = RemoveCustomSource(
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
    )

    @Test
    fun `removeSource removes scraper and invalidates profile`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns listOf(jsProfile())
        every { scraperUpdater.removeScraper("mangadex_scraper.js") } returns true

        val pref = mockk<Preference<String>>(relaxed = true)
        every { preferenceStore.getString(any(), any()) } returns pref

        val result = interactor.removeSource(baseUrl)

        assertTrue(result is Result.Success)
        coVerify { scraperUpdater.removeScraper("mangadex_scraper.js") }
        coVerify { orchestrator.invalidateProfile(baseUrl) }
    }

    @Test
    fun `removeSource returns Error when source not found`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns emptyList()

        val result = interactor.removeSource(baseUrl)

        assertTrue(result is Result.Error)
    }

    @Test
    fun `removeSource keeps scraper for heuristic sources`() = runTest {
        val heuristicProfile = SourceProfile(
            baseUrl = baseUrl,
            contentType = ContentType.MANGA,
            sourceType = SourceType.HEURISTIC,
            enabled = true,
        )
        coEvery { orchestrator.getAllProfiles() } returns listOf(heuristicProfile)

        val mockPref = mockk<Preference<String>>(relaxed = true)
        every { preferenceStore.getString(any(), any()) } returns mockPref

        val result = interactor.removeSource(baseUrl)

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { scraperUpdater.removeScraper(any()) }
        coVerify { orchestrator.invalidateProfile(baseUrl) }
    }

    @Test
    fun `disableSource delegates to the orchestrator`() = runTest {
        coEvery { orchestrator.setSourceEnabled(baseUrl, false) } returns Result.Success(
            SourceProfile(baseUrl = baseUrl, contentType = ContentType.MANGA, enabled = false),
        )

        val result = interactor.disableSource(baseUrl)

        assertTrue(result is Result.Success)
        coVerify { orchestrator.setSourceEnabled(baseUrl, false) }
    }

    @Test
    fun `unlinkScraper switches a JS profile back to heuristic`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns listOf(jsProfile())

        val mockPref = mockk<Preference<String>>(relaxed = true)
        every { preferenceStore.getString(any(), any()) } returns mockPref

        val result = interactor.unlinkScraper(baseUrl)

        assertTrue(result is Result.Success)
        coVerify { orchestrator.setSourceType(baseUrl, SourceType.HEURISTIC, null) }
    }
}