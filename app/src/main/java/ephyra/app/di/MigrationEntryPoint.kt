package ephyra.app.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.storage.service.StoragePreferences

/**
 * Hilt EntryPoint providing preference and repository singletons required during
 * database and user preference migrations.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MigrationEntryPoint {
    fun libraryPreferences(): LibraryPreferences
    fun downloadPreferences(): DownloadPreferences
    fun getCategories(): GetCategories
    fun backupPreferences(): BackupPreferences
    fun storagePreferences(): StoragePreferences
    fun sourcePreferences(): SourcePreferences
    fun extensionRepoRepository(): ExtensionRepoRepository
}
