package ephyra.core.common.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.reflect.KClass

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenEntryPoint {
    // Singletons
    fun basePreferences(): ephyra.domain.base.BasePreferences
    fun coverCache(): ephyra.app.data.cache.CoverCache
    fun mangaScreenModelFactory(): ephyra.feature.manga.MangaScreenModelFactory
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
    fun downloadManager(): ephyra.feature.download.DownloadManager
    fun downloadCache(): ephyra.app.data.download.DownloadCache
    fun trackerManager(): ephyra.app.data.track.TrackerManager
    
    // Preferences / other dependencies
    fun readerPreferences(): ephyra.feature.reader.setting.ReaderPreferences
    fun trackPreferences(): ephyra.domain.track.service.TrackPreferences
    fun downloadPreferences(): ephyra.domain.download.service.DownloadPreferences
    fun backupPreferences(): ephyra.domain.backup.service.BackupPreferences
    fun storagePreferences(): ephyra.domain.storage.service.StoragePreferences
    fun uiPreferences(): ephyra.domain.ui.UiPreferences
    fun preferenceStore(): ephyra.core.common.preference.PreferenceStore
    fun securityPreferences(): ephyra.core.common.core.security.SecurityPreferences
    fun backupScheduler(): ephyra.domain.backup.service.BackupScheduler
    fun restoreScheduler(): ephyra.domain.backup.service.RestoreScheduler
    fun backupFileValidator(): ephyra.app.data.backup.BackupFileValidator
    fun secureActivityDelegate(): ephyra.presentation.core.ui.delegate.SecureActivityDelegate
    fun themingDelegate(): ephyra.presentation.core.ui.delegate.ThemingDelegate
    
    // Extension repos
    fun getExtensionRepoCount(): ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount

    // Serialization
    fun json(): kotlinx.serialization.json.Json
    fun xml(): nl.adaptivity.xmlutil.serialization.XML
}

object CoreContainer {
    lateinit var applicationContext: Context

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: Class<T>): T {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ScreenEntryPoint::class.java)
        val result = when (clazz) {
            ephyra.domain.base.BasePreferences::class.java -> entryPoint.basePreferences()
            ephyra.app.data.cache.CoverCache::class.java -> entryPoint.coverCache()
            ephyra.feature.manga.MangaScreenModelFactory::class.java -> entryPoint.mangaScreenModelFactory()
            ephyra.presentation.core.ui.ExtensionReposScreenFactory::class.java -> entryPoint.extensionReposScreenFactory()
            ephyra.presentation.core.ui.MigrationConfigScreenFactory::class.java -> entryPoint.migrationConfigScreenFactory()
            ephyra.domain.source.service.SourceManager::class.java -> entryPoint.sourceManager()
            eu.kanade.tachiyomi.network.NetworkHelper::class.java -> entryPoint.networkHelper()
            ephyra.domain.updates.repository.UpdatesRepository::class.java -> entryPoint.updatesRepository()
            ephyra.domain.library.service.LibraryPreferences::class.java -> entryPoint.libraryPreferences()
            ephyra.app.extension.ExtensionManager::class.java -> entryPoint.extensionManager()
            ephyra.domain.extension.interactor.TrustExtension::class.java -> entryPoint.trustExtension()
            
            // Interactors
            ephyra.domain.manga.interactor.UpdateMangaNotes::class.java -> entryPoint.updateMangaNotes()
            ephyra.domain.category.interactor.GetCategories::class.java -> entryPoint.getCategories()
            ephyra.domain.category.interactor.DeleteCategory::class.java -> entryPoint.deleteCategory()
            ephyra.domain.category.interactor.ReorderCategory::class.java -> entryPoint.reorderCategory()
            ephyra.domain.category.interactor.RenameCategory::class.java -> entryPoint.renameCategory()
            ephyra.domain.category.interactor.CreateCategoryWithName::class.java -> entryPoint.createCategoryWithName()
            ephyra.domain.category.interactor.SetDisplayMode::class.java -> entryPoint.setDisplayMode()
            ephyra.domain.category.interactor.SetSortModeForCategory::class.java -> entryPoint.setSortModeForCategory()
            ephyra.domain.category.interactor.ResetCategoryFlags::class.java -> entryPoint.resetCategoryFlags()
            ephyra.domain.source.interactor.GetEnabledSources::class.java -> entryPoint.getEnabledSources()
            ephyra.domain.source.interactor.GetLanguagesWithSources::class.java -> entryPoint.getLanguagesWithSources()
            ephyra.domain.source.interactor.GetSourcesWithFavoriteCount::class.java -> entryPoint.getSourcesWithFavoriteCount()
            ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga::class.java -> entryPoint.getSourcesWithNonLibraryManga()
            ephyra.domain.source.interactor.ToggleSource::class.java -> entryPoint.toggleSource()
            ephyra.domain.source.interactor.ToggleSourcePin::class.java -> entryPoint.toggleSourcePin()
            ephyra.domain.source.interactor.ToggleLanguage::class.java -> entryPoint.toggleLanguage()
            ephyra.domain.extension.interactor.GetExtensionsByType::class.java -> entryPoint.getExtensionsByType()
            ephyra.domain.extension.interactor.GetExtensionSources::class.java -> entryPoint.getExtensionSources()
            ephyra.domain.extension.interactor.GetExtensionLanguages::class.java -> entryPoint.getExtensionLanguages()
            ephyra.domain.updates.interactor.GetUpdates::class.java -> entryPoint.getUpdates()
            ephyra.domain.manga.interactor.GetLibraryManga::class.java -> entryPoint.getLibraryManga()
            ephyra.domain.history.interactor.GetNextChapters::class.java -> entryPoint.getNextChapters()
            ephyra.domain.chapter.interactor.GetChaptersByMangaId::class.java -> entryPoint.getChaptersByMangaId()
            ephyra.domain.chapter.interactor.GetBookmarkedChaptersByMangaId::class.java -> entryPoint.getBookmarkedChaptersByMangaId()
            ephyra.domain.chapter.interactor.SetReadStatus::class.java -> entryPoint.setReadStatus()
            ephyra.domain.manga.interactor.UpdateManga::class.java -> entryPoint.updateManga()
            ephyra.domain.category.interactor.SetMangaCategories::class.java -> entryPoint.setMangaCategories()
            ephyra.feature.download.DownloadManager::class.java -> entryPoint.downloadManager()
            ephyra.app.data.download.DownloadCache::class.java -> entryPoint.downloadCache()
            ephyra.app.data.track.TrackerManager::class.java -> entryPoint.trackerManager()
            
            // Preferences / other dependencies
            ephyra.feature.reader.setting.ReaderPreferences::class.java -> entryPoint.readerPreferences()
            ephyra.domain.track.service.TrackPreferences::class.java -> entryPoint.trackPreferences()
            ephyra.domain.download.service.DownloadPreferences::class.java -> entryPoint.downloadPreferences()
            ephyra.domain.backup.service.BackupPreferences::class.java -> entryPoint.backupPreferences()
            ephyra.domain.storage.service.StoragePreferences::class.java -> entryPoint.storagePreferences()
            ephyra.domain.ui.UiPreferences::class.java -> entryPoint.uiPreferences()
            ephyra.core.common.preference.PreferenceStore::class.java -> entryPoint.preferenceStore()
            ephyra.core.common.core.security.SecurityPreferences::class.java -> entryPoint.securityPreferences()
            ephyra.domain.backup.service.BackupScheduler::class.java -> entryPoint.backupScheduler()
            ephyra.domain.backup.service.RestoreScheduler::class.java -> entryPoint.restoreScheduler()
            ephyra.app.data.backup.BackupFileValidator::class.java -> entryPoint.backupFileValidator()
            ephyra.presentation.core.ui.delegate.SecureActivityDelegate::class.java -> entryPoint.secureActivityDelegate()
            ephyra.presentation.core.ui.delegate.ThemingDelegate::class.java -> entryPoint.themingDelegate()
            
            // Extension repos
            ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount::class.java -> entryPoint.getExtensionRepoCount()

            // Serialization
            kotlinx.serialization.json.Json::class.java -> entryPoint.json()
            nl.adaptivity.xmlutil.serialization.XML::class.java -> entryPoint.xml()
            
            else -> throw IllegalArgumentException("No Hilt EntryPoint mapping for requested class: ${clazz.name}")
        }
        return result as T
    }

    inline fun <reified T : Any> get(): T {
        return get(T::class.java)
    }

    fun <T : Any> get(clazz: KClass<T>): T {
        return get(clazz.java)
    }
}
