package ephyra.feature.updates

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.common.preference.getAndSet
import ephyra.domain.updates.service.UpdatesPreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesSettingsViewModel @Inject constructor(
    private val preferences: UpdatesPreferences,
) : BaseUdfViewModel<UpdatesSettingsViewModel.State, UpdatesSettingsScreenEvent, Nothing>(State) {

    val updatesPreferences: UpdatesPreferences get() = preferences

    override fun onEvent(event: UpdatesSettingsScreenEvent) {
        when (event) {
            is UpdatesSettingsScreenEvent.ToggleFilter -> toggleFilter(event.preference)
            UpdatesSettingsScreenEvent.ToggleExcludedScanlators -> toggleExcludedScanlators()
        }
    }

    private fun toggleFilter(preference: (UpdatesPreferences) -> Preference<TriState>) {
        viewModelScope.launch {
            preference(preferences).getAndSet {
                it.next()
            }
        }
    }

    private fun toggleExcludedScanlators() {
        viewModelScope.launch {
            val pref = preferences.filterExcludedScanlators()
            pref.set(!pref.get())
        }
    }

    @Immutable
    data object State
}
