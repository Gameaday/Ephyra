package ephyra.domain.base

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.preference.getEnum
import ephyra.domain.base.BasePreferences.ExtensionInstaller
import kotlinx.coroutines.CoroutineScope

class ExtensionInstallerPreference(
    private val capabilityProvider: InstallerCapabilityProvider,
    preferenceStore: PreferenceStore,
) : Preference<ExtensionInstaller> {

    private val basePref = preferenceStore.getEnum(key(), defaultValue())

    override fun key() = "extension_installer"

    val entries
        get() = ExtensionInstaller.entries.run {
            if (!capabilityProvider.isAvailable(ExtensionInstaller.PACKAGEINSTALLER)) {
                filter { it != ExtensionInstaller.PACKAGEINSTALLER }
            } else {
                toList()
            }
        }

    override fun defaultValue() = if (!capabilityProvider.isAvailable(ExtensionInstaller.PACKAGEINSTALLER)) {
        ExtensionInstaller.PRIVATE
    } else {
        ExtensionInstaller.PACKAGEINSTALLER
    }

    private fun check(value: ExtensionInstaller): ExtensionInstaller {
        when (value) {
            ExtensionInstaller.PACKAGEINSTALLER -> {
                if (!capabilityProvider.isAvailable(
                        ExtensionInstaller.PACKAGEINSTALLER,
                    )
                ) {
                    return ExtensionInstaller.PRIVATE
                }
            }

            ExtensionInstaller.SHIZUKU -> {
                if (!capabilityProvider.isAvailable(ExtensionInstaller.SHIZUKU)) return defaultValue()
            }

            else -> {}
        }
        return value
    }

    override fun getSync(): ExtensionInstaller {
        return check(basePref.getSync())
    }

    override suspend fun get(): ExtensionInstaller {
        val value = basePref.get()
        val checkedValue = check(value)
        if (value != checkedValue) {
            basePref.set(checkedValue)
        }
        return checkedValue
    }

    override fun set(value: ExtensionInstaller) {
        basePref.set(check(value))
    }

    override fun isSet() = basePref.isSet()

    override fun delete() = basePref.delete()

    override fun changes() = basePref.changes()

    override fun stateIn(scope: CoroutineScope) = basePref.stateIn(scope)
}
