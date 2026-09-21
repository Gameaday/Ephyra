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
import kotlinx.coroutines.flow.first
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

    /**
     * Suspends until the ViewModel publishes a state matching [predicate].
     *
     * Inspection work is executed on `Dispatchers.IO` (`ContentSourcingViewModel.inspectSource`),
     * so it is invisible to the `runTest` virtual clock: a `delay`-based polling loop advances
     * virtual time instantly and can assert before the background coroutine has published its
     * state. Collecting the state flow instead parks the test until the real thread emits, which
     * keeps these tests deterministic regardless of machine load.
     */
    private suspend fun awaitState(
        predicate: (ContentSourcingViewModel.State) -> Boolean,
    ): ContentSourcingViewModel.State = viewModel.state.first(predicate)

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

        val state = awaitState { it.inspectedProfile != null }

        assertFalse(state.isInspecting)
        assertNull(state.inspectError)
        assertEquals(expectedProfile, state.inspectedProfile)
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

        assertEquals(expectedProfile, awaitState { it.inspectedProfile != null }.inspectedProfile)

        viewModel.onEvent(ContentSourcingViewModel.Event.SaveInspectedProfile)

        val state = awaitState { it.inspectedProfile == null }

        coVerify { profileCache.save(expectedProfile) }
        assertNull(state.inspectedProfile)
        assertEquals("", state.inspectUrl)
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

        assertEquals(expectedProfile, awaitState { it.inspectedProfile != null }.inspectedProfile)

        viewModel.onEvent(ContentSourcingViewModel.Event.ClearInspection)

        val state = viewModel.state.value
        assertNull(state.inspectedProfile)
        assertEquals("", state.inspectUrl)
        assertNull(state.inspectError)
    }
}
