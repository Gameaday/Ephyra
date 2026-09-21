package ephyra.app.installer

import android.content.Context
import ephyra.domain.base.BasePreferences.ExtensionInstaller
import ephyra.domain.base.InstallerCapabilityProvider

/**
 * Sandboxed in-app installer capability provider.
 * Disables system-level PackageInstaller and Shizuku in favor of private in-app DEX execution.
 */
class AndroidInstallerCapabilityProvider(private val context: Context) : InstallerCapabilityProvider {
    override fun isAvailable(installer: ExtensionInstaller): Boolean = when (installer) {
        ExtensionInstaller.PACKAGEINSTALLER -> false
        ExtensionInstaller.PRIVATE -> true
    }
}
