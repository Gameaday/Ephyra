package ephyra.feature.browse.source

import app.cash.turbine.test
import ephyra.core.common.util.Result
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.source.interactor.GetEnabledSources
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.interactor.ToggleSourcePin
import ephyra.domain.source.model.Pin
import ephyra.domain.source.model.Pins
import ephyra.domain.source.model.Source
import ephyra.feature.browse.presentation.SourceUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourcesViewModelTest {

    private val getEnabledSources: GetEnabledSources = mockk()
    private val toggleSource: ToggleSource = mockk(relaxed = true)
    private val toggleSourcePin: ToggleSourcePin = mockk(relaxed = true)
    private val addCustomSource: AddCustomSource = mockk(relaxed = true)

    private val sourcesFlow = MutableSharedFlow<List<Source>>(replay = 1)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testSource1 = Source(
        id = 1L,
        lang = "en",
        name = "Manga Source En",
        supportsLatest = true,
        isStub = false,
        pin = Pins.unpinned,
    )
    private val testSource2 = Source(
        id = 2L,
        lang = "ja",
        name = "Manga Source Ja",
        supportsLatest = true,
        isStub = false,
        pin = Pins(Pin.Actual),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getEnabledSources.subscribe() } returns sourcesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SourcesViewModel {
        return SourcesViewModel(
            getEnabledSources = getEnabledSources,
            toggleSource = toggleSource,
            toggleSourcePin = toggleSourcePin,
            addCustomSource = addCustomSource,
        )
    }

    @Test
    fun `initial state is loading and empty`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)
            assertTrue(initial.isEmpty)
            assertNull(initial.searchQuery)
            assertNull(initial.dialog)
        }
    }

    @Test
    fun `sources emission populates items and sets loading false`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            assertEquals(true, awaitItem().isLoading)

            sourcesFlow.emit(listOf(testSource1, testSource2))
            advanceTimeBy(300)
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.isLoading)
            assertFalse(state.isEmpty)
            // Expect pinned item first under pinned header, then "en" header
            val headerKeys = state.items.filterIsInstance<SourceUiModel.Header>().map { it.language }
            assertTrue(headerKeys.contains(SourcesViewModel.PINNED_KEY))
            assertTrue(headerKeys.contains("en"))
        }
    }

    @Test
    fun `search event filters sources`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem()

            sourcesFlow.emit(listOf(testSource1, testSource2))
            advanceTimeBy(300)
            advanceUntilIdle()
            awaitItem()

            viewModel.onEvent(SourcesScreenEvent.Search("Ja"))
            advanceTimeBy(300)
            advanceUntilIdle()

            val searchState = awaitItem()
            assertEquals("Ja", searchState.searchQuery)

            val filteredState = awaitItem()
            val itemNames = filteredState.items.filterIsInstance<SourceUiModel.Item>().map { it.source.name }
            assertEquals(listOf("Manga Source Ja"), itemNames)
        }
    }

    @Test
    fun `dialog events update state dialog correctly`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(SourcesScreenEvent.ShowSourceDialog(testSource1))
            val withDialog = awaitItem()
            assertEquals(testSource1, withDialog.dialog?.source)

            viewModel.onEvent(SourcesScreenEvent.CloseDialog)
            val withoutDialog = awaitItem()
            assertNull(withoutDialog.dialog)
        }
    }

    @Test
    fun `toggle source calls interactor`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SourcesScreenEvent.ToggleSource(testSource1))
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleSource.await(testSource1) }
    }

    @Test
    fun `toggle pin calls interactor`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(SourcesScreenEvent.TogglePin(testSource1))
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleSourcePin.await(testSource1) }
    }

    @Test
    fun `error fetching sources emits FailedFetchingSources effect`() = runTest {
        every { getEnabledSources.subscribe() } returns flow {
            throw RuntimeException("Network error")
        }

        val viewModel = createViewModel()
        viewModel.effects.test {
            advanceTimeBy(300)
            advanceUntilIdle()
            val effect = awaitItem()
            assertEquals(SourcesViewModel.Effect.FailedFetchingSources, effect)
        }
    }

    @Test
    fun `addWebSource emits WebSourceAdded effect on success`() = runTest {
        val testProfile = SourceProfile(
            baseUrl = "https://mangadex.org",
            contentType = ephyra.domain.content.model.ContentType.MANGA,
            displayName = "MangaDex",
        )
        coEvery { addCustomSource.addHeuristicProfile("https://mangadex.org", any()) } returns
            Result.Success(testProfile)

        val viewModel = createViewModel()
        viewModel.effects.test {
            viewModel.onEvent(SourcesScreenEvent.AddWebSource("https://mangadex.org", "MangaDex"))
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is SourcesViewModel.Effect.WebSourceAdded)
            assertEquals("MangaDex", (effect as SourcesViewModel.Effect.WebSourceAdded).name)
        }
    }
}
