package ephyra.feature.browse.extension

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.extension.interactor.GetExtensionLanguages
import ephyra.domain.source.interactor.ToggleLanguage
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionFilterViewModelTest {

    private val preferences: SourcePreferences = mockk(relaxed = true)
    private val getExtensionLanguages: GetExtensionLanguages = mockk(relaxed = true)
    private val toggleLanguage: ToggleLanguage = mockk(relaxed = true)

    private val enabledLanguagesPref: Preference<Set<String>> = mockk(relaxed = true)
    private val extensionLanguagesFlow = MutableSharedFlow<List<String>>(replay = 1)
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { preferences.enabledLanguages() } returns enabledLanguagesPref
        every { enabledLanguagesPref.changes() } returns flowOf(setOf("en"))
        every { getExtensionLanguages.subscribe() } returns extensionLanguagesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = ExtensionFilterViewModel(
            preferences,
            getExtensionLanguages,
            toggleLanguage,
        )

        viewModel.state.test {
            assertEquals(ExtensionFilterState.Loading, awaitItem())
        }
    }

    @Test
    fun `languages emission updates state to Success`() = runTest {
        val viewModel = ExtensionFilterViewModel(
            preferences,
            getExtensionLanguages,
            toggleLanguage,
        )

        viewModel.state.test {
            assertEquals(ExtensionFilterState.Loading, awaitItem())

            extensionLanguagesFlow.emit(listOf("en", "ja"))

            val success = awaitItem() as ExtensionFilterState.Success
            assertEquals(2, success.languages.size)
            assertTrue(success.enabledLanguages.contains("en"))
        }
    }

    @Test
    fun `Toggle event calls toggleLanguage interactor`() = runTest {
        val viewModel = ExtensionFilterViewModel(
            preferences,
            getExtensionLanguages,
            toggleLanguage,
        )

        viewModel.onEvent(ExtensionFilterScreenEvent.Toggle("ja"))

        coVerify { toggleLanguage.await("ja") }
    }
}
