package ephyra.presentation.core.util

import androidx.compose.runtime.compositionLocalOf
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.domain.ui.UiPreferences

val LocalUiPreferences = compositionLocalOf<UiPreferences> {
    error("No UiPreferences provided")
}

val LocalPrivacyPreferences = compositionLocalOf<PrivacyPreferences> {
    error("No PrivacyPreferences provided")
}
