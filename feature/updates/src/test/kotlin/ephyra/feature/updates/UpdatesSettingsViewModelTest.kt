package ephyra.feature.updates

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.domain.updates.service.UpdatesPreferences
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesSettingsViewModelTest {

    private val updatesPreferences: UpdatesPreferences = mockk(relaxed = true)
    private val filterDownloadedPref: Preference<TriState> = mockk(relaxed = true)
    private val filterExcludedScanlatorsPref: Preference<Boolean> = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { updatesPreferences.filterDownloaded() } returns filterDownloadedPref
        every { updatesPreferences.filterExcludedScanlators() } returns filterExcludedScanlatorsPref
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is State object`() = runTest {
        val viewModel = UpdatesSettingsViewModel(updatesPreferences)
        viewModel.state.test {
            assertEquals(UpdatesSettingsViewModel.State, awaitItem())
        }
    }

    @Test
    fun `ToggleFilter toggles TriState preference`() = runTest {
        coEvery { filterDownloadedPref.get() } returns TriState.DISABLED
        val viewModel = UpdatesSettingsViewModel(updatesPreferences)

        viewModel.onEvent(UpdatesSettingsScreenEvent.ToggleFilter(UpdatesPreferences::filterDownloaded))

        coVerify { filterDownloadedPref.set(TriState.ENABLED_IS) }
    }

    @Test
    fun `ToggleExcludedScanlators toggles boolean preference`() = runTest {
        coEvery { filterExcludedScanlatorsPref.get() } returns false
        val viewModel = UpdatesSettingsViewModel(updatesPreferences)

        viewModel.onEvent(UpdatesSettingsScreenEvent.ToggleExcludedScanlators)

        coVerify { filterExcludedScanlatorsPref.set(true) }
    }
}
