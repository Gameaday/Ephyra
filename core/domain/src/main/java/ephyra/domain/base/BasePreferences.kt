package ephyra.domain.base

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.system.GLUtil

class BasePreferences(
    private val capabilityProvider: InstallerCapabilityProvider,
    private val preferenceStore: PreferenceStore,
) {

    fun downloadedOnly() = preferenceStore.getBoolean(
        Preference.appStateKey("pref_downloaded_only"),
        false,
    )

    fun incognitoMode() = preferenceStore.getBoolean(Preference.appStateKey("incognito_mode"), false)

    fun extensionInstaller() = ExtensionInstallerPreference(capabilityProvider, preferenceStore)

    fun shownOnboardingFlow() = preferenceStore.getBoolean(Preference.appStateKey("onboarding_complete"), false)

    enum class ExtensionInstaller(val titleRes: Int, val requiresSystemPermission: Boolean) {
        LEGACY(ephyra.i18n.R.string.ext_installer_legacy, true),
        PACKAGEINSTALLER(ephyra.i18n.R.string.ext_installer_packageinstaller, true),
        SHIZUKU(ephyra.i18n.R.string.ext_installer_shizuku, false),
        PRIVATE(ephyra.i18n.R.string.ext_installer_private, false),
    }

    fun displayProfile() = preferenceStore.getString("pref_display_profile_key", "")

    fun hardwareBitmapThreshold() = preferenceStore.getInt("pref_hardware_bitmap_threshold", GLUtil.SAFE_TEXTURE_LIMIT)

    fun alwaysDecodeLongStripWithSSIV() = preferenceStore.getBoolean("pref_always_decode_long_strip_with_ssiv", false)
}
