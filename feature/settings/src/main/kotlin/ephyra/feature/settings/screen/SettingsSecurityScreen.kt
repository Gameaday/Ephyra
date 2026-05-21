package ephyra.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.i18n.stringResource
import ephyra.feature.settings.Preference
import ephyra.presentation.core.i18n.pluralStringResource
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.AppInfo
import ephyra.presentation.core.util.collectAsState
import ephyra.presentation.core.util.system.AuthenticatorUtil.authenticate
import ephyra.presentation.core.util.system.AuthenticatorUtil.isAuthenticationSupported
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = ephyra.app.core.common.R.string.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val screenModel = hiltViewModel<SettingsSecurityScreenModel>()
        val securityPreferences = screenModel.securityPreferences
        val privacyPreferences = screenModel.privacyPreferences
        val appInfo = screenModel.appInfo

        return buildList(2) {
            add(getSecurityGroup(securityPreferences))
            if (!appInfo.telemetryIncluded) return@buildList
            add(getFirebaseGroup(privacyPreferences))
        }
    }

    @Composable
    private fun getSecurityGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val authSupported = remember { context.isAuthenticationSupported() }
        val useAuthPref = securityPreferences.useAuthenticator()
        val useAuth by useAuthPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_security),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useAuthPref,
                    title = stringResource(ephyra.app.core.common.R.string.lock_with_biometrics),
                    enabled = authSupported,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(ephyra.app.core.common.R.string.lock_with_biometrics),
                        )
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.lockAppAfter(),
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(ephyra.app.core.common.R.string.lock_never)
                                0 -> stringResource(ephyra.app.core.common.R.string.lock_always)
                                else -> pluralStringResource(ephyra.app.core.common.R.plurals.lock_after_mins, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.lock_when_idle),
                    enabled = authSupported && useAuth,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(ephyra.app.core.common.R.string.lock_when_idle),
                        )
                    },
                ),

                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.hideNotificationContent(),
                    title = stringResource(ephyra.app.core.common.R.string.hide_notification_content),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.secureScreen(),
                    entries = SecurityPreferences.SecureScreenMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(ephyra.app.core.common.R.string.secure_screen),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(ephyra.app.core.common.R.string.secure_screen_summary)),
            ),
        )
    }

    @Composable
    private fun getFirebaseGroup(
        privacyPreferences: PrivacyPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(ephyra.app.core.common.R.string.pref_firebase),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = privacyPreferences.crashlytics(),
                    title = stringResource(ephyra.app.core.common.R.string.onboarding_permission_crashlytics),
                    subtitle = stringResource(ephyra.app.core.common.R.string.onboarding_permission_crashlytics_description),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = privacyPreferences.analytics(),
                    title = stringResource(ephyra.app.core.common.R.string.onboarding_permission_analytics),
                    subtitle = stringResource(ephyra.app.core.common.R.string.onboarding_permission_analytics_description),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(ephyra.app.core.common.R.string.firebase_summary)),
            ),
        )
    }
}

private val LockAfterValues = persistentListOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
