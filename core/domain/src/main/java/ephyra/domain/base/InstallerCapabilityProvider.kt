package ephyra.domain.base

import ephyra.domain.base.BasePreferences.ExtensionInstaller

/**
 * Checks whether a given installer is available on this device.
 * Implementations live in :app and use Android APIs for MIUI and Shizuku detection.
 */
interface InstallerCapabilityProvider {
    fun isAvailable(installer: ExtensionInstaller): Boolean
}
