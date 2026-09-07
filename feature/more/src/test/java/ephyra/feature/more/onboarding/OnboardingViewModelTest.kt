package ephyra.feature.more.onboarding

import ephyra.core.common.preference.Preference
import ephyra.domain.base.BasePreferences
import ephyra.domain.storage.service.StoragePreferences
import ephyra.presentation.core.ui.AppInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OnboardingViewModelTest {

    private val basePreferences = mockk<BasePreferences>()
    private val storagePreferences = mockk<StoragePreferences>()
    private val appInfo = mockk<AppInfo>()

    private val shownOnboardingPref = mockk<Preference<Boolean>>(relaxed = true)
    private val storageDirPref = mockk<Preference<String>>(relaxed = true)

    @BeforeEach
    fun setUp() {
        every { appInfo.telemetryIncluded } returns true
        every { basePreferences.shownOnboardingFlow() } returns shownOnboardingPref
        every { storagePreferences.baseStorageDirectory() } returns storageDirPref
    }

    @Test
    fun `initializes with preferences and app info telemetry`() {
        val viewModel = OnboardingViewModel(basePreferences, storagePreferences, appInfo)

        assertEquals(shownOnboardingPref, viewModel.shownOnboardingFlow)
        assertEquals(storageDirPref, viewModel.storageDirPref)
        assertTrue(viewModel.telemetryIncluded)
    }

    @Test
    fun `finishOnboarding sets shownOnboardingFlow preference to true`() {
        val viewModel = OnboardingViewModel(basePreferences, storagePreferences, appInfo)

        viewModel.finishOnboarding()

        verify(exactly = 1) { shownOnboardingPref.set(true) }
    }
}
