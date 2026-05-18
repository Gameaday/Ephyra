package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.ui.UiPreferences
import javax.inject.Inject

@HiltViewModel
class SettingsAppearanceScreenModel @Inject constructor(
    val uiPreferences: UiPreferences,
) : ViewModel()
