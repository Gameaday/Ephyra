package ephyra.app.extension

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import ephyra.app.extension.api.ExtensionApi
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.workManager
import logcat.LogPriority
import java.util.concurrent.TimeUnit

/**
 * Periodically checks for extension updates in the background with low-power
 * and unmetered network constraints.
 */
class ExtensionUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters,
    private val extensionApi: ExtensionApi,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withIOContext {
        try {
            extensionApi.checkForUpdates(context)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to check for extension updates" }
            Result.retry()
        }
    }

    companion object {
        const val TAG = "ExtensionUpdateWorker"

        fun createConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
        }

        fun setupTask(context: Context) {
            val constraints = createConstraints()

            val request = PeriodicWorkRequestBuilder<ExtensionUpdateWorker>(
                24,
                TimeUnit.HOURS,
                2,
                TimeUnit.HOURS,
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
