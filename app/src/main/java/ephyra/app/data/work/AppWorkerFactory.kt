package ephyra.app.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import ephyra.app.App
import ephyra.app.data.backup.create.BackupCreateJob
import ephyra.app.data.backup.restore.BackupRestoreJob
import ephyra.app.data.download.DownloadJob
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.app.data.library.MetadataUpdateJob
import ephyra.app.data.updater.AppUpdateDownloadJob
import ephyra.domain.track.service.DelayedTrackingUpdateJob

/**
 * A type-safe, compile-time verified WorkerFactory that manually instantiates
 * background worker jobs using the master AppDependencyContainer.
 */
class AppWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val container = App.container
        return when (workerClassName) {
            AppUpdateDownloadJob::class.java.name -> AppUpdateDownloadJob(
                appContext,
                workerParameters,
                container.networkHelper
            )
            BackupCreateJob::class.java.name -> BackupCreateJob(
                appContext,
                workerParameters,
                container.backupCreator,
                container.storageManager,
                container.backupPreferences,
                container.backupNotifier
            )
            BackupRestoreJob::class.java.name -> BackupRestoreJob(
                appContext,
                workerParameters,
                container.backupRestorer,
                container.backupNotifier
            )
            DownloadJob::class.java.name -> DownloadJob(
                appContext,
                workerParameters,
                container.downloadManager,
                container.downloadPreferences
            )
            DelayedTrackingUpdateJob::class.java.name -> DelayedTrackingUpdateJob(
                appContext,
                workerParameters,
                container.getTracks(),
                container.trackChapter(),
                container.delayedTrackingStore
            )
            MetadataUpdateJob::class.java.name -> MetadataUpdateJob(
                appContext,
                workerParameters,
                container.sourceManager,
                container.coverCache,
                container.getLibraryManga(),
                container.updateManga(),
                container.libraryUpdateNotifier
            )
            LibraryUpdateJob::class.java.name -> LibraryUpdateJob(
                appContext,
                workerParameters,
                container.sourceManager,
                container.libraryPreferences,
                container.downloadManager,
                container.coverCache,
                container.getLibraryManga(),
                container.getManga(),
                container.updateManga(),
                container.syncChaptersWithSource(),
                container.fetchInterval(),
                container.filterChaptersForDownload(),
                container.getChaptersByMangaId(),
                container.refreshCanonicalMetadata(),
                container.libraryUpdateNotifier
            )
            else -> null
        }
    }
}
