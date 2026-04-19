package ephyra.app.installer

import android.content.Context
import ephyra.core.common.util.system.hasMiuiPackageInstaller
import ephyra.core.common.util.system.isShizukuInstalled
import ephyra.domain.base.BasePreferences.ExtensionInstaller
import ephyra.domain.base.InstallerCapabilityProvider

class AndroidInstallerCapabilityProvider(private val context: Context) : InstallerCapabilityProvider {
    override fun isAvailable(installer: ExtensionInstaller): Boolean = when (installer) {
        ExtensionInstaller.PACKAGEINSTALLER -> !context.hasMiuiPackageInstaller
        ExtensionInstaller.SHIZUKU -> context.isShizukuInstalled
        else -> true
    }
}
