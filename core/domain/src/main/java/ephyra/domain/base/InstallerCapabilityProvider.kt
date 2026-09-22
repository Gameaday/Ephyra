package ephyra.domain.base

import ephyra.domain.base.BasePreferences.ExtensionInstaller

/**
 * Checks whether a given installer is available on this device.
 * Implementations live in :app and report which install strategies the sandboxed extension
 * pipeline supports.
 */
interface InstallerCapabilityProvider {
    fun isAvailable(installer: ExtensionInstaller): Boolean
}
