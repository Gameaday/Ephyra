package ephyra.feature.more.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.Preference
import ephyra.domain.base.BasePreferences
import ephyra.domain.storage.service.StoragePreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.AppInfo
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val basePreferences: BasePreferences,
    private val storagePreferences: StoragePreferences,
    private val appInfo: AppInfo,
) : BaseUdfViewModel<OnboardingState, OnboardingEvent, Nothing>(
    OnboardingState(
        shownOnboarding = basePreferences.shownOnboardingFlow().getSync(),
        telemetryIncluded = appInfo.telemetryIncluded,
    ),
) {

    val storageDirPref: Preference<String> = storagePreferences.baseStorageDirectory()

    init {
        viewModelScope.launch {
            basePreferences.shownOnboardingFlow().changes().collectLatest { shown ->
                updateState { it.copy(shownOnboarding = shown) }
            }
        }
    }

    override fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.FinishOnboarding -> {
                basePreferences.shownOnboardingFlow().set(true)
                updateState { it.copy(shownOnboarding = true) }
            }
        }
    }
}

@Immutable
data class OnboardingState(
    val shownOnboarding: Boolean = false,
    val telemetryIncluded: Boolean = false,
)

sealed interface OnboardingEvent {
    data object FinishOnboarding : OnboardingEvent
}
