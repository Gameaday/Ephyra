package ephyra.app.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.core.common.di.CoreContainer

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenEntryPoint {
    // Singletons
    fun basePreferences(): ephyra.domain.base.BasePreferences
    fun coverCache(): ephyra.data.cache.CoverCache
    fun extensionReposScreenFactory(): ephyra.presentation.core.ui.ExtensionReposScreenFactory
    fun migrationConfigScreenFactory(): ephyra.presentation.core.ui.MigrationConfigScreenFactory
    fun sourceManager(): ephyra.domain.source.service.SourceManager
    fun networkHelper(): eu.kanade.tachiyomi.network.NetworkHelper
    fun updatesRepository(): ephyra.domain.updates.repository.UpdatesRepository
    fun libraryPreferences(): ephyra.domain.library.service.LibraryPreferences
    fun extensionManager(): ephyra.app.extension.ExtensionManager
    fun trustExtension(): ephyra.domain.extension.interactor.TrustExtension
    
    // Interactors / Use Cases
    fun updateMangaNotes(): ephyra.domain.manga.interactor.UpdateMangaNotes
    fun getCategories(): ephyra.domain.category.interactor.GetCategories
    fun deleteCategory(): ephyra.domain.category.interactor.DeleteCategory
    fun reorderCategory(): ephyra.domain.category.interactor.ReorderCategory
    fun renameCategory(): ephyra.domain.category.interactor.RenameCategory
    fun createCategoryWithName(): ephyra.domain.category.interactor.CreateCategoryWithName
    fun setDisplayMode(): ephyra.domain.category.interactor.SetDisplayMode
    fun setSortModeForCategory(): ephyra.domain.category.interactor.SetSortModeForCategory
    fun resetCategoryFlags(): ephyra.domain.category.interactor.ResetCategoryFlags
    fun getEnabledSources(): ephyra.domain.source.interactor.GetEnabledSources
    fun getLanguagesWithSources(): ephyra.domain.source.interactor.GetLanguagesWithSources
    fun getSourcesWithFavoriteCount(): ephyra.domain.source.interactor.GetSourcesWithFavoriteCount
    fun getSourcesWithNonLibraryManga(): ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga
    fun toggleSource(): ephyra.domain.source.interactor.ToggleSource
    fun toggleSourcePin(): ephyra.domain.source.interactor.ToggleSourcePin
    fun toggleLanguage(): ephyra.domain.source.interactor.ToggleLanguage
    fun getExtensionsByType(): ephyra.domain.extension.interactor.GetExtensionsByType
    fun getExtensionSources(): ephyra.domain.extension.interactor.GetExtensionSources
    fun getExtensionLanguages(): ephyra.domain.extension.interactor.GetExtensionLanguages
    fun getUpdates(): ephyra.domain.updates.interactor.GetUpdates
    fun getLibraryManga(): ephyra.domain.manga.interactor.GetLibraryManga
    fun getNextChapters(): ephyra.domain.history.interactor.GetNextChapters
    fun getChaptersByMangaId(): ephyra.domain.chapter.interactor.GetChaptersByMangaId
    fun getBookmarkedChaptersByMangaId(): ephyra.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
    fun setReadStatus(): ephyra.domain.chapter.interactor.SetReadStatus
    fun updateManga(): ephyra.domain.manga.interactor.UpdateManga
    fun setMangaCategories(): ephyra.domain.category.interactor.SetMangaCategories
    fun downloadManager(): ephyra.core.download.DownloadManager
    fun downloadCache(): ephyra.core.download.DownloadCache
    fun trackerManager(): ephyra.domain.track.service.TrackerManager
    
    // Preferences / other dependencies
    fun readerPreferences(): ephyra.domain.reader.service.ReaderPreferences
    fun trackPreferences(): ephyra.domain.track.service.TrackPreferences
    fun downloadPreferences(): ephyra.domain.download.service.DownloadPreferences
    fun backupPreferences(): ephyra.domain.backup.service.BackupPreferences
    fun storagePreferences(): ephyra.domain.storage.service.StoragePreferences
    fun uiPreferences(): ephyra.domain.ui.UiPreferences
    fun preferenceStore(): ephyra.core.common.preference.PreferenceStore
    fun securityPreferences(): ephyra.core.common.core.security.SecurityPreferences
    fun backupScheduler(): ephyra.domain.backup.service.BackupScheduler
    fun restoreScheduler(): ephyra.domain.backup.service.RestoreScheduler
    fun backupFileValidator(): ephyra.domain.backup.service.BackupFileValidator
    fun secureActivityDelegate(): ephyra.presentation.core.ui.delegate.SecureActivityDelegate
    fun themingDelegate(): ephyra.presentation.core.ui.delegate.ThemingDelegate
    
    // Extension repos
    fun getExtensionRepoCount(): ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount

    // Serialization
    fun json(): kotlinx.serialization.json.Json
    fun xml(): nl.adaptivity.xmlutil.serialization.XML
}

fun initializeCoreContainer(context: Context) {
    CoreContainer.init(context)
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, ScreenEntryPoint::class.java)

    // Singletons
    CoreContainer.register(ephyra.domain.base.BasePreferences::class.java) { entryPoint.basePreferences() }
    CoreContainer.register(ephyra.data.cache.CoverCache::class.java) { entryPoint.coverCache() }
    CoreContainer.register(ephyra.presentation.core.ui.ExtensionReposScreenFactory::class.java) { entryPoint.extensionReposScreenFactory() }
    CoreContainer.register(ephyra.presentation.core.ui.MigrationConfigScreenFactory::class.java) { entryPoint.migrationConfigScreenFactory() }
    CoreContainer.register(ephyra.domain.source.service.SourceManager::class.java) { entryPoint.sourceManager() }
    CoreContainer.register(eu.kanade.tachiyomi.network.NetworkHelper::class.java) { entryPoint.networkHelper() }
    CoreContainer.register(ephyra.domain.updates.repository.UpdatesRepository::class.java) { entryPoint.updatesRepository() }
    CoreContainer.register(ephyra.domain.library.service.LibraryPreferences::class.java) { entryPoint.libraryPreferences() }
    CoreContainer.register(ephyra.app.extension.ExtensionManager::class.java) { entryPoint.extensionManager() }
    CoreContainer.register(ephyra.domain.extension.interactor.TrustExtension::class.java) { entryPoint.trustExtension() }
    
    // Interactors / Use Cases
    CoreContainer.register(ephyra.domain.manga.interactor.UpdateMangaNotes::class.java) { entryPoint.updateMangaNotes() }
    CoreContainer.register(ephyra.domain.category.interactor.GetCategories::class.java) { entryPoint.getCategories() }
    CoreContainer.register(ephyra.domain.category.interactor.DeleteCategory::class.java) { entryPoint.deleteCategory() }
    CoreContainer.register(ephyra.domain.category.interactor.ReorderCategory::class.java) { entryPoint.reorderCategory() }
    CoreContainer.register(ephyra.domain.category.interactor.RenameCategory::class.java) { entryPoint.renameCategory() }
    CoreContainer.register(ephyra.domain.category.interactor.CreateCategoryWithName::class.java) { entryPoint.createCategoryWithName() }
    CoreContainer.register(ephyra.domain.category.interactor.SetDisplayMode::class.java) { entryPoint.setDisplayMode() }
    CoreContainer.register(ephyra.domain.category.interactor.SetSortModeForCategory::class.java) { entryPoint.setSortModeForCategory() }
    CoreContainer.register(ephyra.domain.category.interactor.ResetCategoryFlags::class.java) { entryPoint.resetCategoryFlags() }
    CoreContainer.register(ephyra.domain.source.interactor.GetEnabledSources::class.java) { entryPoint.getEnabledSources() }
    CoreContainer.register(ephyra.domain.source.interactor.GetLanguagesWithSources::class.java) { entryPoint.getLanguagesWithSources() }
    CoreContainer.register(ephyra.domain.source.interactor.GetSourcesWithFavoriteCount::class.java) { entryPoint.getSourcesWithFavoriteCount() }
    CoreContainer.register(ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga::class.java) { entryPoint.getSourcesWithNonLibraryManga() }
    CoreContainer.register(ephyra.domain.source.interactor.ToggleSource::class.java) { entryPoint.toggleSource() }
    CoreContainer.register(ephyra.domain.source.interactor.ToggleSourcePin::class.java) { entryPoint.toggleSourcePin() }
    CoreContainer.register(ephyra.domain.source.interactor.ToggleLanguage::class.java) { entryPoint.toggleLanguage() }
    CoreContainer.register(ephyra.domain.extension.interactor.GetExtensionsByType::class.java) { entryPoint.getExtensionsByType() }
    CoreContainer.register(ephyra.domain.extension.interactor.GetExtensionSources::class.java) { entryPoint.getExtensionSources() }
    CoreContainer.register(ephyra.domain.extension.interactor.GetExtensionLanguages::class.java) { entryPoint.getExtensionLanguages() }
    CoreContainer.register(ephyra.domain.updates.interactor.GetUpdates::class.java) { entryPoint.getUpdates() }
    CoreContainer.register(ephyra.domain.manga.interactor.GetLibraryManga::class.java) { entryPoint.getLibraryManga() }
    CoreContainer.register(ephyra.domain.history.interactor.GetNextChapters::class.java) { entryPoint.getNextChapters() }
    CoreContainer.register(ephyra.domain.chapter.interactor.GetChaptersByMangaId::class.java) { entryPoint.getChaptersByMangaId() }
    CoreContainer.register(ephyra.domain.chapter.interactor.GetBookmarkedChaptersByMangaId::class.java) { entryPoint.getBookmarkedChaptersByMangaId() }
    CoreContainer.register(ephyra.domain.chapter.interactor.SetReadStatus::class.java) { entryPoint.setReadStatus() }
    CoreContainer.register(ephyra.domain.manga.interactor.UpdateManga::class.java) { entryPoint.updateManga() }
    CoreContainer.register(ephyra.domain.category.interactor.SetMangaCategories::class.java) { entryPoint.setMangaCategories() }
    CoreContainer.register(ephyra.core.download.DownloadManager::class.java) { entryPoint.downloadManager() }
    CoreContainer.register(ephyra.core.download.DownloadCache::class.java) { entryPoint.downloadCache() }
    CoreContainer.register(ephyra.domain.track.service.TrackerManager::class.java) { entryPoint.trackerManager() }
    
    // Preferences / other dependencies
    CoreContainer.register(ephyra.domain.reader.service.ReaderPreferences::class.java) { entryPoint.readerPreferences() }
    CoreContainer.register(ephyra.domain.track.service.TrackPreferences::class.java) { entryPoint.trackPreferences() }
    CoreContainer.register(ephyra.domain.download.service.DownloadPreferences::class.java) { entryPoint.downloadPreferences() }
    CoreContainer.register(ephyra.domain.backup.service.BackupPreferences::class.java) { entryPoint.backupPreferences() }
    CoreContainer.register(ephyra.domain.storage.service.StoragePreferences::class.java) { entryPoint.storagePreferences() }
    CoreContainer.register(ephyra.domain.ui.UiPreferences::class.java) { entryPoint.uiPreferences() }
    CoreContainer.register(ephyra.core.common.preference.PreferenceStore::class.java) { entryPoint.preferenceStore() }
    CoreContainer.register(ephyra.core.common.core.security.SecurityPreferences::class.java) { entryPoint.securityPreferences() }
    CoreContainer.register(ephyra.domain.backup.service.BackupScheduler::class.java) { entryPoint.backupScheduler() }
    CoreContainer.register(ephyra.domain.backup.service.RestoreScheduler::class.java) { entryPoint.restoreScheduler() }
    CoreContainer.register(ephyra.domain.backup.service.BackupFileValidator::class.java) { entryPoint.backupFileValidator() }
    CoreContainer.register(ephyra.presentation.core.ui.delegate.SecureActivityDelegate::class.java) { entryPoint.secureActivityDelegate() }
    CoreContainer.register(ephyra.presentation.core.ui.delegate.ThemingDelegate::class.java) { entryPoint.themingDelegate() }
    
    // Extension repos
    CoreContainer.register(ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount::class.java) { entryPoint.getExtensionRepoCount() }

    // Serialization
    CoreContainer.register(kotlinx.serialization.json.Json::class.java) { entryPoint.json() }
    CoreContainer.register(nl.adaptivity.xmlutil.serialization.XML::class.java) { entryPoint.xml() }
}
