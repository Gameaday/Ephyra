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
