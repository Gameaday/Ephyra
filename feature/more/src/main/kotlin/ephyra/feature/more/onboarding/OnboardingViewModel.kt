package ephyra.feature.more.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.Preference
import ephyra.domain.base.BasePreferences
import ephyra.domain.storage.service.StoragePreferences
import ephyra.presentation.core.ui.AppInfo
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val basePreferences: BasePreferences,
    private val storagePreferences: StoragePreferences,
    private val appInfo: AppInfo,
) : ViewModel() {

    val shownOnboardingFlow: Preference<Boolean> = basePreferences.shownOnboardingFlow()
    val storageDirPref: Preference<String> = storagePreferences.baseStorageDirectory()
    val telemetryIncluded: Boolean = appInfo.telemetryIncluded

    fun finishOnboarding() {
        basePreferences.shownOnboardingFlow().set(true)
    }
}
