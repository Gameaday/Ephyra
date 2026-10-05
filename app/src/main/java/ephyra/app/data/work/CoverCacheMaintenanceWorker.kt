package ephyra.app.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.workManager
import ephyra.data.cache.CoverCache
import ephyra.domain.manga.interactor.GetLibraryManga
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import java.util.concurrent.TimeUnit

/**
 * Maintains the durable remote-cover cache independently of library refreshes.
 *
 * Library cover names are resolved before pruning so maintenance never deletes a cover
 * currently owned by the library. Custom covers live in a separate directory and are not
 * touched by this worker.
 */
class CoverCacheMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val coverCache: CoverCache,
    private val getLibraryManga: GetLibraryManga,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            withIOContext {
                val libraryManga = getLibraryManga.await()
                val protectedNames = coverCache.coverFileNames(
                    libraryManga.map { it.manga.thumbnailUrl to it.manga.coverLastModified },
                )
                val pruned = coverCache.pruneOldCovers(protectedNames = protectedNames)
                if (pruned > 0) {
                    logcat(LogPriority.DEBUG) { "Cover cache maintenance pruned $pruned file(s)" }
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Cover cache maintenance failed; will retry" }
            Result.retry()
        }
    }

    companion object {
        const val TAG = "CoverCacheMaintenance"
        private const val INTERVAL_DAYS = 7L

        /**
         * Cold-start audit (Phase 6): one lightweight pass per process launch. The
         * weekly periodic task bounds long-term growth, but a crash, a killed
         * schedule, or a restore can leave the cache stale far longer; a KEEP-policy
         * one-time request costs one no-op-ish run per cold start and guarantees the
         * audit actually happens. Library covers stay protected by the same
         * protectedNames logic as the periodic run.
         */
        fun enqueueOneTimeAudit(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<CoverCacheMaintenanceWorker>()
                .addTag(TAG_ONE_TIME)
                .build()
            context.workManager.enqueueUniqueWork(
                TAG_ONE_TIME,
                androidx.work.ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private const val TAG_ONE_TIME = "CoverCacheMaintenance.OneTime"

        fun setupTask(context: Context) {
            val request = PeriodicWorkRequestBuilder<CoverCacheMaintenanceWorker>(
                INTERVAL_DAYS,
                TimeUnit.DAYS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
