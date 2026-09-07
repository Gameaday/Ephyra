package ephyra.core.migration

import android.app.Application
import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import ephyra.app.di.MigrationEntryPoint
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.storage.service.StoragePreferences

/**
 * Hilt-native MigrationContext resolving migration dependencies dynamically via MigrationEntryPoint
 * without relying on a legacy compat-shim service locator.
 */
open class MigrationContext(val context: Context, val dryrun: Boolean) {

    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> getInstance(clazz: Class<T>): T? {
        val app = context.applicationContext as? Application
        if (clazz == Application::class.java || clazz == Context::class.java) {
            return app as? T ?: context as? T
        }
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                MigrationEntryPoint::class.java,
            )
            val result = when (clazz) {
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

    inline fun <reified T : Any> get(): T? = getInstance(T::class.java)
}
