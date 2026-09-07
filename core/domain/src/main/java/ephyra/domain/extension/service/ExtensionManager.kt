package ephyra.domain.extension.service

import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.model.InstallStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ExtensionManager {

    val isInitialized: StateFlow<Boolean>

    val installedExtensionsFlow: StateFlow<List<Extension.Installed>>

    val availableExtensionsFlow: StateFlow<List<Extension.Available>>

    val untrustedExtensionsFlow: StateFlow<List<Extension.Untrusted>>

    /**
     * Recognized extensions that could not be loaded (bad lib version, unsigned,
     * broken classes, ...). Every installed-but-broken extension must appear here —
     * the Extensions screen surfaces this list so a problematic extension is never
     * indistinguishable from one the app cannot see at all.
     */
    val failedExtensionsFlow: StateFlow<List<Extension.Failed>>

    fun getExtensionPackage(sourceId: Long): String?

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?>

    fun getAppIconForSource(sourceId: Long): Any?

    /** Returns the icon drawable (as [Any?]) for [pkgName] at the given [density]. */
    fun getExtensionIcon(pkgName: String, density: Int): Any?

    suspend fun findAvailableExtensions()

    fun reloadExtensions()

    suspend fun trust(extension: Extension.Untrusted)

    fun uninstallExtension(extension: Extension)

    /** Uninstalls an extension APK known only by its package name (e.g. failed loads). */
    fun uninstallExtensionByPkgName(pkgName: String)

    fun installExtension(extension: Extension.Available): Flow<InstallStep>

    fun updateExtension(extension: Extension.Installed): Flow<InstallStep>

    fun cancelInstallUpdateExtension(extension: Extension)
}
