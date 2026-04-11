package ephyra.feature.settings.screen

import cafe.adriel.voyager.core.model.ScreenModel
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences

class SettingsSecurityScreenModel(
    val securityPreferences: SecurityPreferences,
    val privacyPreferences: PrivacyPreferences,
) : ScreenModel
