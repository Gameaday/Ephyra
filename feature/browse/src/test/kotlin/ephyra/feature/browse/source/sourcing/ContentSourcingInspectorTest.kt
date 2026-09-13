package ephyra.feature.browse.source.sourcing

import android.content.Context
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.service.LocalContentScanner
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.Endpoint
import ephyra.domain.content.source.EndpointPattern
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.content.source.SourceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContentSourcingInspectorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val scraperUpdater: ScraperScriptUpdater = mockk(relaxed = true)
    private val localScanner: LocalContentScanner = mockk(relaxed = true)
    private val orchestrator: ContentSourceOrchestrator = mockk(relaxed = true)
    private val profileCache: SourceProfileCache = mockk(relaxed = true)
    private val preferenceStore: PreferenceStore = mockk(relaxed = true)

    private lateinit var viewModel: ContentSourcingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { profileCache.getAllProfiledDomains() } returns emptySet()
        coEvery { scraperUpdater.listScrapers() } returns emptyList()

        val mockPref = mockk<Preference<String>>(relaxed = true)
        coEvery { mockPref.get() } returns ""
        every { preferenceStore.getString(any(), any()) } returns mockPref

        viewModel = ContentSourcingViewModel(
            context = context,
            scraperUpdater = scraperUpdater,
            localScanner = localScanner,
            orchestrator = orchestrator,
            profileCache = profileCache,
            preferenceStore = preferenceStore,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `inspect source with valid url updates state with inspected profile`() = runTest {
        val testUrl = "https://test-manga.com"
        val expectedProfile = SourceProfile(
            baseUrl = testUrl,
            displayName = "Test Manga",
            contentType = ContentType.MANGA,
            sourceType = SourceType.HEURISTIC,
            endpoints = mapOf(Endpoint.SEARCH to EndpointPattern("/search")),
        )

        coEvery { orchestrator.discover(testUrl) } returns Result.Success(expectedProfile)

        viewModel.onEvent(ContentSourcingViewModel.Event.UpdateInspectUrl(testUrl))
        assertEquals(testUrl, viewModel.state.value.inspectUrl)

        viewModel.onEvent(ContentSourcingViewModel.Event.InspectSource(testUrl))

        var retries = 50
        while (viewModel.state.value.inspectedProfile == null && retries-- > 0) {
            kotlinx.coroutines.delay(20)
        }

        assertFalse(viewModel.state.value.isInspecting)
        assertNull(viewModel.state.value.inspectError)
        assertEquals(expectedProfile, viewModel.state.value.inspectedProfile)
    }

    @Test
    fun `inspect source with invalid url sets inspectError`() = runTest {
        viewModel.onEvent(ContentSourcingViewModel.Event.InspectSource("invalid-url"))

        assertNotNull(viewModel.state.value.inspectError)
        assertNull(viewModel.state.value.inspectedProfile)
    }

    @Test
    fun `save inspected profile saves to cache and resets inspection state`() = runTest {
        val testUrl = "https://test-novel.com"
        val expectedProfile = SourceProfile(
            baseUrl = testUrl,
            displayName = "Test Novel",
            contentType = ContentType.NOVEL,
            sourceType = SourceType.HEURISTIC,
        )

        coEvery { orchestrator.discover(testUrl) } returns Result.Success(expectedProfile)
        viewModel.onEvent(ContentSourcingViewModel.Event.InspectSource(testUrl))

        var retries = 50
        while (viewModel.state.value.inspectedProfile == null && retries-- > 0) {
            kotlinx.coroutines.delay(20)
        }
        assertEquals(expectedProfile, viewModel.state.value.inspectedProfile)

        viewModel.onEvent(ContentSourcingViewModel.Event.SaveInspectedProfile)

        retries = 50
        while (viewModel.state.value.inspectedProfile != null && retries-- > 0) {
            kotlinx.coroutines.delay(20)
        }

        coVerify { profileCache.save(expectedProfile) }
        assertNull(viewModel.state.value.inspectedProfile)
        assertEquals("", viewModel.state.value.inspectUrl)
    }

    @Test
    fun `clear inspection resets state`() = runTest {
        val testUrl = "https://test-anime.com"
        val expectedProfile = SourceProfile(
            baseUrl = testUrl,
            displayName = "Test Anime",
            contentType = ContentType.ANIME,
            sourceType = SourceType.HEURISTIC,
        )

        coEvery { orchestrator.discover(testUrl) } returns Result.Success(expectedProfile)
        viewModel.onEvent(ContentSourcingViewModel.Event.InspectSource(testUrl))

        var retries = 50
        while (viewModel.state.value.inspectedProfile == null && retries-- > 0) {
            kotlinx.coroutines.delay(20)
        }
        assertEquals(expectedProfile, viewModel.state.value.inspectedProfile)

        viewModel.onEvent(ContentSourcingViewModel.Event.ClearInspection)

        assertNull(viewModel.state.value.inspectedProfile)
        assertEquals("", viewModel.state.value.inspectUrl)
        assertNull(viewModel.state.value.inspectError)
    }
}
