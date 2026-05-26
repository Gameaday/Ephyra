package ephyra.core.migration

import android.app.Application
import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import ephyra.app.di.ScreenEntryPoint
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.storage.service.StoragePreferences

/**
 * Hilt-native MigrationContext resolving migration dependencies dynamically via ScreenEntryPoint
 * without relying on a legacy compat-shim service locator.
 */
class MigrationContext(val context: Context, val dryrun: Boolean) {

    inline fun <reified T : Any> get(): T? {
        val app = context.applicationContext as? Application
        if (T::class.java == Application::class.java || T::class.java == Context::class.java) {
            return app as? T ?: context as? T
        }
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ScreenEntryPoint::class.java,
            )
            val result = when (T::class.java) {
                LibraryPreferences::class.java -> entryPoint.libraryPreferences()
                DownloadPreferences::class.java -> entryPoint.downloadPreferences()
                GetCategories::class.java -> entryPoint.getCategories()
                BackupPreferences::class.java -> entryPoint.backupPreferences()
                StoragePreferences::class.java -> entryPoint.storagePreferences()
                SourcePreferences::class.java -> entryPoint.sourcePreferences()
                ExtensionRepoRepository::class.java -> entryPoint.extensionRepoRepository()
                else -> null
            }
            result as? T
        } catch (e: Exception) {
            null
        }
    }
}
