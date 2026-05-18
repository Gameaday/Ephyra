package ephyra.app.di

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ephyra.core.common.di.CoreContainer
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.preference.DataStorePreferenceStore
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.core.common.notification.NotificationManager
import ephyra.app.data.notification.NotificationManagerImpl
import ephyra.data.room.EphyraDatabase
import ephyra.data.category.CategoryRepositoryImpl
import ephyra.data.chapter.ChapterRepositoryImpl
import ephyra.data.history.HistoryRepositoryImpl
import ephyra.data.manga.MangaRepositoryImpl
import ephyra.data.release.ReleaseServiceImpl
import ephyra.data.source.SourceRepositoryImpl
import ephyra.data.source.StubSourceRepositoryImpl
import ephyra.data.track.TrackRepositoryImpl
import ephyra.data.updates.UpdatesRepositoryImpl
import ephyra.data.repository.ExtensionRepoRepositoryImpl
import ephyra.domain.category.repository.CategoryRepository
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.release.service.ReleaseService
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.track.repository.TrackRepository
import ephyra.domain.updates.repository.UpdatesRepository
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.base.BasePreferences
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.ui.UiPreferences
import ephyra.app.core.security.PrivacyPreferences
import ephyra.app.core.security.SecurityPreferences
import eu.kanade.tachiyomi.network.NetworkPreferences
import ephyra.feature.reader.setting.ReaderPreferences
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.storage.service.StoragePreferences
import ephyra.domain.updates.service.UpdatesPreferences
import ephyra.domain.storage.service.StorageManager
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.network.JavaScriptEngine
import ephyra.domain.jellyfin.interactor.SyncJellyfin
import ephyra.app.data.track.jellyfin.SyncJellyfinImpl
import ephyra.feature.manga.interactor.MangaInfoInteractor
import ephyra.feature.manga.interactor.MangaChapterInteractor
import ephyra.feature.manga.interactor.MangaTrackInteractor
import ephyra.feature.manga.MangaScreenModelFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.AndroidSourceManager
import ephyra.domain.source.service.SourceManager
import ephyra.app.extension.ExtensionManager
import ephyra.app.extension.api.ExtensionApi
import ephyra.app.extension.util.ExtensionLoader
import ephyra.app.extension.util.ExtensionInstaller
import ephyra.app.util.CrashLogUtil
import ephyra.app.data.cache.ChapterCache
import ephyra.app.data.cache.CoverCache
import ephyra.app.util.system.isDebugBuildType
import ephyra.app.data.download.DownloadStore
import ephyra.app.data.download.DownloadProvider
import ephyra.app.data.download.DownloadCache
import ephyra.app.data.download.DownloadManager
import ephyra.app.data.download.Downloader
import ephyra.app.data.download.DownloadPendingDeleter
import ephyra.app.data.download.DownloadNotifier
import ephyra.app.data.track.TrackerManager
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.app.data.backup.BackupDecoder
import ephyra.app.data.backup.BackupFileValidator
import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.backup.create.BackupCreator
import ephyra.app.data.backup.create.creators.CategoriesBackupCreator
import ephyra.app.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.app.data.backup.create.creators.MangaBackupCreator
import ephyra.app.data.backup.create.creators.PreferenceBackupCreator
import ephyra.app.data.backup.create.creators.SourcesBackupCreator
import ephyra.app.data.backup.restore.BackupRestorer
import ephyra.app.data.backup.restore.restorers.CategoriesRestorer
import ephyra.app.data.updater.AppUpdateChecker
import ephyra.app.data.backup.restore.restorers.PreferenceRestorer
import ephyra.app.data.backup.restore.restorers.MangaRestorer
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.saver.ImageSaver
import ephyra.app.data.coil.MangaCoverKeyer
import ephyra.app.data.coil.MangaKeyer
import ephyra.presentation.core.util.Navigator
import ephyra.app.util.NavigatorImpl
import ephyra.presentation.core.ui.delegate.ThemingDelegate
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl

// Interactors
import ephyra.domain.category.interactor.CreateCategoryWithName
import ephyra.domain.category.interactor.DeleteCategory
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.RenameCategory
import ephyra.domain.category.interactor.ReorderCategory
import ephyra.domain.category.interactor.ResetCategoryFlags
import ephyra.domain.category.interactor.SetDisplayMode
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.category.interactor.SetSortModeForCategory
import ephyra.domain.category.interactor.UpdateCategory
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetDeadFavorites
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.upcoming.interactor.GetUpcomingManga
import ephyra.domain.manga.interactor.ResetViewerFlags
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.manga.interactor.SetMangaViewerFlags
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.manga.interactor.DeleteNonLibraryManga
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.RefreshTracks
import ephyra.domain.track.interactor.DeleteTrack
import ephyra.domain.track.interactor.GetTracksPerManga
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.interactor.SyncChapterProgressWithTrack
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.interactor.LinkTrackedMangaToAuthority
import ephyra.domain.track.interactor.MatchUnlinkedManga
import ephyra.domain.track.interactor.RefreshCanonicalMetadata
import ephyra.domain.chapter.interactor.GetChapter
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import ephyra.domain.chapter.interactor.GetChapterByUrlAndMangaId
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.interactor.ShouldUpdateDbChapter
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.interactor.GetAvailableScanlators
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.history.interactor.GetHistory
import ephyra.domain.history.interactor.UpsertHistory
import ephyra.domain.history.interactor.RemoveHistory
import ephyra.domain.history.interactor.RemoveResettedHistory
import ephyra.domain.history.interactor.GetTotalReadDuration
import ephyra.domain.download.interactor.DeleteDownload
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.interactor.GetExtensionSources
import ephyra.domain.extension.interactor.GetExtensionLanguages
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.source.interactor.GetEnabledSources
import ephyra.domain.source.interactor.GetLanguagesWithSources
import ephyra.domain.source.interactor.GetRemoteManga
import ephyra.domain.source.interactor.GetSourcesWithFavoriteCount
import ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga
import ephyra.domain.source.interactor.SetMigrateSorting
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.interactor.ToggleSourcePin
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.ReplaceExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.source.interactor.ToggleIncognito
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.extensionrepo.service.ExtensionRepoService

// Serialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlVersion

class AppDependencyContainer(val application: Application) {

    // 1. Core Serialization Caches
    val json by lazy {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
    
    val xml by lazy {
        XML {
            defaultPolicy { ignoreUnknownChildren() }
            autoPolymorphic = true
            xmlDeclMode = XmlDeclMode.Charset
            indent = 2
            xmlVersion = XmlVersion.XML10
        }
    }
    
    val protobuf by lazy { ProtoBuf }

    // 2. Database and DAOs
    val database: EphyraDatabase by lazy {
        Room.databaseBuilder(
            context = application,
            klass = EphyraDatabase::class.java,
            name = "tachiyomi.db"
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.query("PRAGMA foreign_keys = ON").close()
                    db.query("PRAGMA journal_mode = WAL").close()
                    db.query("PRAGMA synchronous = NORMAL").close()
                }
            })
            .build()
    }

    val mangaDao by lazy { database.mangaDao() }
    val chapterDao by lazy { database.chapterDao() }
    val categoryDao by lazy { database.categoryDao() }
    val historyDao by lazy { database.historyDao() }
    val trackDao by lazy { database.trackDao() }
    val updateDao by lazy { database.updateDao() }
    val extensionRepoDao by lazy { database.extensionRepoDao() }
    val sourceDao by lazy { database.sourceDao() }

    // 3. Shared Preferences
    val preferenceStore: PreferenceStore by lazy { DataStorePreferenceStore(application) }
    val networkPreferences by lazy { NetworkPreferences(preferenceStore, isDebugBuildType) }
    val sourcePreferences by lazy { SourcePreferences(preferenceStore) }
    val securityPreferences by lazy { SecurityPreferences(preferenceStore) }
    val privacyPreferences by lazy { PrivacyPreferences(preferenceStore) }
    val libraryPreferences by lazy { LibraryPreferences(preferenceStore) }
    val updatesPreferences by lazy { UpdatesPreferences(preferenceStore) }
    val readerPreferences by lazy { ReaderPreferences(preferenceStore) }
    val trackPreferences by lazy { TrackPreferences(preferenceStore) }
    val downloadPreferences by lazy { DownloadPreferences(preferenceStore) }
    val backupPreferences by lazy { BackupPreferences(preferenceStore) }
    val androidStorageFolderProvider by lazy { AndroidStorageFolderProvider(application) }
    val storagePreferences by lazy { StoragePreferences(androidStorageFolderProvider, preferenceStore) }
    val uiPreferences by lazy { UiPreferences(preferenceStore) }
    val basePreferences by lazy { BasePreferences(application, preferenceStore) }

    // 4. Repositories
    val categoryRepository: CategoryRepository by lazy { CategoryRepositoryImpl(categoryDao) }
    val mangaRepository: MangaRepository by lazy { MangaRepositoryImpl(mangaDao) }
    val chapterRepository: ChapterRepository by lazy { ChapterRepositoryImpl(chapterDao) }
    val historyRepository: HistoryRepository by lazy { HistoryRepositoryImpl(historyDao) }
    val updatesRepository: UpdatesRepository by lazy { UpdatesRepositoryImpl(updateDao) }
    val sourceRepository: SourceRepository by lazy { SourceRepositoryImpl(sourceDao, extensionManager, sourcePreferences) }
    val stubSourceRepository: StubSourceRepository by lazy { StubSourceRepositoryImpl(sourceDao) }
    val extensionRepoRepository: ExtensionRepoRepository by lazy { ExtensionRepoRepositoryImpl(extensionRepoDao) }
    val trackRepository: TrackRepository by lazy { TrackRepositoryImpl(trackDao) }

    // 5. Shared Core Services & Managers
    val networkHelper by lazy { NetworkHelper(application, networkPreferences) }
    val javaScriptEngine by lazy { JavaScriptEngine(application) }
    
    val trustExtension by lazy { TrustExtension(preferenceStore, sourcePreferences) }
    val extensionLoader by lazy { ExtensionLoader(sourcePreferences, trustExtension) }
    val extensionInstaller by lazy { ExtensionInstaller(application, sourcePreferences, trustExtension, securityPreferences) }
    val extensionApi by lazy { ExtensionApi(networkHelper, preferenceStore, getExtensionRepo(), updateExtensionRepo(), securityPreferences, extensionLoader, json) }
    val extensionManager by lazy { ExtensionManager(application, sourcePreferences, trustExtension, securityPreferences, extensionLoader, extensionApi) }
    
    val sourceManager: SourceManager by lazy { AndroidSourceManager(application, extensionManager, stubSourceRepository, localSourceFileSystem, localCoverManager, downloadManager) }
    val localSourceFileSystem by lazy { LocalSourceFileSystem(androidStorageFolderProvider) }
    val localCoverManager by lazy { LocalCoverManager(application, androidStorageFolderProvider) }
    val storageManager by lazy { StorageManager(application, storagePreferences) }
    
    val downloadStore by lazy { DownloadStore(application, sourceManager, json, updatesPreferences, sourcePreferences) }
    val downloadProvider by lazy { DownloadProvider(application, sourceManager, storageManager) }
    val downloadCache by lazy { DownloadCache(application, provider = downloadProvider, sourceManager = sourceManager, preferences = downloadPreferences, storageManager = storageManager) }
    val downloadManager by lazy { DownloadManager(application, downloadProvider, downloadStore, downloadCache, sourceManager, updatesPreferences, sourcePreferences, downloadPreferences) }
    val downloader by lazy { Downloader(application, downloadProvider, downloadStore, downloadCache, sourceManager, updatesPreferences, sourcePreferences, downloadPreferences, trackerManager, networkHelper, json, basePreferences) }
    val downloadPendingDeleter by lazy { DownloadPendingDeleter(application, sourceManager) }
    val downloadNotifier by lazy { DownloadNotifier(application, imageSaver) }
    
    val trackerManager by lazy { TrackerManager(application, trackPreferences, json, basePreferences, trackRepository, networkHelper) }
    val delayedTrackingStore by lazy { DelayedTrackingStore(application) }

    val imageSaver by lazy { ImageSaver(application) }
    val chapterCache by lazy { ChapterCache(application, json) }
    val coverCache by lazy { CoverCache(application) }
    val crashLogUtil by lazy { CrashLogUtil(application, storageManager) }
    val navigator: Navigator by lazy { NavigatorImpl() }
    val notificationManager: NotificationManager by lazy { NotificationManagerImpl(application) }
    val themingDelegate by lazy { ThemingDelegateImpl(uiPreferences) }
    val secureActivityDelegate by lazy { SecureActivityDelegateImpl(basePreferences, privacyPreferences) }

    // 6. Backup & Restore
    val backupDecoder by lazy { BackupDecoder(application, json) }
    val backupFileValidator by lazy { BackupFileValidator(application, json, sourcePreferences, extensionManager) }
    val categoriesBackupCreator by lazy { CategoriesBackupCreator(categoryRepository) }
    val mangaBackupCreator by lazy { MangaBackupCreator(mangaRepository, chapterRepository, historyRepository) }
    val preferenceBackupCreator by lazy { PreferenceBackupCreator(preferenceStore, sourcePreferences) }
    val extensionRepoBackupCreator by lazy { ExtensionRepoBackupCreator(extensionRepoRepository) }
    val sourcesBackupCreator by lazy { SourcesBackupCreator(sourcePreferences) }
    val backupCreator by lazy { BackupCreator(application, categoriesBackupCreator, mangaBackupCreator, preferenceBackupCreator, extensionRepoBackupCreator, sourcesBackupCreator, preferenceStore, json, parser = json, backupPreferences = backupPreferences) }
    
    val categoriesRestorer by lazy { CategoriesRestorer(categoryRepository, categoryDao, database) }
    val backupRestorer by lazy { BackupRestorer(application, categoriesRestorer, preferenceRestorer, mangaRestorer, backupPreferences) }
    val appUpdateChecker by lazy { AppUpdateChecker(networkHelper) }
    val preferenceRestorer by lazy { PreferenceRestorer(application, preferenceStore, sourcePreferences, json, parser = json) }
    val mangaRestorer by lazy { MangaRestorer(mangaRepository, categoryRepository, chapterRepository, historyRepository, trackRepository, sourceManager, backupPreferences, database) }
    val libraryUpdateNotifier by lazy { LibraryUpdateNotifier(application, mangaRepository, sourceManager) }
    val backupNotifier by lazy { BackupNotifier(application, imageSaver) }
    val releaseService: ReleaseService by lazy { ReleaseServiceImpl(networkHelper, json) }

    // 7. Interactors (Use Cases)
    fun getCategories() = GetCategories(categoryRepository)
    fun resetCategoryFlags() = ResetCategoryFlags(categoryRepository, categoryDao)
    fun setDisplayMode() = SetDisplayMode(categoryRepository)
    fun setSortModeForCategory() = SetSortModeForCategory(categoryRepository, categoryDao)
    fun createCategoryWithName() = CreateCategoryWithName(categoryRepository, categoryDao)
    fun renameCategory() = RenameCategory(categoryRepository)
    fun reorderCategory() = ReorderCategory(categoryRepository)
    fun updateCategory() = UpdateCategory(categoryRepository)
    fun deleteCategory() = DeleteCategory(categoryRepository, categoryDao, database)

    fun getDuplicateLibraryManga() = GetDuplicateLibraryManga(mangaRepository)
    fun getFavorites() = GetFavorites(mangaRepository)
    fun getFavoritesByCanonicalId() = GetFavoritesByCanonicalId(mangaRepository)
    fun getDeadFavorites() = GetDeadFavorites(mangaRepository)
    fun getLibraryManga() = GetLibraryManga(mangaRepository)
    fun getMangaWithChapters() = GetMangaWithChapters(mangaRepository, chapterRepository)
    fun getMangaByUrlAndSourceId() = GetMangaByUrlAndSourceId(mangaRepository)
    fun getManga() = GetManga(mangaRepository)
    fun getNextChapters() = GetNextChapters(chapterRepository, historyRepository, updatesRepository)
    fun getUpcomingManga() = GetUpcomingManga(mangaRepository)
    fun resetViewerFlags() = ResetViewerFlags(mangaRepository)
    fun setMangaChapterFlags() = SetMangaChapterFlags(mangaRepository)
    fun fetchInterval() = FetchInterval(mangaRepository)
    fun setMangaDefaultChapterFlags() = SetMangaDefaultChapterFlags(mangaRepository, chapterRepository, database)
    fun setMangaViewerFlags() = SetMangaViewerFlags(mangaRepository)
    fun networkToLocalManga() = NetworkToLocalManga(mangaRepository)
    fun updateManga() = UpdateManga(mangaRepository, chapterRepository, categoryDao, updatesRepository, database, coverCache)
    fun findContentSource() = FindContentSource(mangaRepository, sourceManager)
    fun updateMangaNotes() = UpdateMangaNotes(mangaRepository)
    fun setMangaCategories() = SetMangaCategories(categoryRepository)
    fun getExcludedScanlators() = GetExcludedScanlators(mangaRepository)
    fun setExcludedScanlators() = SetExcludedScanlators(mangaRepository)
    fun deleteNonLibraryManga() = DeleteNonLibraryManga(mangaRepository)
    fun migrateManga() = MigrateMangaUseCase(mangaRepository, categoryRepository, chapterRepository, historyRepository, trackRepository, sourceManager, database, trackChapter(), deleteDownload())

    fun getApplicationRelease() = GetApplicationRelease(releaseService, basePreferences)

    fun trackChapter() = TrackChapter(trackRepository, trackerManager, delayedTrackingStore, basePreferences)
    fun addTracks() = AddTracks(trackRepository, trackerManager, trackDao, delayedTrackingStore, basePreferences, trackChapter())
    fun refreshTracks() = RefreshTracks(trackRepository, trackerManager, trackDao, delayedTrackingStore)
    fun deleteTrack() = DeleteTrack(trackRepository)
    fun getTracksPerManga() = GetTracksPerManga(trackRepository)
    fun getTracks() = GetTracks(trackRepository)
    fun insertTrack() = InsertTrack(trackRepository)
    fun syncChapterProgressWithTrack() = SyncChapterProgressWithTrack(trackRepository, trackerManager, delayedTrackingStore)
    fun trackerListImporter() = TrackerListImporter(trackRepository, trackerManager, trackDao, delayedTrackingStore)
    fun linkTrackedMangaToAuthority() = LinkTrackedMangaToAuthority(trackRepository, trackerManager)
    fun matchUnlinkedManga() = MatchUnlinkedManga(trackRepository, trackerManager, trackDao, delayedTrackingStore)
    fun refreshCanonicalMetadata() = RefreshCanonicalMetadata(trackRepository, trackerManager, trackDao, delayedTrackingStore)

    fun getChapter() = GetChapter(chapterRepository)
    fun getChaptersByMangaId() = GetChaptersByMangaId(chapterRepository)
    fun getBookmarkedChaptersByMangaId() = GetBookmarkedChaptersByMangaId(chapterRepository)
    fun getChapterByUrlAndMangaId() = GetChapterByUrlAndMangaId(chapterRepository)
    fun updateChapter() = UpdateChapter(chapterRepository)
    fun setReadStatus() = SetReadStatus(chapterRepository, database, updatesRepository, trackChapter())
    fun shouldUpdateDbChapter() = ShouldUpdateDbChapter()
    fun syncChaptersWithSource() = SyncChaptersWithSource(mangaRepository, chapterRepository, sourceManager, database, updatesRepository, getManga(), getChaptersByMangaId(), setMangaDefaultChapterFlags(), shouldUpdateDbChapter(), updateManga())
    fun getAvailableScanlators() = GetAvailableScanlators(chapterRepository)
    fun filterChaptersForDownload() = FilterChaptersForDownload(sourceManager, downloadManager, getChaptersByMangaId())
    fun generateAuthorityChapters() = GenerateAuthorityChapters(chapterRepository)

    fun getHistory() = GetHistory(historyRepository)
    fun upsertHistory() = UpsertHistory(historyRepository)
    fun removeHistory() = RemoveHistory(historyRepository)
    fun removeResettedHistory() = RemoveResettedHistory(historyRepository)
    fun getTotalReadDuration() = GetTotalReadDuration(historyRepository)

    fun deleteDownload() = DeleteDownload(sourceManager, downloadManager)

    fun getExtensionsByType() = GetExtensionsByType(extensionManager, sourcePreferences)
    fun getExtensionSources() = GetExtensionSources(extensionManager)
    fun getExtensionLanguages() = GetExtensionLanguages(extensionManager, sourcePreferences)

    fun getUpdates() = GetUpdates(updatesRepository)

    fun getEnabledSources() = GetEnabledSources(sourceRepository, sourcePreferences)
    fun getLanguagesWithSources() = GetLanguagesWithSources(sourceRepository, sourcePreferences)
    fun getRemoteManga() = GetRemoteManga(sourceRepository)
    fun getSourcesWithFavoriteCount() = GetSourcesWithFavoriteCount(sourceRepository, sourcePreferences)
    fun getSourcesWithNonLibraryManga() = GetSourcesWithNonLibraryManga(sourceRepository)
    fun setMigrateSorting() = SetMigrateSorting(sourceRepository)
    fun toggleLanguage() = ToggleLanguage(sourceRepository)
    fun toggleSource() = ToggleSource(sourceRepository)
    fun toggleSourcePin() = ToggleSourcePin(sourceRepository)
    fun trustExtension() = TrustExtension(preferenceStore, sourcePreferences)

    fun getExtensionRepo() = GetExtensionRepo(extensionRepoRepository)
    fun getExtensionRepoCount() = GetExtensionRepoCount(extensionRepoRepository)
    fun createExtensionRepo() = CreateExtensionRepo(extensionRepoRepository, extensionRepoService)
    fun deleteExtensionRepo() = DeleteExtensionRepo(extensionRepoRepository)
    fun replaceExtensionRepo() = ReplaceExtensionRepo(extensionRepoRepository)
    fun updateExtensionRepo() = UpdateExtensionRepo(extensionRepoRepository, extensionRepoService)
    fun extensionRepoService() = ExtensionRepoService(networkHelper, json)
    val extensionRepoService by lazy { ExtensionRepoService(networkHelper, json) }

    fun toggleIncognito() = ToggleIncognito(basePreferences)
    fun getIncognitoState() = GetIncognitoState(basePreferences, trackerManager, sourceManager)

    init {
        // Register all singletons and interactors into our pure CoreContainer DI registry at startup
        CoreContainer.register(EphyraDatabase::class.java, database)
        CoreContainer.register(PreferenceStore::class.java, preferenceStore)
        CoreContainer.register(NetworkPreferences::class.java, networkPreferences)
        CoreContainer.register(SourcePreferences::class.java, sourcePreferences)
        CoreContainer.register(SecurityPreferences::class.java, securityPreferences)
        CoreContainer.register(PrivacyPreferences::class.java, privacyPreferences)
        CoreContainer.register(LibraryPreferences::class.java, libraryPreferences)
        CoreContainer.register(UpdatesPreferences::class.java, updatesPreferences)
        CoreContainer.register(ReaderPreferences::class.java, readerPreferences)
        CoreContainer.register(TrackPreferences::class.java, trackPreferences)
        CoreContainer.register(DownloadPreferences::class.java, downloadPreferences)
        CoreContainer.register(BackupPreferences::class.java, backupPreferences)
        CoreContainer.register(StoragePreferences::class.java, storagePreferences)
        CoreContainer.register(UiPreferences::class.java, uiPreferences)
        CoreContainer.register(BasePreferences::class.java, basePreferences)

        CoreContainer.register(CategoryRepository::class.java, categoryRepository)
        CoreContainer.register(MangaRepository::class.java, mangaRepository)
        CoreContainer.register(ChapterRepository::class.java, chapterRepository)
        CoreContainer.register(HistoryRepository::class.java, historyRepository)
        CoreContainer.register(UpdatesRepository::class.java, updatesRepository)
        CoreContainer.register(SourceRepository::class.java, sourceRepository)
        CoreContainer.register(StubSourceRepository::class.java, stubSourceRepository)
        CoreContainer.register(ExtensionRepoRepository::class.java, extensionRepoRepository)
        CoreContainer.register(TrackRepository::class.java, trackRepository)

        CoreContainer.register(NetworkHelper::class.java, networkHelper)
        CoreContainer.register(JavaScriptEngine::class.java, javaScriptEngine)
        CoreContainer.register(ExtensionApi::class.java, extensionApi)
        CoreContainer.register(ExtensionLoader::class.java, extensionLoader)
        CoreContainer.register(ExtensionInstaller::class.java, extensionInstaller)
        CoreContainer.register(ExtensionManager::class.java, extensionManager)
        CoreContainer.register(SourceManager::class.java, sourceManager)
        CoreContainer.register(LocalSourceFileSystem::class.java, localSourceFileSystem)
        CoreContainer.register(LocalCoverManager::class.java, localCoverManager)
        CoreContainer.register(StorageManager::class.java, storageManager)

        CoreContainer.register(DownloadStore::class.java, downloadStore)
        CoreContainer.register(DownloadProvider::class.java, downloadProvider)
        CoreContainer.register(DownloadCache::class.java, downloadCache)
        CoreContainer.register(DownloadManager::class.java, downloadManager)
        CoreContainer.register(Downloader::class.java, downloader)
        CoreContainer.register(DownloadPendingDeleter::class.java, downloadPendingDeleter)
        CoreContainer.register(DownloadNotifier::class.java, downloadNotifier)

        CoreContainer.register(TrackerManager::class.java, trackerManager)
        CoreContainer.register(DelayedTrackingStore::class.java, delayedTrackingStore)

        CoreContainer.register(ImageSaver::class.java, imageSaver)
        CoreContainer.register(ChapterCache::class.java, chapterCache)
        CoreContainer.register(CoverCache::class.java, coverCache)
        CoreContainer.register(CrashLogUtil::class.java, crashLogUtil)
        CoreContainer.register(Navigator::class.java, navigator)
        CoreContainer.register(NotificationManager::class.java, notificationManager)
        CoreContainer.register(ThemingDelegate::class.java, themingDelegate)
        CoreContainer.register(SecureActivityDelegate::class.java, secureActivityDelegate)

        CoreContainer.register(BackupDecoder::class.java, backupDecoder)
        CoreContainer.register(BackupFileValidator::class.java, backupFileValidator)
        CoreContainer.register(CategoriesBackupCreator::class.java, categoriesBackupCreator)
        CoreContainer.register(MangaBackupCreator::class.java, mangaBackupCreator)
        CoreContainer.register(PreferenceBackupCreator::class.java, preferenceBackupCreator)
        CoreContainer.register(ExtensionRepoBackupCreator::class.java, extensionRepoBackupCreator)
        CoreContainer.register(SourcesBackupCreator::class.java, sourcesBackupCreator)
        CoreContainer.register(BackupCreator::class.java, backupCreator)

        CoreContainer.register(CategoriesRestorer::class.java, categoriesRestorer)
        CoreContainer.register(BackupRestorer::class.java, backupRestorer)
        CoreContainer.register(AppUpdateChecker::class.java, appUpdateChecker)
        CoreContainer.register(PreferenceRestorer::class.java, preferenceRestorer)
        CoreContainer.register(MangaRestorer::class.java, mangaRestorer)
        CoreContainer.register(LibraryUpdateNotifier::class.java, libraryUpdateNotifier)
        CoreContainer.register(BackupNotifier::class.java, backupNotifier)
        CoreContainer.register(ReleaseService::class.java, releaseService)

        // Register interactors as factories by registering a getter function or register them directly
        // Features retrieve them dynamically as instances
        CoreContainer.register(GetCategories::class.java, getCategories())
        CoreContainer.register(ResetCategoryFlags::class.java, resetCategoryFlags())
        CoreContainer.register(SetDisplayMode::class.java, setDisplayMode())
        CoreContainer.register(SetSortModeForCategory::class.java, setSortModeForCategory())
        CoreContainer.register(CreateCategoryWithName::class.java, createCategoryWithName())
        CoreContainer.register(RenameCategory::class.java, renameCategory())
        CoreContainer.register(ReorderCategory::class.java, reorderCategory())
        CoreContainer.register(UpdateCategory::class.java, updateCategory())
        CoreContainer.register(DeleteCategory::class.java, deleteCategory())

        CoreContainer.register(GetDuplicateLibraryManga::class.java, getDuplicateLibraryManga())
        CoreContainer.register(GetFavorites::class.java, getFavorites())
        CoreContainer.register(GetFavoritesByCanonicalId::class.java, getFavoritesByCanonicalId())
        CoreContainer.register(GetDeadFavorites::class.java, getDeadFavorites())
        CoreContainer.register(GetLibraryManga::class.java, getLibraryManga())
        CoreContainer.register(GetMangaWithChapters::class.java, getMangaWithChapters())
        CoreContainer.register(GetMangaByUrlAndSourceId::class.java, getMangaByUrlAndSourceId())
        CoreContainer.register(GetManga::class.java, getManga())
        CoreContainer.register(GetNextChapters::class.java, getNextChapters())
        CoreContainer.register(GetUpcomingManga::class.java, getUpcomingManga())
        CoreContainer.register(ResetViewerFlags::class.java, resetViewerFlags())
        CoreContainer.register(SetMangaChapterFlags::class.java, setMangaChapterFlags())
        CoreContainer.register(FetchInterval::class.java, fetchInterval())
        CoreContainer.register(SetMangaDefaultChapterFlags::class.java, setMangaDefaultChapterFlags())
        CoreContainer.register(SetMangaViewerFlags::class.java, setMangaViewerFlags())
        CoreContainer.register(NetworkToLocalManga::class.java, networkToLocalManga())
        CoreContainer.register(UpdateManga::class.java, updateManga())
        CoreContainer.register(FindContentSource::class.java, findContentSource())
        CoreContainer.register(UpdateMangaNotes::class.java, updateMangaNotes())
        CoreContainer.register(SetMangaCategories::class.java, setMangaCategories())
        CoreContainer.register(GetExcludedScanlators::class.java, getExcludedScanlators())
        CoreContainer.register(SetExcludedScanlators::class.java, setExcludedScanlators())
        CoreContainer.register(DeleteNonLibraryManga::class.java, deleteNonLibraryManga())
        CoreContainer.register(MigrateMangaUseCase::class.java, migrateManga())

        CoreContainer.register(GetApplicationRelease::class.java, getApplicationRelease())

        CoreContainer.register(TrackChapter::class.java, trackChapter())
        CoreContainer.register(AddTracks::class.java, addTracks())
        CoreContainer.register(RefreshTracks::class.java, refreshTracks())
        CoreContainer.register(DeleteTrack::class.java, deleteTrack())
        CoreContainer.register(GetTracksPerManga::class.java, getTracksPerManga())
        CoreContainer.register(GetTracks::class.java, getTracks())
        CoreContainer.register(InsertTrack::class.java, insertTrack())
        CoreContainer.register(SyncChapterProgressWithTrack::class.java, syncChapterProgressWithTrack())
        CoreContainer.register(TrackerListImporter::class.java, trackerListImporter())
        CoreContainer.register(LinkTrackedMangaToAuthority::class.java, linkTrackedMangaToAuthority())
        CoreContainer.register(MatchUnlinkedManga::class.java, matchUnlinkedManga())
        CoreContainer.register(RefreshCanonicalMetadata::class.java, refreshCanonicalMetadata())

        CoreContainer.register(GetChapter::class.java, getChapter())
        CoreContainer.register(GetChaptersByMangaId::class.java, getChaptersByMangaId())
        CoreContainer.register(GetBookmarkedChaptersByMangaId::class.java, getBookmarkedChaptersByMangaId())
        CoreContainer.register(GetChapterByUrlAndMangaId::class.java, getChapterByUrlAndMangaId())
        CoreContainer.register(UpdateChapter::class.java, updateChapter())
        CoreContainer.register(SetReadStatus::class.java, setReadStatus())
        CoreContainer.register(ShouldUpdateDbChapter::class.java, shouldUpdateDbChapter())
        CoreContainer.register(SyncChaptersWithSource::class.java, syncChaptersWithSource())
        CoreContainer.register(GetAvailableScanlators::class.java, getAvailableScanlators())
        CoreContainer.register(FilterChaptersForDownload::class.java, filterChaptersForDownload())
        CoreContainer.register(GenerateAuthorityChapters::class.java, generateAuthorityChapters())

        CoreContainer.register(GetHistory::class.java, getHistory())
        CoreContainer.register(UpsertHistory::class.java, upsertHistory())
        CoreContainer.register(RemoveHistory::class.java, removeHistory())
        CoreContainer.register(RemoveResettedHistory::class.java, removeResettedHistory())
        CoreContainer.register(GetTotalReadDuration::class.java, totalReadDuration = getTotalReadDuration())

        CoreContainer.register(DeleteDownload::class.java, deleteDownload())

        val syncJellyfin = SyncJellyfinImpl()
        CoreContainer.register(SyncJellyfin::class.java, syncJellyfin)

        val mangaInfoInteractor = MangaInfoInteractor(
            updateManga = CoreContainer.get(),
            mangaRepository = CoreContainer.get(),
            refreshCanonical = CoreContainer.get(),
            matchUnlinkedManga = CoreContainer.get(),
            syncJellyfin = syncJellyfin,
            setExcludedScanlators = CoreContainer.get(),
            setMangaCategories = CoreContainer.get(),
        )
        CoreContainer.register(MangaInfoInteractor::class.java, mangaInfoInteractor)

        val mangaChapterInteractor = MangaChapterInteractor(
            setMangaChapterFlags = CoreContainer.get(),
            setMangaDefaultChapterFlags = CoreContainer.get(),
            setReadStatus = CoreContainer.get(),
            updateChapter = CoreContainer.get(),
            libraryPreferences = CoreContainer.get(),
            filterChaptersForDownload = CoreContainer.get(),
            syncChaptersWithSource = CoreContainer.get(),
            downloadManager = CoreContainer.get(),
        )
        CoreContainer.register(MangaChapterInteractor::class.java, mangaChapterInteractor)

        val mangaTrackInteractor = MangaTrackInteractor(
            getTracks = CoreContainer.get(),
            addTracks = CoreContainer.get(),
            refreshCanonical = CoreContainer.get(),
            matchUnlinkedManga = CoreContainer.get(),
            trackChapter = CoreContainer.get(),
            trackPreferences = CoreContainer.get(),
            trackerManager = CoreContainer.get(),
            refreshTracks = CoreContainer.get(),
        )
        CoreContainer.register(MangaTrackInteractor::class.java, mangaTrackInteractor)

        val mangaScreenModelFactory = MangaScreenModelFactory(
            context = application,
            basePreferences = CoreContainer.get(),
            uiPreferences = CoreContainer.get(),
            libraryPreferences = CoreContainer.get(),
            readerPreferences = CoreContainer.get(),
            downloadManager = CoreContainer.get(),
            downloadCache = CoreContainer.get(),
            getMangaAndChapters = CoreContainer.get(),
            getDuplicateLibraryManga = CoreContainer.get(),
            getAvailableScanlators = CoreContainer.get(),
            getExcludedScanlators = CoreContainer.get(),
            getCategories = CoreContainer.get(),
            sourceManager = CoreContainer.get(),
            mangaInfoInteractor = mangaInfoInteractor,
            mangaChapterInteractor = mangaChapterInteractor,
            mangaTrackInteractor = mangaTrackInteractor,
            syncJellyfin = syncJellyfin,
        )
        CoreContainer.register(MangaScreenModelFactory::class.java, mangaScreenModelFactory)

        CoreContainer.register(GetExtensionsByType::class.java, getExtensionsByType())
        CoreContainer.register(GetExtensionSources::class.java, getExtensionSources())
        CoreContainer.register(GetExtensionLanguages::class.java, getExtensionLanguages())

        CoreContainer.register(GetUpdates::class.java, getUpdates())

        CoreContainer.register(GetEnabledSources::class.java, getEnabledSources())
        CoreContainer.register(GetLanguagesWithSources::class.java, getLanguagesWithSources())
        CoreContainer.register(GetRemoteManga::class.java, getRemoteManga())
        CoreContainer.register(GetSourcesWithFavoriteCount::class.java, getSourcesWithFavoriteCount())
        CoreContainer.register(GetSourcesWithNonLibraryManga::class.java, getSourcesWithNonLibraryManga())
        CoreContainer.register(SetMigrateSorting::class.java, setMigrateSorting())
        CoreContainer.register(ToggleLanguage::class.java, toggleLanguage())
        CoreContainer.register(ToggleSource::class.java, toggleSource())
        CoreContainer.register(ToggleSourcePin::class.java, toggleSourcePin())
        CoreContainer.register(TrustExtension::class.java, trustExtension())

        CoreContainer.register(GetExtensionRepo::class.java, getExtensionRepo())
        CoreContainer.register(GetExtensionRepoCount::class.java, getExtensionRepoCount())
        CoreContainer.register(CreateExtensionRepo::class.java, createExtensionRepo())
        CoreContainer.register(DeleteExtensionRepo::class.java, deleteExtensionRepo())
        CoreContainer.register(ReplaceExtensionRepo::class.java, replaceExtensionRepo())
        CoreContainer.register(UpdateExtensionRepo::class.java, updateExtensionRepo())

        CoreContainer.register(ToggleIncognito::class.java, toggleIncognito())
        CoreContainer.register(GetIncognitoState::class.java, getIncognitoState())

        // Screen Models
        CoreContainer.registerFactory(ephyra.feature.category.CategoryScreenModel::class.java) {
            ephyra.feature.category.CategoryScreenModel(
                getCategories = CoreContainer.get(),
                createCategoryWithName = CoreContainer.get(),
                deleteCategory = CoreContainer.get(),
                reorderCategory = CoreContainer.get(),
                renameCategory = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsSecurityScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsSecurityScreenModel(
                securityPreferences = CoreContainer.get(),
                privacyPreferences = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsReaderScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsReaderScreenModel(
                readerPreferences = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsTrackingScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsTrackingScreenModel(
                trackPreferences = CoreContainer.get(),
                trackerManager = CoreContainer.get(),
                sourceManager = CoreContainer.get(),
                libraryPreferences = CoreContainer.get(),
                trackerListImporter = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsAppearanceScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsAppearanceScreenModel(
                uiPreferences = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsDataScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsDataScreenModel(
                backupPreferences = CoreContainer.get(),
                storagePreferences = CoreContainer.get(),
                libraryPreferences = CoreContainer.get(),
                chapterCache = CoreContainer.get(),
                getFavorites = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsDownloadScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsDownloadScreenModel(
                getCategories = CoreContainer.get(),
                downloadPreferences = CoreContainer.get(),
                trackerManager = CoreContainer.get(),
                trackPreferences = CoreContainer.get(),
                libraryPreferences = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.about.AboutScreenModel::class.java) {
            ephyra.feature.settings.screen.about.AboutScreenModel(
                appUpdateChecker = CoreContainer.get(),
                uiPreferences = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsBrowseScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsBrowseScreenModel(
                sourcePreferences = CoreContainer.get(),
                getExtensionRepoCount = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsLibraryScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsLibraryScreenModel(
                libraryPreferences = CoreContainer.get(),
                getCategories = CoreContainer.get(),
                resetCategoryFlags = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.settings.screen.SettingsAdvancedScreenModel::class.java) {
            ephyra.feature.settings.screen.SettingsAdvancedScreenModel(
                basePreferences = CoreContainer.get(),
                networkPreferences = CoreContainer.get(),
                libraryPreferences = CoreContainer.get(),
                downloadCache = CoreContainer.get(),
                networkHelper = CoreContainer.get(),
                resetViewerFlags = CoreContainer.get(),
                trustExtension = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.updates.UpdatesSettingsScreenModel::class.java) {
            ephyra.feature.updates.UpdatesSettingsScreenModel(
                updatesPreferences = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.updates.UpdatesScreenModel::class.java) {
            ephyra.feature.updates.UpdatesScreenModel(
                sourceManager = CoreContainer.get(),
                downloadManager = CoreContainer.get(),
                downloadCache = CoreContainer.get(),
                updateChapter = CoreContainer.get(),
                setReadStatus = CoreContainer.get(),
                getUpdates = CoreContainer.get(),
                getManga = CoreContainer.get(),
                getChapter = CoreContainer.get(),
                libraryPreferences = CoreContainer.get(),
                updatesPreferences = CoreContainer.get(),
                application = application
            )
        }
        CoreContainer.registerFactory(ephyra.feature.download.DownloadQueueScreenModel::class.java) {
            ephyra.feature.download.DownloadQueueScreenModel(
                downloadManager = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.library.LibraryScreenModel::class.java) {
            ephyra.feature.library.LibraryScreenModel(
                getLibraryManga = CoreContainer.get(),
                getCategories = CoreContainer.get(),
                getTracksPerManga = CoreContainer.get(),
                getNextChapters = CoreContainer.get(),
                getChaptersByMangaId = CoreContainer.get(),
                getBookmarkedChaptersByMangaId = CoreContainer.get(),
                setReadStatus = CoreContainer.get(),
                updateManga = CoreContainer.get(),
                setMangaCategories = CoreContainer.get(),
                preferences = CoreContainer.get(),
                libraryPreferences = CoreContainer.get(),
                coverCache = CoreContainer.get(),
                sourceManager = CoreContainer.get(),
                downloadManager = CoreContainer.get(),
                downloadCache = CoreContainer.get(),
                trackerManager = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.library.LibrarySettingsScreenModel::class.java) {
            ephyra.feature.library.LibrarySettingsScreenModel(
                preferences = CoreContainer.get(),
                libraryPreferences = CoreContainer.get(),
                setDisplayMode = CoreContainer.get(),
                setSortModeForCategory = CoreContainer.get(),
                trackerManager = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.history.HistoryScreenModel::class.java) {
            ephyra.feature.history.HistoryScreenModel(
                addTracks = CoreContainer.get(),
                getCategories = CoreContainer.get(),
                getDuplicateLibraryManga = CoreContainer.get(),
                getHistory = CoreContainer.get(),
                getManga = CoreContainer.get(),
                getNextChapters = CoreContainer.get(),
                libraryPreferences = CoreContainer.get(),
                removeHistory = CoreContainer.get(),
                setMangaCategories = CoreContainer.get(),
                updateManga = CoreContainer.get(),
                sourceManager = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.browse.migration.sources.MigrateSourceScreenModel::class.java) {
            ephyra.feature.browse.migration.sources.MigrateSourceScreenModel(
                preferences = CoreContainer.get(),
                getSourcesWithFavoriteCount = CoreContainer.get(),
                setMigrateSorting = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.browse.extension.ExtensionsScreenModel::class.java) {
            ephyra.feature.browse.extension.ExtensionsScreenModel(
                context = application,
                preferences = CoreContainer.get(),
                basePreferences = CoreContainer.get(),
                extensionManager = CoreContainer.get(),
                getExtensions = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.browse.source.SourcesScreenModel::class.java) {
            ephyra.feature.browse.source.SourcesScreenModel(
                getEnabledSources = CoreContainer.get(),
                toggleSource = CoreContainer.get(),
                toggleSourcePin = CoreContainer.get()
            )
        }
        CoreContainer.registerFactory(ephyra.feature.browse.source.SourcesFilterScreenModel::class.java) {
            ephyra.feature.browse.source.SourcesFilterScreenModel(
                preferences = CoreContainer.get(),
                getLanguagesWithSources = CoreContainer.get(),
                toggleSource = CoreContainer.get(),
                toggleLanguage = CoreContainer.get()
            )
        }
    }
}
