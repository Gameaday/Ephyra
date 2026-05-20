package ephyra.app.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ephyra.app.data.backup.BackupNotifier
import ephyra.app.data.backup.create.BackupCreateJob
import ephyra.app.data.backup.create.BackupCreator
import ephyra.app.data.backup.restore.BackupRestoreJob
import ephyra.app.data.backup.restore.BackupRestorer
import ephyra.app.data.download.DownloadJob
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.app.data.library.LibraryUpdateNotifier
import ephyra.app.data.library.MetadataUpdateJob
import ephyra.app.data.updater.AppUpdateDownloadJob
import ephyra.data.cache.CoverCache
import ephyra.domain.backup.service.BackupPreferences
import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.storage.service.StorageManager
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.RefreshCanonicalMetadata
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.service.DelayedTrackingUpdateJob
import ephyra.domain.track.store.DelayedTrackingStore
import ephyra.feature.download.DownloadManager
import eu.kanade.tachiyomi.network.NetworkHelper

/**
 * A standard Dagger/Hilt EntryPoint to bridge Android WorkManager background jobs
 * with Ephyra's dependency graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerFactoryEntryPoint {
    fun networkHelper(): NetworkHelper
    fun backupCreator(): BackupCreator
    fun storageManager(): StorageManager
    fun backupPreferences(): BackupPreferences
    fun backupNotifier(): BackupNotifier
    fun backupRestorer(): BackupRestorer
    fun downloadManager(): DownloadManager
    fun downloadPreferences(): DownloadPreferences
    fun getTracks(): GetTracks
    fun trackChapter(): TrackChapter
    fun delayedTrackingStore(): DelayedTrackingStore
    fun sourceManager(): SourceManager
    fun coverCache(): CoverCache
    fun getLibraryManga(): GetLibraryManga
    fun getManga(): GetManga
    fun updateManga(): UpdateManga
    fun syncChaptersWithSource(): SyncChaptersWithSource
    fun fetchInterval(): FetchInterval
    fun filterChaptersForDownload(): FilterChaptersForDownload
    fun getChaptersByMangaId(): GetChaptersByMangaId
    fun refreshCanonicalMetadata(): RefreshCanonicalMetadata
    fun libraryUpdateNotifier(): LibraryUpdateNotifier
    fun libraryPreferences(): LibraryPreferences
}

/**
 * A WorkManager WorkerFactory that retrieves Ephyra dependency singletons
 * from the Hilt EntryPoint at runtime.
 */
class AppWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            WorkerFactoryEntryPoint::class.java
        )
        return when (workerClassName) {
            AppUpdateDownloadJob::class.java.name -> AppUpdateDownloadJob(
                appContext,
                workerParameters,
                entryPoint.networkHelper()
            )
            BackupCreateJob::class.java.name -> BackupCreateJob(
                appContext,
                workerParameters,
                entryPoint.backupCreator(),
                entryPoint.storageManager(),
                entryPoint.backupPreferences(),
                entryPoint.backupNotifier()
            )
            BackupRestoreJob::class.java.name -> BackupRestoreJob(
                appContext,
                workerParameters,
                entryPoint.backupRestorer(),
                entryPoint.backupNotifier()
            )
            DownloadJob::class.java.name -> DownloadJob(
                appContext,
                workerParameters,
                entryPoint.downloadManager(),
                entryPoint.downloadPreferences()
            )
            DelayedTrackingUpdateJob::class.java.name -> DelayedTrackingUpdateJob(
                appContext,
                workerParameters,
                entryPoint.getTracks(),
                entryPoint.trackChapter(),
                entryPoint.delayedTrackingStore()
            )
            MetadataUpdateJob::class.java.name -> MetadataUpdateJob(
                appContext,
                workerParameters,
                entryPoint.sourceManager(),
                entryPoint.coverCache(),
                entryPoint.getLibraryManga(),
                entryPoint.updateManga(),
                entryPoint.libraryUpdateNotifier()
            )
            LibraryUpdateJob::class.java.name -> LibraryUpdateJob(
                appContext,
                workerParameters,
                entryPoint.sourceManager(),
                entryPoint.libraryPreferences(),
                entryPoint.downloadManager(),
                entryPoint.coverCache(),
                entryPoint.getLibraryManga(),
                entryPoint.getManga(),
                entryPoint.updateManga(),
                entryPoint.syncChaptersWithSource(),
                entryPoint.fetchInterval(),
                entryPoint.filterChaptersForDownload(),
                entryPoint.getChaptersByMangaId(),
                entryPoint.refreshCanonicalMetadata(),
                entryPoint.libraryUpdateNotifier()
            )
            else -> null
        }
    }
}
