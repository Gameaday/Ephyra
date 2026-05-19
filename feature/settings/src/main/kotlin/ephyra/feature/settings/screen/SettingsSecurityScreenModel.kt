package ephyra.feature.settings.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences
import javax.inject.Inject

@HiltViewModel
class SettingsSecurityScreenModel @Inject constructor(
    val securityPreferences: SecurityPreferences,
    val privacyPreferences: PrivacyPreferences,
) : ViewModel()
