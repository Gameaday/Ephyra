package ephyra.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ephyra.app.data.notification.NotificationManagerImpl
import ephyra.app.data.storage.StorageManagerImpl
import ephyra.core.common.notification.NotificationManager
import ephyra.data.category.CategoryRepositoryImpl
import ephyra.data.chapter.ChapterRepositoryImpl
import ephyra.data.content.ContentDatabaseImpl
import ephyra.data.content.ContentRepositoryImpl
import ephyra.data.content.ContentUnitRepositoryImpl
import ephyra.data.history.HistoryRepositoryImpl
import ephyra.data.manga.ExcludedScanlatorRepositoryImpl
import ephyra.data.manga.MangaRepositoryImpl
import ephyra.data.repository.ExtensionRepoRepositoryImpl
import ephyra.data.source.SourceRepositoryImpl
import ephyra.data.source.StubSourceRepositoryImpl
import ephyra.data.track.TrackRepositoryImpl
import ephyra.data.updates.UpdatesRepositoryImpl
import ephyra.domain.category.repository.CategoryRepository
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.content.repository.ContentDatabase
import ephyra.domain.content.repository.ContentRepository
import ephyra.domain.content.repository.ContentUnitRepository
import ephyra.domain.extensionrepo.repository.ExtensionRepoRepository
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.manga.repository.ExcludedScanlatorRepository
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.repository.StubSourceRepository
import ephyra.domain.storage.service.StorageManager
import ephyra.domain.track.repository.TrackRepository
import ephyra.domain.updates.repository.UpdatesRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryBindingsModule {

    @Binds
    @Singleton
    fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    fun bindMangaRepository(impl: MangaRepositoryImpl): MangaRepository

    @Binds
    @Singleton
    fun bindChapterRepository(impl: ChapterRepositoryImpl): ChapterRepository

    @Binds
    @Singleton
    fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository

    @Binds
    @Singleton
    fun bindContentUnitRepository(impl: ContentUnitRepositoryImpl): ContentUnitRepository

    @Binds
    @Singleton
    fun bindContentDatabase(impl: ContentDatabaseImpl): ContentDatabase

    @Binds
    @Singleton
    fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    fun bindUpdatesRepository(impl: UpdatesRepositoryImpl): UpdatesRepository

    @Binds
    @Singleton
    fun bindSourceRepository(impl: SourceRepositoryImpl): SourceRepository

    @Binds
    @Singleton
    fun bindStubSourceRepository(impl: StubSourceRepositoryImpl): StubSourceRepository

    @Binds
    @Singleton
    fun bindExtensionRepoRepository(impl: ExtensionRepoRepositoryImpl): ExtensionRepoRepository

    @Binds
    @Singleton
    fun bindTrackRepository(impl: TrackRepositoryImpl): TrackRepository

    @Binds
    @Singleton
    fun bindExcludedScanlatorRepository(impl: ExcludedScanlatorRepositoryImpl): ExcludedScanlatorRepository

    @Binds
    @Singleton
    fun bindNotificationManager(impl: NotificationManagerImpl): NotificationManager

    @Binds
    @Singleton
    fun bindStorageManager(impl: StorageManagerImpl): StorageManager
}
