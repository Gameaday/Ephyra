package ephyra.app.data.backup.create

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import ephyra.app.data.backup.BackupNotifier
import ephyra.data.backup.create.BackupCreator
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.workManager
import ephyra.core.common.util.system.setForegroundSafely
import ephyra.data.notification.Notifications
import logcat.LogPriority
import java.util.concurrent.TimeUnit

class BackupCreateJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val backupCreator: BackupCreator,
) : CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        setForegroundSafely()

        return try {
            notifier.showBackupProgress()
            val uri = backupCreator.createBackup()
            notifier.showBackupComplete(uri)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showBackupError(e.message)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "BackupCreate"

        fun setupTask(context: Context, interval: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BackupCreateJob>(
                interval.toLong(),
                TimeUnit.HOURS,
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun startNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupCreateJob>()
                .addTag(TAG)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }
    }
}
