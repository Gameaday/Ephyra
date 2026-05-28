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
import ephyra.app.crash.CrashActivity
import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.download.DownloadNotifier
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.notification.NotificationManagerImpl
import ephyra.app.data.storage.StorageManagerImpl
import ephyra.app.data.track.jellyfin.SyncJellyfinImpl
import ephyra.app.data.updater.AppUpdateNotifier
import ephyra.app.extension.ExtensionManager
import ephyra.app.extension.api.ExtensionApi
import ephyra.app.extension.util.ExtensionInstaller
import ephyra.app.extension.util.ExtensionLoader
import ephyra.app.installer.AndroidInstallerCapabilityProvider
import ephyra.app.track.DelayedTrackingStore
import ephyra.app.track.TrackingJobSchedulerImpl
import ephyra.app.ui.base.delegate.SecureActivityDelegateImpl
import ephyra.app.ui.base.delegate.ThemingDelegateImpl
import ephyra.app.util.NavigatorImpl
import ephyra.app.util.system.isDebugBuildType
import ephyra.core.common.core.security.PrivacyPreferences
import ephyra.core.common.core.security.SecurityPreferences
import ephyra.core.common.notification.NotificationManager
import ephyra.core.common.preference.DataStorePreferenceStore
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.saver.ImageSaver
import ephyra.core.common.storage.AndroidStorageFolderProvider
import ephyra.core.download.DownloadCache
import ephyra.core.download.DownloadManager
import ephyra.core.download.DownloadPendingDeleter
import ephyra.core.download.DownloadProvider
import ephyra.core.download.DownloadStore
import ephyra.core.download.Downloader
import ephyra.data.backup.BackupDecoder
import ephyra.data.backup.BackupFileValidatorImpl
import ephyra.data.backup.create.BackupCreator
import ephyra.data.backup.create.creators.CategoriesBackupCreator
import ephyra.data.backup.create.creators.ExtensionRepoBackupCreator
import ephyra.data.backup.create.creators.MangaBackupCreator
import ephyra.data.backup.create.creators.PreferenceBackupCreator
import ephyra.data.backup.create.creators.SourcesBackupCreator
import ephyra.data.backup.restore.BackupRestorer
import ephyra.data.backup.restore.restorers.CategoriesRestorer
import ephyra.data.backup.restore.restorers.ExtensionRepoRestorer
import ephyra.data.backup.restore.restorers.MangaRestorer
import ephyra.data.backup.restore.restorers.PreferenceRestorer
import ephyra.data.cache.ChapterCache
import ephyra.data.cache.CoverCache
import ephyra.data.category.CategoryRepositoryImpl
import ephyra.data.chapter.ChapterRepositoryImpl
import ephyra.data.content.ContentDatabaseImpl
import ephyra.data.content.ContentRepositoryImpl
import ephyra.data.content.ContentUnitRepositoryImpl
import ephyra.data.content.merge.OpportunisticMergeManager
import ephyra.data.history.HistoryRepositoryImpl
import ephyra.data.manga.ExcludedScanlatorRepositoryImpl
import ephyra.data.manga.MangaRepositoryImpl
import ephyra.data.release.ReleaseServiceImpl
import ephyra.data.repository.ExtensionRepoRepositoryImpl
import ephyra.data.room.EphyraDatabase
import ephyra.data.room.daos.CategoryDao
import ephyra.data.room.daos.ChapterDao
import ephyra.data.room.daos.ExcludedScanlatorDao
import ephyra.data.room.daos.ExtensionRepoDao
import ephyra.data.room.daos.HistoryDao
import ephyra.data.room.daos.MangaDao
import ephyra.data.room.daos.SourceDao
import ephyra.data.room.daos.TrackDao
import ephyra.data.room.daos.UpdateDao
import ephyra.data.saver.ImageSaverImpl
import ephyra.data.source.SourceRepositoryImpl
import ephyra.data.source.StubSourceRepositoryImpl
import ephyra.data.sourcing.DynamicScraperUpdater
import ephyra.data.sourcing.ScriptableContentSourceEngine
import ephyra.data.track.TrackRepositoryImpl
import ephyra.data.track.TrackerManagerImpl
import ephyra.data.track.TrackingServiceImpl
import ephyra.data.updater.AppUpdateChecker
import ephyra.data.updates.UpdatesRepositoryImpl
import ephyra.domain.backup.service.BackupFileValidator
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.base.BasePreferences
import ephyra.domain.base.InstallerCapabilityProvider
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
import ephyra.domain.chapter.interactor.GetAvailableScanlators
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
import ephyra.domain.content.interactor.GetContentItem
import ephyra.domain.content.interactor.GetContentUnits
import ephyra.domain.content.repository.ContentDatabase
import ephyra.domain.content.repository.ContentRepository
import ephyra.domain.content.repository.ContentUnitRepository
import ephyra.domain.content.source.AdaptiveHeuristicEngine
import ephyra.domain.content.source.ContentSourceEngine
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.HeuristicContentSourceEngine
import ephyra.domain.content.source.RemoteSource
import ephyra.domain.content.source.SourceProfileCache
import ephyra.domain.download.interactor.DeleteDownload
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.extension.interactor.GetExtensionLanguages
import ephyra.domain.extension.interactor.GetExtensionSources
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.interactor.TrustExtension
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
import ephyra.domain.manga.interactor.FindContentSource
import ephyra.domain.manga.interactor.GetDeadFavorites
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.interactor.GetFavoritesByCanonicalId
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.GetMangaWithChapters
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.interactor.ResetViewerFlags
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.interactor.SetMangaViewerFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.repository.ExcludedScanlatorRepository
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.release.interactor.GetApplicationRelease
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
import ephyra.domain.track.service.TrackerManager
import ephyra.domain.track.service.TrackingJobScheduler
import ephyra.domain.track.service.TrackingService
import ephyra.domain.track.store.TrackingQueueStore
import ephyra.domain.ui.UiPreferences
import ephyra.domain.upcoming.interactor.GetUpcomingManga
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.updates.repository.UpdatesRepository
import ephyra.domain.updates.service.UpdatesPreferences
import ephyra.feature.manga.interactor.MangaChapterInteractor
import ephyra.feature.manga.interactor.MangaInfoInteractor
import ephyra.feature.manga.interactor.MangaTrackInteractor
import ephyra.presentation.core.ui.delegate.SecureActivityDelegate
import ephyra.presentation.core.ui.delegate.ThemingDelegate
import ephyra.presentation.core.util.AppNavigator
import ephyra.presentation.core.util.CrashLogUtil
import ephyra.source.api.ScriptableSourceEngine
import ephyra.source.local.image.LocalCoverManager
import ephyra.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.AndroidSourceManager
import ephyra.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import javax.inject.Provider
import javax.inject.Singleton

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
            name = "tachiyomi.db",
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

    @Provides
    @Singleton
    fun provideExcludedScanlatorDao(database: EphyraDatabase) = database.excludedScanlatorDao()

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
        preferenceStore: PreferenceStore,
    ) = StoragePreferences(androidStorageFolderProvider, preferenceStore)

    @Provides
    @Singleton
    fun provideUiPreferences(preferenceStore: PreferenceStore) =
        UiPreferences(preferenceStore)

    @Provides
    @Singleton
    fun provideInstallerCapabilityProvider(@ApplicationContext context: Context): InstallerCapabilityProvider =
        AndroidInstallerCapabilityProvider(context)

    @Provides
    @Singleton
    fun provideBasePreferences(
        capabilityProvider: InstallerCapabilityProvider,
        preferenceStore: PreferenceStore,
    ) = BasePreferences(capabilityProvider, preferenceStore)

    // 4. Repositories Providers
    @Provides
    @Singleton
    fun provideCategoryRepository(categoryDao: CategoryDao): CategoryRepository =
        CategoryRepositoryImpl(categoryDao)

    @Provides
    @Singleton
    fun provideMangaRepository(mangaDao: MangaDao): MangaRepository =
        MangaRepositoryImpl(mangaDao)

    @Provides
    @Singleton
    fun provideChapterRepository(chapterDao: ChapterDao): ChapterRepository =
        ChapterRepositoryImpl(chapterDao)

    @Provides
    @Singleton
    fun provideContentRepository(mangaRepository: MangaRepository): ContentRepository =
        ContentRepositoryImpl(mangaRepository)

    @Provides
    @Singleton
    fun provideContentUnitRepository(chapterRepository: ChapterRepository): ContentUnitRepository =
        ContentUnitRepositoryImpl(chapterRepository)

    @Provides
    @Singleton
    fun provideContentDatabase(
        mangaDao: MangaDao,
        chapterDao: ChapterDao,
    ): ContentDatabase = ContentDatabaseImpl(mangaDao, chapterDao)

    @Provides
    @Singleton
    fun provideSourceProfileCache(preferenceStore: PreferenceStore, json: Json): SourceProfileCache =
        SourceProfileCache(preferenceStore, json)

    @Provides
    @Singleton
    fun provideDynamicScraperUpdater(
        @ApplicationContext context: Context,
        networkHelper: NetworkHelper,
        preferenceStore: PreferenceStore,
    ): DynamicScraperUpdater = DynamicScraperUpdater(context, networkHelper, preferenceStore)

    @Provides
    @Singleton
    fun provideOpportunisticMergeManager(): OpportunisticMergeManager =
        OpportunisticMergeManager()

    @Provides
    @Singleton
    fun provideHeuristicContentSourceEngine(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        networkHelper: NetworkHelper,
        profileCache: SourceProfileCache,
    ): AdaptiveHeuristicEngine =
        AdaptiveHeuristicEngine(ioDispatcher, networkHelper, profileCache)

    @Provides
    @Singleton
    fun provideScriptableContentSourceEngine(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        scraperUpdater: DynamicScraperUpdater,
        scriptEngine: ScriptableSourceEngine,
        preferenceStore: PreferenceStore,
        json: Json,
    ): ScriptableContentSourceEngine =
        ScriptableContentSourceEngine(ioDispatcher, scraperUpdater, scriptEngine, preferenceStore, json)

    @Provides
    @Singleton
    fun provideContentSourceOrchestrator(
        profileCache: SourceProfileCache,
        heuristicEngine: AdaptiveHeuristicEngine,
        scriptEngine: ScriptableContentSourceEngine,
        preferenceStore: PreferenceStore,
    ): ContentSourceOrchestrator =
        ContentSourceOrchestrator(profileCache, heuristicEngine, scriptEngine, preferenceStore)

    @Provides
    @Singleton
    fun provideRemoteSource(
        orchestrator: ContentSourceOrchestrator,
    ): RemoteSource = orchestrator

    @Provides
    @Singleton
    fun provideHistoryRepository(historyDao: HistoryDao): HistoryRepository =
        HistoryRepositoryImpl(historyDao)

    @Provides
    @Singleton
    fun provideUpdatesRepository(updateDao: UpdateDao): UpdatesRepository =
        UpdatesRepositoryImpl(updateDao)

    @Provides
    @Singleton
    fun provideSourceRepository(
        sourceManager: SourceManager,
        mangaDao: MangaDao,
        networkToLocalManga: NetworkToLocalManga,
    ): SourceRepository = SourceRepositoryImpl(sourceManager, mangaDao, networkToLocalManga)

    @Provides
    @Singleton
    fun provideStubSourceRepository(sourceDao: SourceDao): StubSourceRepository =
        StubSourceRepositoryImpl(sourceDao)

    @Provides
    @Singleton
    fun provideExtensionRepoRepository(extensionRepoDao: ExtensionRepoDao): ExtensionRepoRepository =
        ExtensionRepoRepositoryImpl(extensionRepoDao)

    @Provides
    @Singleton
    fun provideTrackRepository(trackDao: TrackDao): TrackRepository =
        TrackRepositoryImpl(trackDao)

    @Provides
    @Singleton
    fun provideExcludedScanlatorRepository(excludedScanlatorDao: ExcludedScanlatorDao): ExcludedScanlatorRepository =
        ExcludedScanlatorRepositoryImpl(excludedScanlatorDao)

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
    fun provideTrustExtension(extensionRepoRepository: ExtensionRepoRepository, sourcePreferences: SourcePreferences) =
        TrustExtension(extensionRepoRepository, sourcePreferences)

    @Provides
    @Singleton
    fun provideExtensionLoader(sourcePreferences: SourcePreferences, trustExtension: TrustExtension) =
        ExtensionLoader(sourcePreferences, trustExtension)

    @Provides
    @Singleton
    fun provideExtensionInstaller(
        @ApplicationContext context: Context,
        basePreferences: BasePreferences,
        networkHelper: NetworkHelper,
        extensionLoader: ExtensionLoader,
    ) = ExtensionInstaller(context, basePreferences, networkHelper, extensionLoader)

    @Provides
    @Singleton
    fun provideExtensionApi(
        networkHelper: NetworkHelper,
        preferenceStore: PreferenceStore,
        getExtensionRepo: GetExtensionRepo,
        updateExtensionRepo: UpdateExtensionRepo,
        securityPreferences: SecurityPreferences,
        extensionLoader: ExtensionLoader,
        json: Json,
    ) = ExtensionApi(
        networkHelper,
        preferenceStore,
        getExtensionRepo,
        updateExtensionRepo,
        securityPreferences,
        extensionLoader,
        json,
    )

    @Provides
    @Singleton
    fun provideExtensionManager(
        @ApplicationContext context: Context,
        sourcePreferences: SourcePreferences,
        trustExtension: TrustExtension,
        securityPreferences: SecurityPreferences,
        extensionLoader: ExtensionLoader,
        extensionApi: ExtensionApi,
        extensionInstaller: ExtensionInstaller,
    ) = ExtensionManager(
        context,
        sourcePreferences,
        trustExtension,
        securityPreferences,
        extensionLoader,
        extensionApi,
        extensionInstaller,
    )

    @Provides
    fun provideDomainExtensionManager(
        extensionManager: ExtensionManager,
    ): ephyra.domain.extension.service.ExtensionManager = extensionManager

    @Provides
    @Singleton
    fun provideSourceManager(
        @ApplicationContext context: Context,
        extensionManager: ExtensionManager,
        stubSourceRepository: StubSourceRepository,
        localSourceFileSystem: LocalSourceFileSystem,
        localCoverManager: LocalCoverManager,
        downloadManagerProvider: Provider<DownloadManager>,
        json: Json,
        xml: XML,
    ): SourceManager = AndroidSourceManager(
        context = context,
        extensionManager = extensionManager,
        sourceRepository = stubSourceRepository,
        fileSystem = localSourceFileSystem,
        coverManager = localCoverManager,
        downloadManagerProvider = downloadManagerProvider,
        json = json,
        xml = xml,
    )

    @Provides
    @Singleton
    fun provideLocalSourceFileSystem(storageManager: StorageManager) =
        LocalSourceFileSystem(storageManager)

    @Provides
    @Singleton
    fun provideLocalCoverManager(
        @ApplicationContext context: Context,
        localSourceFileSystem: LocalSourceFileSystem,
    ) = LocalCoverManager(context, localSourceFileSystem)

    @Provides
    @Singleton
    fun provideStorageManager(
        @ApplicationContext context: Context,
        storagePreferences: StoragePreferences,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): StorageManager = StorageManagerImpl(context, storagePreferences, ioDispatcher)

    @Provides
    @Singleton
    fun provideDownloadStore(
        @ApplicationContext context: Context,
        sourceManager: SourceManager,
        json: Json,
        getManga: GetManga,
        getChapter: GetChapter,
    ): DownloadStore = DownloadStore(context, sourceManager, json, getManga, getChapter)

    @Provides
    @Singleton
    fun provideDownloadProvider(
        @ApplicationContext context: Context,
        storageManager: StorageManager,
        libraryPreferences: LibraryPreferences,
    ): DownloadProvider = DownloadProvider(context, storageManager, libraryPreferences)

    @Provides
    @Singleton
    fun provideDownloadCache(
        application: Application,
        downloadProvider: DownloadProvider,
        sourceManager: SourceManager,
        storageManager: StorageManager,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DownloadCache = DownloadCache(application, downloadProvider, sourceManager, storageManager, ioDispatcher)

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        downloadProvider: DownloadProvider,
        downloadCache: DownloadCache,
        getCategories: GetCategories,
        getManga: GetManga,
        getChapter: GetChapter,
        sourceManager: SourceManager,
        downloadPreferences: DownloadPreferences,
        libraryPreferences: LibraryPreferences,
        downloader: Downloader,
        pendingDeleter: DownloadPendingDeleter,
    ): DownloadManager = DownloadManager(
        context = context,
        provider = downloadProvider,
        cache = downloadCache,
        getCategories = getCategories,
        getManga = getManga,
        getChapter = getChapter,
        sourceManager = sourceManager,
        downloadPreferences = downloadPreferences,
        libraryPreferences = libraryPreferences,
        downloader = downloader,
        pendingDeleter = pendingDeleter,
    )

    @Provides
    @Singleton
    fun provideDomainDownloadManager(
        downloadManager: DownloadManager,
    ): ephyra.domain.download.service.DownloadManager = downloadManager

    @Provides
    @Singleton
    fun provideDownloader(
        @ApplicationContext context: Context,
        downloadProvider: DownloadProvider,
        downloadCache: DownloadCache,
        sourceManager: SourceManager,
        chapterCache: ChapterCache,
        downloadPreferences: DownloadPreferences,
        readerPreferences: ReaderPreferences,
        libraryPreferences: LibraryPreferences,
        xml: XML,
        getCategories: GetCategories,
        getTracks: GetTracks,
        downloadNotifier: ephyra.domain.download.service.DownloadNotifier,
        downloadStore: DownloadStore,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): Downloader = Downloader(
        context = context,
        provider = downloadProvider,
        cache = downloadCache,
        sourceManager = sourceManager,
        chapterCache = chapterCache,
        downloadPreferences = downloadPreferences,
        readerPreferences = readerPreferences,
        libraryPreferences = libraryPreferences,
        xml = xml,
        getCategories = getCategories,
        getTracks = getTracks,
        notifier = downloadNotifier,
        store = downloadStore,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideDownloadPendingDeleter(@ApplicationContext context: Context, json: Json) =
        DownloadPendingDeleter(context, json)

    @Provides
    @Singleton
    fun provideDownloadNotifier(
        @ApplicationContext context: Context,
        securityPreferences: SecurityPreferences,
    ): ephyra.domain.download.service.DownloadNotifier =
        DownloadNotifier(context, securityPreferences)

    @Provides
    @Singleton
    fun provideTrackerManager(
        application: Application,
        trackPreferences: TrackPreferences,
        libraryPreferences: LibraryPreferences,
        sourceManager: SourceManager,
        networkHelper: NetworkHelper,
        addTracks: AddTracks,
        insertTrack: InsertTrack,
        json: Json,
    ): TrackerManager = TrackerManagerImpl(
        context = application,
        trackPreferences = trackPreferences,
        libraryPreferences = libraryPreferences,
        sourceManager = sourceManager,
        networkService = networkHelper,
        addTracks = addTracks,
        insertTrack = insertTrack,
        json = json,
    )

    @Provides
    @Singleton
    fun provideTrackingService(
        trackerManager: TrackerManager,
        addTracks: AddTracks,
        trackRepository: TrackRepository,
    ): TrackingService = TrackingServiceImpl(trackerManager, addTracks, trackRepository)

    @Provides
    @Singleton
    fun provideTrackingQueueStore(@ApplicationContext context: Context): TrackingQueueStore =
        DelayedTrackingStore(context)

    @Provides
    @Singleton
    fun provideTrackingJobScheduler(@ApplicationContext context: Context): TrackingJobScheduler =
        TrackingJobSchedulerImpl(context)

    @Provides
    @Singleton
    fun provideImageSaver(@ApplicationContext context: Context): ImageSaver = ImageSaverImpl(context)

    @Provides
    @Singleton
    fun provideChapterCache(
        @ApplicationContext context: Context,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) = ChapterCache(context, json, ioDispatcher)

    @Provides
    @Singleton
    fun provideCoverCache(@ApplicationContext context: Context) = CoverCache(context)

    @Provides
    fun provideDomainCoverCache(
        coverCache: ephyra.data.cache.CoverCache,
    ): ephyra.domain.manga.service.CoverCache = coverCache

    @Provides
    @Singleton
    fun provideCrashLogUtil(@ApplicationContext context: Context, extensionManager: ExtensionManager) =
        CrashLogUtil(context, extensionManager)

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
    fun provideSecureActivityDelegate(
        basePreferences: BasePreferences,
        securityPreferences: SecurityPreferences,
    ): SecureActivityDelegate =
        SecureActivityDelegateImpl(basePreferences, securityPreferences)

    // 6. Backup & Restore
    @Provides
    @Singleton
    fun provideBackupDecoder(@ApplicationContext context: Context, protoBuf: ProtoBuf) =
        BackupDecoder(context, protoBuf)

    @Provides
    @Singleton
    fun provideBackupFileValidator(
        @ApplicationContext context: Context,
        trackerManager: TrackerManager,
        sourceManager: SourceManager,
    ): BackupFileValidator = BackupFileValidatorImpl(context, trackerManager, sourceManager)

    @Provides
    @Singleton
    fun provideCategoriesBackupCreator(getCategories: GetCategories) =
        CategoriesBackupCreator(getCategories)

    @Provides
    @Singleton
    fun provideMangaBackupCreator(
        getCategories: GetCategories,
        getHistory: GetHistory,
        getChaptersByMangaId: GetChaptersByMangaId,
        getTracks: GetTracks,
        getExcludedScanlators: GetExcludedScanlators,
    ) = MangaBackupCreator(getCategories, getHistory, getChaptersByMangaId, getTracks, getExcludedScanlators)

    @Provides
    @Singleton
    fun providePreferenceBackupCreator(sourceManager: SourceManager, preferenceStore: PreferenceStore) =
        PreferenceBackupCreator(sourceManager, preferenceStore)

    @Provides
    @Singleton
    fun provideExtensionRepoBackupCreator(getExtensionRepo: GetExtensionRepo) =
        ExtensionRepoBackupCreator(getExtensionRepo)

    @Provides
    @Singleton
    fun provideSourcesBackupCreator(sourceManager: SourceManager) =
        SourcesBackupCreator(sourceManager)

    @Provides
    @Singleton
    fun provideBackupCreator(
        @ApplicationContext context: Context,
        protoBuf: ProtoBuf,
        getFavorites: GetFavorites,
        backupPreferences: BackupPreferences,
        mangaRepository: MangaRepository,
        categoriesBackupCreator: CategoriesBackupCreator,
        mangaBackupCreator: MangaBackupCreator,
        preferenceBackupCreator: PreferenceBackupCreator,
        extensionRepoBackupCreator: ExtensionRepoBackupCreator,
        sourcesBackupCreator: SourcesBackupCreator,
        storageManager: StorageManager,
    ) = BackupCreator(
        context = context,
        parser = protoBuf,
        getFavorites = getFavorites,
        backupPreferences = backupPreferences,
        mangaRepository = mangaRepository,
        categoriesBackupCreator = categoriesBackupCreator,
        mangaBackupCreator = mangaBackupCreator,
        preferenceBackupCreator = preferenceBackupCreator,
        extensionRepoBackupCreator = extensionRepoBackupCreator,
        sourcesBackupCreator = sourcesBackupCreator,
        storageManager = storageManager,
    )

    @Provides
    @Singleton
    fun provideCategoriesRestorer(
        categoryRepository: CategoryRepository,
        getCategories: GetCategories,
        libraryPreferences: LibraryPreferences,
    ) = CategoriesRestorer(categoryRepository, getCategories, libraryPreferences)

    @Provides
    @Singleton
    fun providePreferenceRestorer(
        @ApplicationContext context: Context,
        getCategories: GetCategories,
        preferenceStore: PreferenceStore,
        libraryPreferences: LibraryPreferences,
        backupPreferences: BackupPreferences,
        backupScheduler: ephyra.domain.backup.service.BackupScheduler,
        libraryUpdateScheduler: ephyra.domain.library.service.LibraryUpdateScheduler,
    ) = PreferenceRestorer(
        context = context,
        getCategories = getCategories,
        preferenceStore = preferenceStore,
        libraryPreferences = libraryPreferences,
        backupPreferences = backupPreferences,
        backupScheduler = backupScheduler,
        libraryUpdateScheduler = libraryUpdateScheduler,
    )

    @Provides
    @Singleton
    fun provideExtensionRepoRestorer(
        extensionRepoRepository: ExtensionRepoRepository,
        getExtensionRepos: GetExtensionRepo,
    ) = ExtensionRepoRestorer(extensionRepoRepository, getExtensionRepos)

    @Provides
    @Singleton
    fun provideMangaRestorer(
        database: EphyraDatabase,
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
        historyRepository: HistoryRepository,
        upsertHistory: UpsertHistory,
        getCategories: GetCategories,
        getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
        getChaptersByMangaId: GetChaptersByMangaId,
        updateManga: UpdateManga,
        getTracks: GetTracks,
        insertTrack: InsertTrack,
        getExcludedScanlators: GetExcludedScanlators,
        setExcludedScanlators: SetExcludedScanlators,
        fetchInterval: FetchInterval,
    ) = MangaRestorer(
        database = database,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        historyRepository = historyRepository,
        upsertHistory = upsertHistory,
        getCategories = getCategories,
        getMangaByUrlAndSourceId = getMangaByUrlAndSourceId,
        getChaptersByMangaId = getChaptersByMangaId,
        updateManga = updateManga,
        getTracks = getTracks,
        insertTrack = insertTrack,
        getExcludedScanlators = getExcludedScanlators,
        setExcludedScanlators = setExcludedScanlators,
        fetchInterval = fetchInterval,
    )

    @Provides
    @Singleton
    fun provideBackupRestorer(
        @ApplicationContext context: Context,
        categoriesRestorer: CategoriesRestorer,
        preferenceRestorer: PreferenceRestorer,
        extensionRepoRestorer: ExtensionRepoRestorer,
        mangaRestorer: MangaRestorer,
        backupNotifier: ephyra.domain.backup.service.BackupNotifier,
    ) = BackupRestorer(
        context = context,
        categoriesRestorer = categoriesRestorer,
        preferenceRestorer = preferenceRestorer,
        extensionRepoRestorer = extensionRepoRestorer,
        mangaRestorer = mangaRestorer,
        notifier = backupNotifier,
    )

    @Provides
    @Singleton
    fun provideAppUpdateNotifier(
        @ApplicationContext context: Context,
    ): ephyra.domain.release.service.AppUpdateNotifier =
        AppUpdateNotifier(context)

    @Provides
    @Singleton
    fun provideAppUpdateDownloader(
        @ApplicationContext context: Context,
    ): ephyra.domain.release.service.AppUpdateDownloader =
        ephyra.app.data.updater.AppUpdateDownloaderImpl(context)

    @Provides
    @Singleton
    fun provideAppUpdateChecker(
        getApplicationRelease: GetApplicationRelease,
        appUpdateNotifier: ephyra.domain.release.service.AppUpdateNotifier,
    ) = AppUpdateChecker(getApplicationRelease, appUpdateNotifier)

    @Provides
    @Singleton
    fun provideLibraryUpdateNotifier(
        @ApplicationContext context: Context,
        securityPreferences: SecurityPreferences,
        sourceManager: SourceManager,
    ) = LibraryUpdateNotifier(context, securityPreferences, sourceManager)

    @Provides
    @Singleton
    fun provideBackupNotifier(@ApplicationContext context: Context) =
        BackupNotifier(context)

    @Provides
    @Singleton
    fun provideBackupNotifierDomain(@ApplicationContext context: Context): ephyra.domain.backup.service.BackupNotifier =
        BackupNotifier(context)

    @Provides
    @Singleton
    fun provideChapterCacheDomain(
        @ApplicationContext context: Context,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): ephyra.domain.chapter.service.ChapterCache =
        ChapterCache(context, json, ioDispatcher)

    @Provides
    @Singleton
    fun provideSnackbarHostState(): androidx.compose.material3.SnackbarHostState =
        androidx.compose.material3.SnackbarHostState()

    @Provides
    @Singleton
    fun provideLibraryExporter(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): ephyra.domain.export.LibraryExporter =
        ephyra.data.export.LibraryExporterImpl(context, ioDispatcher)

    @Provides
    @Singleton
    fun provideMatchUnlinkedJobRunner(): ephyra.presentation.core.ui.MatchUnlinkedJobRunner =
        object : ephyra.presentation.core.ui.MatchUnlinkedJobRunner {
            override fun isRunning(context: Context): Boolean = false
            override fun start(context: Context) {
                // No-op default runner used to satisfy DI during build; replace with real implementation if needed.
            }
        }

    @Provides
    @Singleton
    fun provideReleaseService(networkHelper: NetworkHelper, json: Json): ReleaseService =
        ReleaseServiceImpl(networkHelper, json)

    // 7. Interactors (Use Cases) Providers
    @Provides
    fun provideGetCategories(categoryRepository: CategoryRepository) = GetCategories(categoryRepository)

    @Provides
    fun provideResetCategoryFlags(libraryPreferences: LibraryPreferences, categoryRepository: CategoryRepository) =
        ResetCategoryFlags(libraryPreferences, categoryRepository)

    @Provides
    fun provideSetDisplayMode(libraryPreferences: LibraryPreferences) = SetDisplayMode(libraryPreferences)

    @Provides
    fun provideSetSortModeForCategory(libraryPreferences: LibraryPreferences, categoryRepository: CategoryRepository) =
        SetSortModeForCategory(libraryPreferences, categoryRepository)

    @Provides
    fun provideCreateCategoryWithName(categoryRepository: CategoryRepository, libraryPreferences: LibraryPreferences) =
        CreateCategoryWithName(categoryRepository, libraryPreferences)

    @Provides
    fun provideRenameCategory(categoryRepository: CategoryRepository) = RenameCategory(categoryRepository)

    @Provides
    fun provideReorderCategory(categoryRepository: CategoryRepository) = ReorderCategory(categoryRepository)

    @Provides
    fun provideUpdateCategory(categoryRepository: CategoryRepository) = UpdateCategory(categoryRepository)

    @Provides
    fun provideDeleteCategory(
        categoryRepository: CategoryRepository,
        libraryPreferences: LibraryPreferences,
        downloadPreferences: DownloadPreferences,
    ) = DeleteCategory(categoryRepository, libraryPreferences, downloadPreferences)

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
    fun provideGetContentItem(contentRepository: ContentRepository) = GetContentItem(contentRepository)

    @Provides
    fun provideGetContentUnits(contentUnitRepository: ContentUnitRepository) = GetContentUnits(contentUnitRepository)

    @Provides
    fun provideGetNextChapters(
        getChaptersByMangaId: GetChaptersByMangaId,
        getManga: GetManga,
        historyRepository: HistoryRepository,
    ) = GetNextChapters(getChaptersByMangaId, getManga, historyRepository)

    @Provides
    fun provideGetUpcomingManga(mangaRepository: MangaRepository) = GetUpcomingManga(mangaRepository)

    @Provides
    fun provideResetViewerFlags(mangaRepository: MangaRepository) = ResetViewerFlags(mangaRepository)

    @Provides
    fun provideSetMangaChapterFlags(mangaRepository: MangaRepository) = SetMangaChapterFlags(mangaRepository)

    @Provides
    fun provideFetchInterval(getChaptersByMangaId: GetChaptersByMangaId) = FetchInterval(getChaptersByMangaId)

    @Provides
    fun provideSetMangaDefaultChapterFlags(
        libraryPreferences: LibraryPreferences,
        setMangaChapterFlags: SetMangaChapterFlags,
        getFavorites: GetFavorites,
    ) = SetMangaDefaultChapterFlags(libraryPreferences, setMangaChapterFlags, getFavorites)

    @Provides
    fun provideSetMangaViewerFlags(mangaRepository: MangaRepository) = SetMangaViewerFlags(mangaRepository)

    @Provides
    fun provideNetworkToLocalManga(mangaRepository: MangaRepository) = NetworkToLocalManga(mangaRepository)

    @Provides
    fun provideUpdateManga(
        mangaRepository: MangaRepository,
        fetchInterval: FetchInterval,
        coverCache: CoverCache,
        libraryPreferences: LibraryPreferences,
        downloadManager: DownloadManager,
        trackPreferences: TrackPreferences,
    ) = UpdateManga(mangaRepository, fetchInterval, coverCache, libraryPreferences, downloadManager, trackPreferences)

    @Provides
    fun provideFindContentSource(sourceManager: SourceManager, getFavoritesByCanonicalId: GetFavoritesByCanonicalId) =
        FindContentSource(sourceManager, getFavoritesByCanonicalId)

    @Provides
    fun provideUpdateMangaNotes(mangaRepository: MangaRepository) = UpdateMangaNotes(mangaRepository)

    @Provides
    fun provideSetMangaCategories(mangaRepository: MangaRepository) = SetMangaCategories(mangaRepository)

    @Provides
    fun provideGetExcludedScanlators(
        excludedScanlatorRepository: ExcludedScanlatorRepository,
    ) = GetExcludedScanlators(excludedScanlatorRepository)

    @Provides
    fun provideSetExcludedScanlators(
        excludedScanlatorRepository: ExcludedScanlatorRepository,
    ) = SetExcludedScanlators(excludedScanlatorRepository)

    @Provides
    fun provideDeleteNonLibraryManga(mangaRepository: MangaRepository) = DeleteNonLibraryManga(mangaRepository)

    @Provides
    fun provideMigrateManga(
        sourcePreferences: SourcePreferences,
        trackerManager: TrackerManager,
        sourceManager: SourceManager,
        downloadManager: DownloadManager,
        updateManga: UpdateManga,
        getChaptersByMangaId: GetChaptersByMangaId,
        syncChaptersWithSource: SyncChaptersWithSource,
        updateChapter: UpdateChapter,
        getCategories: GetCategories,
        setMangaCategories: SetMangaCategories,
        getTracks: GetTracks,
        insertTrack: InsertTrack,
        coverCache: ephyra.domain.manga.service.CoverCache,
    ) = MigrateMangaUseCase(
        sourcePreferences,
        trackerManager,
        sourceManager,
        downloadManager,
        updateManga,
        getChaptersByMangaId,
        syncChaptersWithSource,
        updateChapter,
        getCategories,
        setMangaCategories,
        getTracks,
        insertTrack,
        coverCache,
    )

    @Provides
    fun provideGetApplicationRelease(releaseService: ReleaseService, preferenceStore: PreferenceStore) =
        GetApplicationRelease(releaseService, preferenceStore)

    @Provides
    fun provideTrackChapter(
        getTracks: GetTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertTrack,
        trackingQueue: TrackingQueueStore,
        trackingJobScheduler: TrackingJobScheduler,
    ) = TrackChapter(getTracks, trackerManager, insertTrack, trackingQueue, trackingJobScheduler)

    @Provides
    fun provideAddTracks(
        insertTrack: InsertTrack,
        syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
        getChaptersByMangaId: GetChaptersByMangaId,
        getHistory: GetHistory,
        trackerManagerProvider: javax.inject.Provider<TrackerManager>,
        mangaRepository: MangaRepository,
    ) = AddTracks(
        insertTrack = insertTrack,
        syncChapterProgressWithTrack = syncChapterProgressWithTrack,
        getChaptersByMangaId = getChaptersByMangaId,
        getHistory = getHistory,
        trackerManagerProvider = { trackerManagerProvider.get() },
        mangaRepository = mangaRepository,
    )

    @Provides
    fun provideRefreshTracks(
        getTracks: GetTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertTrack,
        syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    ) = RefreshTracks(getTracks, trackerManager, insertTrack, syncChapterProgressWithTrack)

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
        updateChapter: UpdateChapter,
        insertTrack: InsertTrack,
        getChaptersByMangaId: GetChaptersByMangaId,
    ) = SyncChapterProgressWithTrack(updateChapter, insertTrack, getChaptersByMangaId)

    @Provides
    fun provideTrackerListImporter(
        mangaRepository: MangaRepository,
        insertTrack: InsertTrack,
        trackerManager: TrackerManager,
        generateAuthorityChapters: GenerateAuthorityChapters,
    ) = TrackerListImporter(mangaRepository, insertTrack, trackerManager, generateAuthorityChapters)

    @Provides
    fun provideLinkTrackedMangaToAuthority(mangaRepository: MangaRepository, getTracks: GetTracks) =
        LinkTrackedMangaToAuthority(mangaRepository, getTracks)

    @Provides
    fun provideMatchUnlinkedManga(
        mangaRepository: MangaRepository,
        trackerManager: TrackerManager,
        getTracks: GetTracks,
        trackPreferences: TrackPreferences,
    ) = MatchUnlinkedManga(mangaRepository, trackerManager, getTracks, trackPreferences)

    @Provides
    fun provideRefreshCanonicalMetadata(
        mangaRepository: MangaRepository,
        trackerManager: TrackerManager,
        trackPreferences: TrackPreferences,
        coverCache: ephyra.domain.manga.service.CoverCache,
    ) = RefreshCanonicalMetadata(mangaRepository, trackerManager, trackPreferences, coverCache)

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
        downloadPreferences: DownloadPreferences,
        deleteDownload: DeleteDownload,
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
    ) = SetReadStatus(downloadPreferences, deleteDownload, mangaRepository, chapterRepository)

    @Provides
    fun provideShouldUpdateDbChapter() = ShouldUpdateDbChapter()

    @Provides
    fun provideSyncChaptersWithSource(
        downloadManager: DownloadManager,
        downloadProvider: DownloadProvider,
        chapterRepository: ChapterRepository,
        shouldUpdateDbChapter: ShouldUpdateDbChapter,
        updateManga: UpdateManga,
        updateChapter: UpdateChapter,
        getChaptersByMangaId: GetChaptersByMangaId,
        getExcludedScanlators: GetExcludedScanlators,
        libraryPreferences: LibraryPreferences,
        setMangaChapterFlags: SetMangaChapterFlags,
    ) = SyncChaptersWithSource(
        downloadManager,
        downloadProvider,
        chapterRepository,
        shouldUpdateDbChapter,
        updateManga,
        updateChapter,
        getChaptersByMangaId,
        getExcludedScanlators,
        libraryPreferences,
        setMangaChapterFlags,
    )

    @Provides
    fun provideGetAvailableScanlators(chapterRepository: ChapterRepository) =
        GetAvailableScanlators(chapterRepository)

    @Provides
    fun provideFilterChaptersForDownload(
        getChaptersByMangaId: GetChaptersByMangaId,
        downloadPreferences: DownloadPreferences,
        getCategories: GetCategories,
    ) = FilterChaptersForDownload(getChaptersByMangaId, downloadPreferences, getCategories)

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
    fun provideGetExtensionsByType(
        extensionManager: ephyra.domain.extension.service.ExtensionManager,
        sourcePreferences: SourcePreferences,
    ) =
        GetExtensionsByType(sourcePreferences, extensionManager)

    @Provides
    fun provideGetExtensionSources(sourcePreferences: SourcePreferences) =
        GetExtensionSources(sourcePreferences)

    @Provides
    fun provideGetExtensionLanguages(
        extensionManager: ephyra.domain.extension.service.ExtensionManager,
        sourcePreferences: SourcePreferences,
    ) =
        GetExtensionLanguages(sourcePreferences, extensionManager)

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
    fun provideSetMigrateSorting(sourcePreferences: SourcePreferences) = SetMigrateSorting(sourcePreferences)

    @Provides
    fun provideToggleLanguage(sourcePreferences: SourcePreferences) = ToggleLanguage(sourcePreferences)

    @Provides
    fun provideToggleSource(sourcePreferences: SourcePreferences) = ToggleSource(sourcePreferences)

    @Provides
    fun provideToggleSourcePin(sourcePreferences: SourcePreferences) = ToggleSourcePin(sourcePreferences)

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
        extensionRepoService: ExtensionRepoService,
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
        extensionRepoService: ExtensionRepoService,
    ) = UpdateExtensionRepo(extensionRepoRepository, extensionRepoService)

    @Provides
    fun provideToggleIncognito(sourcePreferences: SourcePreferences) = ToggleIncognito(sourcePreferences)

    @Provides
    fun provideGetIncognitoState(
        basePreferences: BasePreferences,
        sourcePreferences: SourcePreferences,
        extensionManager: ephyra.domain.extension.service.ExtensionManager,
    ) = GetIncognitoState(basePreferences, sourcePreferences, extensionManager)

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
        setMangaCategories: SetMangaCategories,
    ) = MangaInfoInteractor(
        updateManga,
        mangaRepository,
        refreshCanonical,
        matchUnlinkedManga,
        syncJellyfin,
        setExcludedScanlators,
        setMangaCategories,
    )

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
        downloadManager: DownloadManager,
    ) = MangaChapterInteractor(
        setMangaChapterFlags,
        setMangaDefaultChapterFlags,
        setReadStatus,
        updateChapter,
        libraryPreferences,
        filterChaptersForDownload,
        syncChaptersWithSource,
        downloadManager,
    )

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
        refreshTracks: RefreshTracks,
    ) = MangaTrackInteractor(
        getTracks,
        addTracks,
        refreshCanonical,
        matchUnlinkedManga,
        trackChapter,
        trackPreferences,
        trackerManager,
        refreshTracks,
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
    fun provideBackupScheduler(
        workScheduler: ephyra.app.data.scheduler.WorkSchedulerImpl,
    ): ephyra.domain.backup.service.BackupScheduler = workScheduler

    @Provides
    @Singleton
    fun provideRestoreScheduler(
        workScheduler: ephyra.app.data.scheduler.WorkSchedulerImpl,
    ): ephyra.domain.backup.service.RestoreScheduler = workScheduler

    @Provides
    @Singleton
    fun provideLibraryUpdateScheduler(
        workScheduler: ephyra.app.data.scheduler.WorkSchedulerImpl,
    ): ephyra.domain.library.service.LibraryUpdateScheduler = workScheduler

    @Provides
    @Singleton
    fun provideMetadataUpdateScheduler(
        workScheduler: ephyra.app.data.scheduler.WorkSchedulerImpl,
    ): ephyra.domain.library.service.MetadataUpdateScheduler = workScheduler

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
