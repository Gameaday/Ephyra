package ephyra.app.installer

import ephyra.domain.base.BasePreferences.ExtensionInstaller
import ephyra.domain.base.InstallerCapabilityProvider

/**
 * Sandboxed in-app installer capability provider.
 *
 * Only the private in-app DEX installer is available. The system-level PackageInstaller and the
 * Shizuku-backed privileged flow it required were removed together with the legacy APK extension
 * system, so no Android APIs (and therefore no `Context`) are needed to answer this.
 */
class AndroidInstallerCapabilityProvider : InstallerCapabilityProvider {
    override fun isAvailable(installer: ExtensionInstaller): Boolean = when (installer) {
        ExtensionInstaller.PACKAGEINSTALLER -> false
        ExtensionInstaller.PRIVATE -> true
    }
}
