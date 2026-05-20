package ephyra.app.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ephyra.app.core.security.PrivacyPreferences
import ephyra.app.core.security.SecurityPreferences
import ephyra.app.crash.CrashActivity
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
import ephyra.app.data.backup.restore.restorers.MangaRestorer
import ephyra.app.data.backup.restore.restorers.PreferenceRestorer
import ephyra.app.data.cache.ChapterCache
import ephyra.data.cache.CoverCache
import ephyra.app.data.download.DownloadCache
import ephyra.app.data.download.DownloadNotifier
import ephyra.app.data.download.DownloadPendingDeleter
import ephyra.app.data.download.DownloadProvider
import ephyra.app.data.download.DownloadStore
import ephyra.app.data.download.Downloader
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.notification.NotificationManagerImpl
import ephyra.app.data.saver.ImageSaver
import ephyra.app.data.track.TrackerManager
import ephyra.app.data.track.jellyfin.SyncJellyfinImpl
import ephyra.app.data.updater.AppUpdateChecker
import ephyra.app.extension.ExtensionManager
import ephyra.app.extension.api.ExtensionApi
import ephyra.app.extension.util.ExtensionInstaller
import ephyra.app.extension.util.ExtensionLoader
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.app.util.CrashLogUtil
import ephyra.app.util.NavigatorImpl
import ephyra.app.util.system.isDebugBuildType
import ephyra.core.common.notification.NotificationManager
import ephyra.core.common.preference.DataStorePreferenceStore
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.data.category.CategoryRepositoryImpl
import ephyra.data.chapter.ChapterRepositoryImpl
import ephyra.data.history.HistoryRepositoryImpl
import ephyra.data.manga.MangaRepositoryImpl
import ephyra.data.release.ReleaseServiceImpl
import ephyra.data.repository.ExtensionRepoRepositoryImpl
import ephyra.data.room.EphyraDatabase
import ephyra.data.source.SourceRepositoryImpl
import ephyra.data.source.StubSourceRepositoryImpl
import ephyra.data.track.TrackRepositoryImpl
import ephyra.data.updates.UpdatesRepositoryImpl
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.base.BasePreferences
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
import ephyra.domain.category.repository.CategoryRepository
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.chapter.interactor.GetBookmarkedChaptersByMangaId
import ephyra.domain.chapter.interactor.GetChapter
import ephyra.domain.chapter.interactor.GetChapterByUrlAndMangaId
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.interactor.ShouldUpdateDbChapter
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.download.interactor.DeleteDownload
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.extension.interactor.GetExtensionLanguages
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.interactor.GetExtensionSources
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepoCount
import ephyra.domain.extensionrepo.interactor.ReplaceExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.extensionrepo.service.ExtensionRepoService
import ephyra.domain.history.interactor.GetHistory
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.interactor.GetTotalReadDuration
import ephyra.domain.history.interactor.RemoveHistory
import ephyra.domain.history.interactor.RemoveResettedHistory
import ephyra.domain.history.interactor.UpsertHistory
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.jellyfin.interactor.SyncJellyfin
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.DeleteNonLibraryManga
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.manga.interactor.GetDeadFavorites
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.ResetViewerFlags
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.interactor.SetMangaViewerFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.repository.MangaRepository as ReleaseMangaRepository
import ephyra.domain.release.service.ReleaseService
import ephyra.domain.source.interactor.GetEnabledSources
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.interactor.GetLanguagesWithSources
import ephyra.domain.source.interactor.GetRemoteManga
import ephyra.domain.source.interactor.GetSourcesWithFavoriteCount
import ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga
import ephyra.domain.source.interactor.SetMigrateSorting
import ephyra.domain.source.interactor.ToggleIncognito
import ephyra.domain.source.interactor.ToggleLanguage
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.interactor.ToggleSourcePin
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.storage.service.StorageManager
import ephyra.domain.storage.service.StoragePreferences
import ephyra.domain.track.interactor.AddTracks
import ephyra.domain.track.interactor.DeleteTrack
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.GetTracksPerManga
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.interactor.LinkTrackedMangaToAuthority
import ephyra.domain.track.interactor.MatchUnlinkedManga
import ephyra.domain.track.interactor.RefreshCanonicalMetadata
import ephyra.domain.track.interactor.RefreshTracks
import ephyra.domain.track.interactor.SyncChapterProgressWithTrack
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.repository.TrackRepository
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.domain.ui.UiPreferences
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.updates.repository.UpdatesRepository
import ephyra.domain.updates.service.UpdatesPreferences
import ephyra.feature.download.DownloadManager
import ephyra.feature.manga.MangaScreenModelFactory
import ephyra.feature.manga.interactor.MangaChapterInteractor
import ephyra.feature.manga.interactor.MangaInfoInteractor
import ephyra.feature.manga.interactor.MangaTrackInteractor
import ephyra.feature.reader.setting.ReaderPreferences
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate
import ephyra.presentation.core.ui.delegate.ThemingDelegate
import ephyra.presentation.core.util.AppNavigator
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.AndroidSourceManager
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlVersion

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // 1. Serialization Providers
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideXml(): XML = XML {
        defaultPolicy { ignoreUnknownChildren() }
        autoPolymorphic = true
        xmlDeclMode = XmlDeclMode.Charset
        indent = 2
        xmlVersion = XmlVersion.XML10
    }

    @Provides
    @Singleton
    fun provideProtoBuf(): ProtoBuf = ProtoBuf

    // 2. Database Providers
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EphyraDatabase {
        return Room.databaseBuilder(
            context = context,
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

    @Provides
    @Singleton
    fun provideMangaDao(database: EphyraDatabase) = database.mangaDao()

    @Provides
    @Singleton
    fun provideChapterDao(database: EphyraDatabase) = database.chapterDao()

    @Provides
    @Singleton
    fun provideCategoryDao(database: EphyraDatabase) = database.categoryDao()

    @Provides
    @Singleton
    fun provideHistoryDao(database: EphyraDatabase) = database.historyDao()

    @Provides
    @Singleton
    fun provideTrackDao(database: EphyraDatabase) = database.trackDao()

    @Provides
    @Singleton
    fun provideUpdateDao(database: EphyraDatabase) = database.updateDao()

    @Provides
    @Singleton
    fun provideExtensionRepoDao(database: EphyraDatabase) = database.extensionRepoDao()

    @Provides
    @Singleton
    fun provideSourceDao(database: EphyraDatabase) = database.sourceDao()

    // 3. Shared Preferences Providers
    @Provides
    @Singleton
    fun providePreferenceStore(@ApplicationContext context: Context): PreferenceStore =
        DataStorePreferenceStore(context)

    @Provides
    @Singleton
    fun provideNetworkPreferences(preferenceStore: PreferenceStore) =
        NetworkPreferences(preferenceStore, isDebugBuildType)

    @Provides
    @Singleton
    fun provideSourcePreferences(preferenceStore: PreferenceStore) =
        SourcePreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideSecurityPreferences(preferenceStore: PreferenceStore) =
        SecurityPreferences(preferenceStore)

    @Provides
    @Singleton
    fun providePrivacyPreferences(preferenceStore: PreferenceStore) =
        PrivacyPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideLibraryPreferences(preferenceStore: PreferenceStore) =
        LibraryPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideUpdatesPreferences(preferenceStore: PreferenceStore) =
        UpdatesPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideReaderPreferences(preferenceStore: PreferenceStore) =
        ReaderPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideTrackPreferences(preferenceStore: PreferenceStore) =
        TrackPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideDownloadPreferences(preferenceStore: PreferenceStore) =
        DownloadPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideBackupPreferences(preferenceStore: PreferenceStore) =
        BackupPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideAndroidStorageFolderProvider(@ApplicationContext context: Context) =
        AndroidStorageFolderProvider(context)

    @Provides
    @Singleton
    fun provideStoragePreferences(
        androidStorageFolderProvider: AndroidStorageFolderProvider,
        preferenceStore: PreferenceStore
    ) = StoragePreferences(androidStorageFolderProvider, preferenceStore)

    @Provides
    @Singleton
    fun provideUiPreferences(preferenceStore: PreferenceStore) =
        UiPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideBasePreferences(@ApplicationContext context: Context, preferenceStore: PreferenceStore) =
        BasePreferences(context, preferenceStore)

    // 4. Repositories Providers
    @Provides
    @Singleton
    fun provideCategoryRepository(categoryDao: ephyra.data.category.CategoryDao): CategoryRepository =
        CategoryRepositoryImpl(categoryDao)

    @Provides
    @Singleton
    fun provideMangaRepository(mangaDao: ephyra.data.manga.MangaDao): MangaRepository =
        MangaRepositoryImpl(mangaDao)

    @Provides
    @Singleton
    fun provideChapterRepository(chapterDao: ephyra.data.chapter.ChapterDao): ChapterRepository =
        ChapterRepositoryImpl(chapterDao)

    @Provides
    @Singleton
    fun provideHistoryRepository(historyDao: ephyra.data.history.HistoryDao): HistoryRepository =
        HistoryRepositoryImpl(historyDao)

    @Provides
    @Singleton
    fun provideUpdatesRepository(updateDao: ephyra.data.updates.UpdatesDao): UpdatesRepository =
        UpdatesRepositoryImpl(updateDao)

    @Provides
    @Singleton
    fun provideSourceRepository(
        sourceDao: ephyra.data.source.SourceDao,
        extensionManager: ExtensionManager,
        sourcePreferences: SourcePreferences
    ): SourceRepository = SourceRepositoryImpl(sourceDao, extensionManager, sourcePreferences)

    @Provides
    @Singleton
    fun provideStubSourceRepository(sourceDao: ephyra.data.source.SourceDao): StubSourceRepository =
        StubSourceRepositoryImpl(sourceDao)

    @Provides
    @Singleton
    fun provideExtensionRepoRepository(extensionRepoDao: ephyra.data.repository.ExtensionRepoDao): ExtensionRepoRepository =
        ExtensionRepoRepositoryImpl(extensionRepoDao)

    @Provides
    @Singleton
    fun provideTrackRepository(trackDao: ephyra.data.track.TrackDao): TrackRepository =
        TrackRepositoryImpl(trackDao)

    // 5. Shared Core Services & Managers
    @Provides
    @Singleton
    fun provideNetworkHelper(@ApplicationContext context: Context, networkPreferences: NetworkPreferences) =
        NetworkHelper(context, networkPreferences)

    @Provides
    @Singleton
    fun provideJavaScriptEngine(@ApplicationContext context: Context) =
        JavaScriptEngine(context)

    @Provides
    @Singleton
    fun provideTrustExtension(preferenceStore: PreferenceStore, sourcePreferences: SourcePreferences) =
        TrustExtension(preferenceStore, sourcePreferences)

    @Provides
    @Singleton
    fun provideExtensionLoader(sourcePreferences: SourcePreferences, trustExtension: TrustExtension) =
        ExtensionLoader(sourcePreferences, trustExtension)

    @Provides
    @Singleton
    fun provideExtensionInstaller(
        @ApplicationContext context: Context,
        sourcePreferences: SourcePreferences,
        trustExtension: TrustExtension,
        securityPreferences: SecurityPreferences
    ) = ExtensionInstaller(context, sourcePreferences, trustExtension, securityPreferences)

    @Provides
    @Singleton
    fun provideExtensionApi(
        networkHelper: NetworkHelper,
        preferenceStore: PreferenceStore,
        getExtensionRepo: GetExtensionRepo,
        updateExtensionRepo: UpdateExtensionRepo,
        securityPreferences: SecurityPreferences,
        extensionLoader: ExtensionLoader,
        json: Json
    ) = ExtensionApi(networkHelper, preferenceStore, getExtensionRepo, updateExtensionRepo, securityPreferences, extensionLoader, json)

    @Provides
    @Singleton
    fun provideExtensionManager(
        @ApplicationContext context: Context,
        sourcePreferences: SourcePreferences,
        trustExtension: TrustExtension,
        securityPreferences: SecurityPreferences,
        extensionLoader: ExtensionLoader,
        extensionApi: ExtensionApi
    ) = ExtensionManager(context, sourcePreferences, trustExtension, securityPreferences, extensionLoader, extensionApi)

    @Provides
    @Singleton
    fun provideSourceManager(
        @ApplicationContext context: Context,
        extensionManager: ExtensionManager,
        stubSourceRepository: StubSourceRepository,
        localSourceFileSystem: LocalSourceFileSystem,
        localCoverManager: LocalCoverManager,
        downloadManager: DownloadManager
    ): SourceManager = AndroidSourceManager(context, extensionManager, stubSourceRepository, localSourceFileSystem, localCoverManager, downloadManager)

    @Provides
    @Singleton
    fun provideLocalSourceFileSystem(androidStorageFolderProvider: AndroidStorageFolderProvider) =
        LocalSourceFileSystem(androidStorageFolderProvider)

    @Provides
    @Singleton
    fun provideLocalCoverManager(
        @ApplicationContext context: Context,
        androidStorageFolderProvider: AndroidStorageFolderProvider
    ) = LocalCoverManager(context, androidStorageFolderProvider)

    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context, storagePreferences: StoragePreferences) =
        StorageManager(context, storagePreferences)

    @Provides
    @Singleton
    fun provideDownloadStore(
        @ApplicationContext context: Context,
        sourceManager: SourceManager,
        json: Json,
        updatesPreferences: UpdatesPreferences,
        sourcePreferences: SourcePreferences
    ) = DownloadStore(context, sourceManager, json, updatesPreferences, sourcePreferences)

    @Provides
    @Singleton
    fun provideDownloadProvider(
        @ApplicationContext context: Context,
        sourceManager: SourceManager,
        storageManager: StorageManager
    ) = DownloadProvider(context, sourceManager, storageManager)

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        downloadProvider: DownloadProvider,
        sourceManager: SourceManager,
        downloadPreferences: DownloadPreferences,
        storageManager: StorageManager
    ) = DownloadCache(context, downloadProvider, sourceManager, downloadPreferences, storageManager)

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        downloadProvider: DownloadProvider,
        downloadStore: DownloadStore,
        downloadCache: DownloadCache,
        sourceManager: SourceManager,
        updatesPreferences: UpdatesPreferences,
        sourcePreferences: SourcePreferences,
        downloadPreferences: DownloadPreferences
    ) = DownloadManager(context, downloadProvider, downloadStore, downloadCache, sourceManager, updatesPreferences, sourcePreferences, downloadPreferences)

    @Provides
    @Singleton
    fun provideDownloader(
        @ApplicationContext context: Context,
        downloadProvider: DownloadProvider,
        downloadStore: DownloadStore,
        downloadCache: DownloadCache,
        sourceManager: SourceManager,
        updatesPreferences: UpdatesPreferences,
        sourcePreferences: SourcePreferences,
        downloadPreferences: DownloadPreferences,
        trackerManager: TrackerManager,
        networkHelper: NetworkHelper,
        json: Json,
        basePreferences: BasePreferences
    ) = Downloader(context, downloadProvider, downloadStore, downloadCache, sourceManager, updatesPreferences, sourcePreferences, downloadPreferences, trackerManager, networkHelper, json, basePreferences)

    @Provides
    @Singleton
    fun provideDownloadPendingDeleter(@ApplicationContext context: Context, sourceManager: SourceManager) =
        DownloadPendingDeleter(context, sourceManager)

    @Provides
    @Singleton
    fun provideDownloadNotifier(@ApplicationContext context: Context, imageSaver: ImageSaver) =
        DownloadNotifier(context, imageSaver)

    @Provides
    @Singleton
    fun provideTrackerManager(
        @ApplicationContext context: Context,
        trackPreferences: TrackPreferences,
        json: Json,
        basePreferences: BasePreferences,
        trackRepository: TrackRepository,
        networkHelper: NetworkHelper
    ) = TrackerManager(context, trackPreferences, json, basePreferences, trackRepository, networkHelper)

    @Provides
    @Singleton
    fun provideDelayedTrackingStore(@ApplicationContext context: Context) =
        DelayedTrackingStore(context)

    @Provides
    @Singleton
    fun provideImageSaver(@ApplicationContext context: Context) = ImageSaver(context)

    @Provides
    @Singleton
    fun provideChapterCache(@ApplicationContext context: Context, json: Json) =
        ChapterCache(context, json)

    @Provides
    @Singleton
    fun provideCoverCache(@ApplicationContext context: Context) = CoverCache(context)

    @Provides
    @Singleton
    fun provideCrashLogUtil(@ApplicationContext context: Context, storageManager: StorageManager) =
        CrashLogUtil(context, storageManager)

    @Provides
    @Singleton
    fun provideNavigator(): AppNavigator = NavigatorImpl()

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
        NotificationManagerImpl(context)

    @Provides
    @Singleton
    fun provideThemingDelegate(uiPreferences: UiPreferences): ThemingDelegate =
        ThemingDelegateImpl(uiPreferences)

    @Provides
    @Singleton
    fun provideSecureActivityDelegate(basePreferences: BasePreferences, privacyPreferences: PrivacyPreferences): SecureActivityDelegate =
        SecureActivityDelegateImpl(basePreferences, privacyPreferences)

    // 6. Backup & Restore
    @Provides
    @Singleton
    fun provideBackupDecoder(@ApplicationContext context: Context, json: Json) =
        BackupDecoder(context, json)

    @Provides
    @Singleton
    fun provideBackupFileValidator(
        @ApplicationContext context: Context,
        json: Json,
        sourcePreferences: SourcePreferences,
        extensionManager: ExtensionManager
    ) = BackupFileValidator(context, json, sourcePreferences, extensionManager)

    @Provides
    @Singleton
    fun provideCategoriesBackupCreator(categoryRepository: CategoryRepository) =
        CategoriesBackupCreator(categoryRepository)

    @Provides
    @Singleton
    fun provideMangaBackupCreator(
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
        historyRepository: HistoryRepository
    ) = MangaBackupCreator(mangaRepository, chapterRepository, historyRepository)

    @Provides
    @Singleton
    fun providePreferenceBackupCreator(preferenceStore: PreferenceStore, sourcePreferences: SourcePreferences) =
        PreferenceBackupCreator(preferenceStore, sourcePreferences)

    @Provides
    @Singleton
    fun provideExtensionRepoBackupCreator(extensionRepoRepository: ExtensionRepoRepository) =
        ExtensionRepoBackupCreator(extensionRepoRepository)

    @Provides
    @Singleton
    fun provideSourcesBackupCreator(sourcePreferences: SourcePreferences) =
        SourcesBackupCreator(sourcePreferences)

    @Provides
    @Singleton
    fun provideBackupCreator(
        @ApplicationContext context: Context,
        categoriesBackupCreator: CategoriesBackupCreator,
        mangaBackupCreator: MangaBackupCreator,
        preferenceBackupCreator: PreferenceBackupCreator,
        extensionRepoBackupCreator: ExtensionRepoBackupCreator,
        sourcesBackupCreator: SourcesBackupCreator,
        preferenceStore: PreferenceStore,
        json: Json,
        backupPreferences: BackupPreferences
    ) = BackupCreator(context, categoriesBackupCreator, mangaBackupCreator, preferenceBackupCreator, extensionRepoBackupCreator, sourcesBackupCreator, preferenceStore, json, json, backupPreferences)

    @Provides
    @Singleton
    fun provideCategoriesRestorer(
        categoryRepository: CategoryRepository,
        categoryDao: ephyra.data.category.CategoryDao,
        database: EphyraDatabase
    ) = CategoriesRestorer(categoryRepository, categoryDao, database)

    @Provides
    @Singleton
    fun providePreferenceRestorer(
        @ApplicationContext context: Context,
        preferenceStore: PreferenceStore,
        sourcePreferences: SourcePreferences,
        json: Json
    ) = PreferenceRestorer(context, preferenceStore, sourcePreferences, json, json)

    @Provides
    @Singleton
    fun provideMangaRestorer(
        mangaRepository: MangaRepository,
        categoryRepository: CategoryRepository,
        chapterRepository: ChapterRepository,
        historyRepository: HistoryRepository,
        trackRepository: TrackRepository,
        sourceManager: SourceManager,
        backupPreferences: BackupPreferences,
        database: EphyraDatabase
    ) = MangaRestorer(mangaRepository, categoryRepository, chapterRepository, historyRepository, trackRepository, sourceManager, backupPreferences, database)

    @Provides
    @Singleton
    fun provideBackupRestorer(
        @ApplicationContext context: Context,
        categoriesRestorer: CategoriesRestorer,
        preferenceRestorer: PreferenceRestorer,
        mangaRestorer: MangaRestorer,
        backupPreferences: BackupPreferences
    ) = BackupRestorer(context, categoriesRestorer, preferenceRestorer, mangaRestorer, backupPreferences)

    @Provides
    @Singleton
    fun provideAppUpdateChecker(networkHelper: NetworkHelper) = AppUpdateChecker(networkHelper)

    @Provides
    @Singleton
    fun provideLibraryUpdateNotifier(@ApplicationContext context: Context, mangaRepository: MangaRepository, sourceManager: SourceManager) =
        LibraryUpdateNotifier(context, mangaRepository, sourceManager)

    @Provides
    @Singleton
    fun provideBackupNotifier(@ApplicationContext context: Context, imageSaver: ImageSaver) =
        BackupNotifier(context, imageSaver)

    @Provides
    @Singleton
    fun provideReleaseService(networkHelper: NetworkHelper, json: Json): ReleaseService =
        ReleaseServiceImpl(networkHelper, json)

    // 7. Interactors (Use Cases) Providers
    @Provides
    fun provideGetCategories(categoryRepository: CategoryRepository) = GetCategories(categoryRepository)

    @Provides
    fun provideResetCategoryFlags(categoryRepository: CategoryRepository, categoryDao: ephyra.data.category.CategoryDao) =
        ResetCategoryFlags(categoryRepository, categoryDao)

    @Provides
    fun provideSetDisplayMode(categoryRepository: CategoryRepository) = SetDisplayMode(categoryRepository)

    @Provides
    fun provideSetSortModeForCategory(categoryRepository: CategoryRepository, categoryDao: ephyra.data.category.CategoryDao) =
        SetSortModeForCategory(categoryRepository, categoryDao)

    @Provides
    fun provideCreateCategoryWithName(categoryRepository: CategoryRepository, categoryDao: ephyra.data.category.CategoryDao) =
        CreateCategoryWithName(categoryRepository, categoryDao)

    @Provides
    fun provideRenameCategory(categoryRepository: CategoryRepository) = RenameCategory(categoryRepository)

    @Provides
    fun provideReorderCategory(categoryRepository: CategoryRepository) = ReorderCategory(categoryRepository)

    @Provides
    fun provideUpdateCategory(categoryRepository: CategoryRepository) = UpdateCategory(categoryRepository)

    @Provides
    fun provideDeleteCategory(
        categoryRepository: CategoryRepository,
        categoryDao: ephyra.data.category.CategoryDao,
        database: EphyraDatabase
    ) = DeleteCategory(categoryRepository, categoryDao, database)

    @Provides
    fun provideGetDuplicateLibraryManga(mangaRepository: MangaRepository) = GetDuplicateLibraryManga(mangaRepository)

    @Provides
    fun provideGetFavorites(mangaRepository: MangaRepository) = GetFavorites(mangaRepository)

    @Provides
    fun provideGetFavoritesByCanonicalId(mangaRepository: MangaRepository) = GetFavoritesByCanonicalId(mangaRepository)

    @Provides
    fun provideGetDeadFavorites(mangaRepository: MangaRepository) = GetDeadFavorites(mangaRepository)

    @Provides
    fun provideGetLibraryManga(mangaRepository: MangaRepository) = GetLibraryManga(mangaRepository)

    @Provides
    fun provideGetMangaWithChapters(mangaRepository: MangaRepository, chapterRepository: ChapterRepository) =
        GetMangaWithChapters(mangaRepository, chapterRepository)

    @Provides
    fun provideGetMangaByUrlAndSourceId(mangaRepository: MangaRepository) = GetMangaByUrlAndSourceId(mangaRepository)

    @Provides
    fun provideGetManga(mangaRepository: MangaRepository) = GetManga(mangaRepository)

    @Provides
    fun provideGetNextChapters(
        chapterRepository: ChapterRepository,
        historyRepository: HistoryRepository,
        updatesRepository: UpdatesRepository
    ) = GetNextChapters(chapterRepository, historyRepository, updatesRepository)

    @Provides
    fun provideGetUpcomingManga(mangaRepository: MangaRepository) = GetUpcomingManga(mangaRepository)

    @Provides
    fun provideResetViewerFlags(mangaRepository: MangaRepository) = ResetViewerFlags(mangaRepository)

    @Provides
    fun provideSetMangaChapterFlags(mangaRepository: MangaRepository) = SetMangaChapterFlags(mangaRepository)

    @Provides
    fun provideFetchInterval(mangaRepository: MangaRepository) = FetchInterval(mangaRepository)

    @Provides
    fun provideSetMangaDefaultChapterFlags(
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
        database: EphyraDatabase
    ) = SetMangaDefaultChapterFlags(mangaRepository, chapterRepository, database)

    @Provides
    fun provideSetMangaViewerFlags(mangaRepository: MangaRepository) = SetMangaViewerFlags(mangaRepository)

    @Provides
    fun provideNetworkToLocalManga(mangaRepository: MangaRepository) = NetworkToLocalManga(mangaRepository)

    @Provides
    fun provideUpdateManga(
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
        categoryDao: ephyra.data.category.CategoryDao,
        updatesRepository: UpdatesRepository,
        database: EphyraDatabase,
        coverCache: CoverCache
    ) = UpdateManga(mangaRepository, chapterRepository, categoryDao, updatesRepository, database, coverCache)

    @Provides
    fun provideFindContentSource(mangaRepository: MangaRepository, sourceManager: SourceManager) =
        FindContentSource(mangaRepository, sourceManager)

    @Provides
    fun provideUpdateMangaNotes(mangaRepository: MangaRepository) = UpdateMangaNotes(mangaRepository)

    @Provides
    fun provideSetMangaCategories(categoryRepository: CategoryRepository) = SetMangaCategories(categoryRepository)

    @Provides
    fun provideGetExcludedScanlators(mangaRepository: MangaRepository) = GetExcludedScanlators(mangaRepository)

    @Provides
    fun provideSetExcludedScanlators(mangaRepository: MangaRepository) = SetExcludedScanlators(mangaRepository)

    @Provides
    fun provideDeleteNonLibraryManga(mangaRepository: MangaRepository) = DeleteNonLibraryManga(mangaRepository)

    @Provides
    fun provideMigrateManga(
        mangaRepository: MangaRepository,
        categoryRepository: CategoryRepository,
        chapterRepository: ChapterRepository,
        historyRepository: HistoryRepository,
        trackRepository: TrackRepository,
        sourceManager: SourceManager,
        database: EphyraDatabase,
        trackChapter: TrackChapter,
        deleteDownload: DeleteDownload
    ) = MigrateMangaUseCase(mangaRepository, categoryRepository, chapterRepository, historyRepository, trackRepository, sourceManager, database, trackChapter, deleteDownload)

    @Provides
    fun provideGetApplicationRelease(releaseService: ReleaseService, basePreferences: BasePreferences) =
        GetApplicationRelease(releaseService, basePreferences)

    @Provides
    fun provideTrackChapter(
        trackRepository: TrackRepository,
        trackerManager: TrackerManager,
        delayedTrackingStore: DelayedTrackingStore,
        basePreferences: BasePreferences
    ) = TrackChapter(trackRepository, trackerManager, delayedTrackingStore, basePreferences)

    @Provides
    fun provideAddTracks(
        trackRepository: TrackRepository,
        trackerManager: TrackerManager,
        trackDao: ephyra.data.track.TrackDao,
        delayedTrackingStore: DelayedTrackingStore,
        basePreferences: BasePreferences,
        trackChapter: TrackChapter
    ) = AddTracks(trackRepository, trackerManager, trackDao, delayedTrackingStore, basePreferences, trackChapter)

    @Provides
    fun provideRefreshTracks(
        trackRepository: TrackRepository,
        trackerManager: TrackerManager,
        trackDao: ephyra.data.track.TrackDao,
        delayedTrackingStore: DelayedTrackingStore
    ) = RefreshTracks(trackRepository, trackerManager, trackDao, delayedTrackingStore)

    @Provides
    fun provideDeleteTrack(trackRepository: TrackRepository) = DeleteTrack(trackRepository)

    @Provides
    fun provideGetTracksPerManga(trackRepository: TrackRepository) = GetTracksPerManga(trackRepository)

    @Provides
    fun provideGetTracks(trackRepository: TrackRepository) = GetTracks(trackRepository)

    @Provides
    fun provideInsertTrack(trackRepository: TrackRepository) = InsertTrack(trackRepository)

    @Provides
    fun provideSyncChapterProgressWithTrack(
        trackRepository: TrackRepository,
        trackerManager: TrackerManager,
        delayedTrackingStore: DelayedTrackingStore
    ) = SyncChapterProgressWithTrack(trackRepository, trackerManager, delayedTrackingStore)

    @Provides
    fun provideTrackerListImporter(
        trackRepository: TrackRepository,
        trackerManager: TrackerManager,
        trackDao: ephyra.data.track.TrackDao,
        delayedTrackingStore: DelayedTrackingStore
    ) = TrackerListImporter(trackRepository, trackerManager, trackDao, delayedTrackingStore)

    @Provides
    fun provideLinkTrackedMangaToAuthority(trackRepository: TrackRepository, trackerManager: TrackerManager) =
        LinkTrackedMangaToAuthority(trackRepository, trackerManager)

    @Provides
    fun provideMatchUnlinkedManga(
        trackRepository: TrackRepository,
        trackerManager: TrackerManager,
        trackDao: ephyra.data.track.TrackDao,
        delayedTrackingStore: DelayedTrackingStore
    ) = MatchUnlinkedManga(trackRepository, trackerManager, trackDao, delayedTrackingStore)

    @Provides
    fun provideRefreshCanonicalMetadata(
        trackRepository: TrackRepository,
        trackerManager: TrackerManager,
        trackDao: ephyra.data.track.TrackDao,
        delayedTrackingStore: DelayedTrackingStore
    ) = RefreshCanonicalMetadata(trackRepository, trackerManager, trackDao, delayedTrackingStore)

    @Provides
    fun provideGetChapter(chapterRepository: ChapterRepository) = GetChapter(chapterRepository)

    @Provides
    fun provideGetChaptersByMangaId(chapterRepository: ChapterRepository) = GetChaptersByMangaId(chapterRepository)

    @Provides
    fun provideGetBookmarkedChaptersByMangaId(chapterRepository: ChapterRepository) =
        GetBookmarkedChaptersByMangaId(chapterRepository)

    @Provides
    fun provideGetChapterByUrlAndMangaId(chapterRepository: ChapterRepository) =
        GetChapterByUrlAndMangaId(chapterRepository)

    @Provides
    fun provideUpdateChapter(chapterRepository: ChapterRepository) = UpdateChapter(chapterRepository)

    @Provides
    fun provideSetReadStatus(
        chapterRepository: ChapterRepository,
        database: EphyraDatabase,
        updatesRepository: UpdatesRepository,
        trackChapter: TrackChapter
    ) = SetReadStatus(chapterRepository, database, updatesRepository, trackChapter)

    @Provides
    fun provideShouldUpdateDbChapter() = ShouldUpdateDbChapter()

    @Provides
    fun provideSyncChaptersWithSource(
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
        sourceManager: SourceManager,
        database: EphyraDatabase,
        updatesRepository: UpdatesRepository,
        getManga: GetManga,
        getChaptersByMangaId: GetChaptersByMangaId,
        setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
        shouldUpdateDbChapter: ShouldUpdateDbChapter,
        updateManga: UpdateManga
    ) = SyncChaptersWithSource(mangaRepository, chapterRepository, sourceManager, database, updatesRepository, getManga, getChaptersByMangaId, setMangaDefaultChapterFlags, shouldUpdateDbChapter, updateManga)

    @Provides
    fun provideGetAvailableScanlators(chapterRepository: ChapterRepository) =
        GetAvailableScanlators(chapterRepository)

    @Provides
    fun provideFilterChaptersForDownload(
        sourceManager: SourceManager,
        downloadManager: DownloadManager,
        getChaptersByMangaId: GetChaptersByMangaId
    ) = FilterChaptersForDownload(sourceManager, downloadManager, getChaptersByMangaId)

    @Provides
    fun provideGenerateAuthorityChapters(chapterRepository: ChapterRepository) =
        GenerateAuthorityChapters(chapterRepository)

    @Provides
    fun provideGetHistory(historyRepository: HistoryRepository) = GetHistory(historyRepository)

    @Provides
    fun provideUpsertHistory(historyRepository: HistoryRepository) = UpsertHistory(historyRepository)

    @Provides
    fun provideRemoveHistory(historyRepository: HistoryRepository) = RemoveHistory(historyRepository)

    @Provides
    fun provideRemoveResettedHistory(historyRepository: HistoryRepository) =
        RemoveResettedHistory(historyRepository)

    @Provides
    fun provideGetTotalReadDuration(historyRepository: HistoryRepository) =
        GetTotalReadDuration(historyRepository)

    @Provides
    fun provideDeleteDownload(sourceManager: SourceManager, downloadManager: DownloadManager) =
        DeleteDownload(sourceManager, downloadManager)

    @Provides
    fun provideGetExtensionsByType(extensionManager: ExtensionManager, sourcePreferences: SourcePreferences) =
        GetExtensionsByType(extensionManager, sourcePreferences)

    @Provides
    fun provideGetExtensionSources(extensionManager: ExtensionManager) =
        GetExtensionSources(extensionManager)

    @Provides
    fun provideGetExtensionLanguages(extensionManager: ExtensionManager, sourcePreferences: SourcePreferences) =
        GetExtensionLanguages(extensionManager, sourcePreferences)

    @Provides
    fun provideGetUpdates(updatesRepository: UpdatesRepository) = GetUpdates(updatesRepository)

    @Provides
    fun provideGetEnabledSources(sourceRepository: SourceRepository, sourcePreferences: SourcePreferences) =
        GetEnabledSources(sourceRepository, sourcePreferences)

    @Provides
    fun provideGetLanguagesWithSources(sourceRepository: SourceRepository, sourcePreferences: SourcePreferences) =
        GetLanguagesWithSources(sourceRepository, sourcePreferences)

    @Provides
    fun provideGetRemoteManga(sourceRepository: SourceRepository) = GetRemoteManga(sourceRepository)

    @Provides
    fun provideGetSourcesWithFavoriteCount(sourceRepository: SourceRepository, sourcePreferences: SourcePreferences) =
        GetSourcesWithFavoriteCount(sourceRepository, sourcePreferences)

    @Provides
    fun provideGetSourcesWithNonLibraryManga(sourceRepository: SourceRepository) =
        GetSourcesWithNonLibraryManga(sourceRepository)

    @Provides
    fun provideSetMigrateSorting(sourceRepository: SourceRepository) = SetMigrateSorting(sourceRepository)

    @Provides
    fun provideToggleLanguage(sourceRepository: SourceRepository) = ToggleLanguage(sourceRepository)

    @Provides
    fun provideToggleSource(sourceRepository: SourceRepository) = ToggleSource(sourceRepository)

    @Provides
    fun provideToggleSourcePin(sourceRepository: SourceRepository) = ToggleSourcePin(sourceRepository)

    @Provides
    fun provideTrustExtension(preferenceStore: PreferenceStore, sourcePreferences: SourcePreferences) =
        TrustExtension(preferenceStore, sourcePreferences)

    @Provides
    fun provideGetExtensionRepo(extensionRepoRepository: ExtensionRepoRepository) =
        GetExtensionRepo(extensionRepoRepository)

    @Provides
    fun provideGetExtensionRepoCount(extensionRepoRepository: ExtensionRepoRepository) =
        GetExtensionRepoCount(extensionRepoRepository)

    @Provides
    fun provideExtensionRepoService(networkHelper: NetworkHelper, json: Json) =
        ExtensionRepoService(networkHelper, json)

    @Provides
    fun provideCreateExtensionRepo(
        extensionRepoRepository: ExtensionRepoRepository,
        extensionRepoService: ExtensionRepoService
    ) = CreateExtensionRepo(extensionRepoRepository, extensionRepoService)

    @Provides
    fun provideDeleteExtensionRepo(extensionRepoRepository: ExtensionRepoRepository) =
        DeleteExtensionRepo(extensionRepoRepository)

    @Provides
    fun provideReplaceExtensionRepo(extensionRepoRepository: ExtensionRepoRepository) =
        ReplaceExtensionRepo(extensionRepoRepository)

    @Provides
    fun provideUpdateExtensionRepo(
        extensionRepoRepository: ExtensionRepoRepository,
        extensionRepoService: ExtensionRepoService
    ) = UpdateExtensionRepo(extensionRepoRepository, extensionRepoService)

    @Provides
    fun provideToggleIncognito(basePreferences: BasePreferences) = ToggleIncognito(basePreferences)

    @Provides
    fun provideGetIncognitoState(
        basePreferences: BasePreferences,
        trackerManager: TrackerManager,
        sourceManager: SourceManager
    ) = GetIncognitoState(basePreferences, trackerManager, sourceManager)

    @Provides
    @Singleton
    fun provideSyncJellyfin(): SyncJellyfin = SyncJellyfinImpl()

    @Provides
    @Singleton
    fun provideMangaInfoInteractor(
        updateManga: UpdateManga,
        mangaRepository: MangaRepository,
        refreshCanonical: RefreshCanonicalMetadata,
        matchUnlinkedManga: MatchUnlinkedManga,
        syncJellyfin: SyncJellyfin,
        setExcludedScanlators: SetExcludedScanlators,
        setMangaCategories: SetMangaCategories
    ) = MangaInfoInteractor(updateManga, mangaRepository, refreshCanonical, matchUnlinkedManga, syncJellyfin, setExcludedScanlators, setMangaCategories)

    @Provides
    @Singleton
    fun provideMangaChapterInteractor(
        setMangaChapterFlags: SetMangaChapterFlags,
        setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
        setReadStatus: SetReadStatus,
        updateChapter: UpdateChapter,
        libraryPreferences: LibraryPreferences,
        filterChaptersForDownload: FilterChaptersForDownload,
        syncChaptersWithSource: SyncChaptersWithSource,
        downloadManager: DownloadManager
    ) = MangaChapterInteractor(setMangaChapterFlags, setMangaDefaultChapterFlags, setReadStatus, updateChapter, libraryPreferences, filterChaptersForDownload, syncChaptersWithSource, downloadManager)

    @Provides
    @Singleton
    fun provideMangaTrackInteractor(
        getTracks: GetTracks,
        addTracks: AddTracks,
        refreshCanonical: RefreshCanonicalMetadata,
        matchUnlinkedManga: MatchUnlinkedManga,
        trackChapter: TrackChapter,
        trackPreferences: TrackPreferences,
        trackerManager: TrackerManager,
        refreshTracks: RefreshTracks
    ) = MangaTrackInteractor(getTracks, addTracks, refreshCanonical, matchUnlinkedManga, trackChapter, trackPreferences, trackerManager, refreshTracks)

    @Provides
    @Singleton
    fun provideMangaScreenModelFactory(
        @ApplicationContext context: Context,
        basePreferences: BasePreferences,
        uiPreferences: UiPreferences,
        libraryPreferences: LibraryPreferences,
        readerPreferences: ReaderPreferences,
        downloadManager: DownloadManager,
        downloadCache: DownloadCache,
        getMangaAndChapters: GetMangaWithChapters,
        getDuplicateLibraryManga: GetDuplicateLibraryManga,
        getAvailableScanlators: GetAvailableScanlators,
        getExcludedScanlators: GetExcludedScanlators,
        getCategories: GetCategories,
        sourceManager: SourceManager,
        mangaInfoInteractor: MangaInfoInteractor,
        mangaChapterInteractor: MangaChapterInteractor,
        mangaTrackInteractor: MangaTrackInteractor,
        syncJellyfin: SyncJellyfin
    ) = MangaScreenModelFactory(
        context,
        basePreferences,
        uiPreferences,
        libraryPreferences,
        readerPreferences,
        downloadManager,
        downloadCache,
        getMangaAndChapters,
        getDuplicateLibraryManga,
        getAvailableScanlators,
        getExcludedScanlators,
        getCategories,
        sourceManager,
        mangaInfoInteractor,
        mangaChapterInteractor,
        mangaTrackInteractor,
        syncJellyfin
    )
 
    @Provides
    @Singleton
    fun provideExtensionReposScreenFactory(): ephyra.presentation.core.ui.ExtensionReposScreenFactory {
        return ephyra.presentation.core.ui.ExtensionReposScreenFactory { url ->
            ephyra.feature.settings.screen.browse.ExtensionReposScreen(url)
        }
    }

    @Provides
    @Singleton
    fun provideMigrationConfigScreenFactory(): ephyra.presentation.core.ui.MigrationConfigScreenFactory {
        return ephyra.presentation.core.ui.MigrationConfigScreenFactory { mangaIds ->
            ephyra.feature.migration.config.MigrationConfigScreen(mangaIds)
        }
    }

    @Provides
    @Singleton
    fun provideWorkScheduler(@ApplicationContext context: Context): ephyra.app.data.scheduler.WorkSchedulerImpl {
        return ephyra.app.data.scheduler.WorkSchedulerImpl(context)
    }

    @Provides
    @Singleton
    fun provideBackupScheduler(workScheduler: ephyra.app.data.scheduler.WorkSchedulerImpl): ephyra.domain.backup.service.BackupScheduler = workScheduler

    @Provides
    @Singleton
    fun provideRestoreScheduler(workScheduler: ephyra.app.data.scheduler.WorkSchedulerImpl): ephyra.domain.backup.service.RestoreScheduler = workScheduler

    @Provides
    @Singleton
    fun provideLibraryUpdateScheduler(workScheduler: ephyra.app.data.scheduler.WorkSchedulerImpl): ephyra.domain.library.service.LibraryUpdateScheduler = workScheduler

    @Provides
    @Singleton
    fun provideMetadataUpdateScheduler(workScheduler: ephyra.app.data.scheduler.WorkSchedulerImpl): ephyra.domain.library.service.MetadataUpdateScheduler = workScheduler

    @Provides
    @Singleton
    fun provideAppInfo(): ephyra.presentation.core.ui.AppInfo {
        return object : ephyra.presentation.core.ui.AppInfo {
            override val isDebug: Boolean = ephyra.app.BuildConfig.DEBUG
            override val buildType: String = ephyra.app.BuildConfig.BUILD_TYPE
            override val commitSha: String = "unknown"
            override val commitCount: String = "0"
            override val versionName: String = ephyra.app.BuildConfig.VERSION_NAME
            override val buildTime: String = "unknown"
            override val githubRepo: String = "Gameaday/Ephyra"
            override val telemetryIncluded: Boolean = ephyra.app.BuildConfig.TELEMETRY_INCLUDED
            override val updaterEnabled: Boolean = ephyra.app.BuildConfig.UPDATER_ENABLED
        }
    }
}
