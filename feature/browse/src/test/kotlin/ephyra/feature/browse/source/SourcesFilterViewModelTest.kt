package ephyra.feature.browse.source

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.source.interactor.GetLanguagesWithSources
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.model.Pin
import ephyra.domain.source.model.Pins
import ephyra.domain.source.model.Source
import ephyra.domain.source.service.SourcePreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.TreeMap

@OptIn(ExperimentalCoroutinesApi::class)
class SourcesFilterViewModelTest {

    private val preferences: SourcePreferences = mockk(relaxed = true)
    private val getLanguagesWithSources: GetLanguagesWithSources = mockk(relaxed = true)
    private val toggleSource: ToggleSource = mockk(relaxed = true)
    private val toggleLanguage: ToggleLanguage = mockk(relaxed = true)

    private val enabledLanguagesPref: Preference<Set<String>> = mockk(relaxed = true)
    private val disabledSourcesPref: Preference<Set<String>> = mockk(relaxed = true)

    private val languagesWithSourcesFlow = MutableSharedFlow<java.util.SortedMap<String, List<Source>>>(replay = 1)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testSource = Source(
        id = 1L,
        lang = "en",
        name = "Manga Source En",
        supportsLatest = true,
        isStub = false,
        pin = Pins.unpinned,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { preferences.enabledLanguages() } returns enabledLanguagesPref
        every { preferences.disabledSources() } returns disabledSourcesPref
        every { enabledLanguagesPref.changes() } returns flowOf(setOf("en"))
        every { disabledSourcesPref.changes() } returns flowOf(emptySet())
        every { getLanguagesWithSources.subscribe() } returns languagesWithSourcesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = SourcesFilterViewModel(
            preferences,
            getLanguagesWithSources,
            toggleSource,
            toggleLanguage,
        )

        viewModel.state.test {
            assertEquals(SourcesFilterViewModel.State.Loading, awaitItem())
        }
    }

    @Test
    fun `languages and sources emission updates state to Success`() = runTest {
        val viewModel = SourcesFilterViewModel(
            preferences,
            getLanguagesWithSources,
            toggleSource,
            toggleLanguage,
        )

        val items = TreeMap<String, List<Source>>().apply {
            put("en", listOf(testSource))
        }

        viewModel.state.test {
            assertEquals(SourcesFilterViewModel.State.Loading, awaitItem())

            languagesWithSourcesFlow.emit(items)

            val success = awaitItem() as SourcesFilterViewModel.State.Success
            assertEquals(1, success.items.size)
            assertTrue(success.enabledLanguages.contains("en"))
        }
    }

    @Test
    fun `ToggleSource calls interactor`() = runTest {
        val viewModel = SourcesFilterViewModel(
            preferences,
            getLanguagesWithSources,
            toggleSource,
            toggleLanguage,
        )

        viewModel.onEvent(SourcesFilterScreenEvent.ToggleSource(testSource))

        coVerify { toggleSource.await(testSource) }
    }

    @Test
    fun `ToggleLanguage calls interactor`() = runTest {
        val viewModel = SourcesFilterViewModel(
            preferences,
            getLanguagesWithSources,
            toggleSource,
            toggleLanguage,
        )

        viewModel.onEvent(SourcesFilterScreenEvent.ToggleLanguage("en"))

        coVerify { toggleLanguage.await("en") }
    }
}
