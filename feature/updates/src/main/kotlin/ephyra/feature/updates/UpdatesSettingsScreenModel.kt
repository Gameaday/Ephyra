package ephyra.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.core.common.util.lang.launchIO
import ephyra.domain.updates.service.UpdatesPreferences
import javax.inject.Inject

@HiltViewModel
class UpdatesSettingsScreenModel @Inject constructor(
    val updatesPreferences: UpdatesPreferences,
) : ViewModel() {

    fun onEvent(event: UpdatesSettingsScreenEvent) {
        when (event) {
            is UpdatesSettingsScreenEvent.ToggleFilter -> toggleFilter(event.preference)
        }
    }

    private fun toggleFilter(preference: (UpdatesPreferences) -> Preference<TriState>) {
        viewModelScope.launchIO {
            preference(updatesPreferences).getAndSet {
                it.next()
            }
        }
    }
}
