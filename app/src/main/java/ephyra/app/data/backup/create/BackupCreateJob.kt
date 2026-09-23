package ephyra.app.data.backup.create

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import ephyra.app.data.backup.BackupNotifier
import ephyra.core.common.util.system.isRunning
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.setForegroundSafely
import ephyra.core.common.util.system.workManager
import ephyra.data.backup.create.BackupCreator
import ephyra.data.backup.create.BackupOptions
import ephyra.data.notification.Notifications
import kotlinx.coroutines.CancellationException
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

        val uriString = inputData.getString(URI_KEY)
        val uri = uriString?.let { Uri.parse(it) }
        val optionsArray = inputData.getBooleanArray(OPTIONS_KEY)
        val options = optionsArray?.let { BackupOptions.fromBooleanArray(it) }

        return try {
            notifier.showBackupProgress()
            val resultUri = backupCreator.createBackup(uri, options)
            notifier.showBackupComplete(resultUri.toString())
            Result.success()
        } catch (e: CancellationException) {
            // The system stopped the worker (reboot, OOM trim, app force-stop). This is
            // not a backup failure — surfacing it spams a false "backup failed" alert.
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Backup failed" }
            notifier.showBackupError(e.message ?: e.toString())
            Result.failure()
        }
    }

    companion object {
        const val TAG = "BackupCreate"

        // Periodic and one-time unique work share WorkManager's WorkName table — the
        // manual request needs its own name or KEEP sees the always-scheduled periodic
        // work and silently drops the manual backup.
        const val TAG_MANUAL = "BackupCreateManual"
        private const val URI_KEY = "backup_uri"
        private const val OPTIONS_KEY = "backup_options"

        fun setupTask(context: Context, interval: Int) {
            // Interval 0 = "Off": cancel and never build a request. WorkManager rejects
            // intervals below 15 minutes with IllegalArgumentException, so building a
            // 0-hour periodic request would crash the Settings screen on selection and
            // the ALWAYS migration on every app start.
            if (interval <= 0) {
                context.workManager.cancelUniqueWork(TAG)
                return
            }

            // No network constraint: the backup is written locally. Requiring
            // connectivity would silently postpone it forever on offline devices.
            val request = PeriodicWorkRequestBuilder<BackupCreateJob>(
                interval.toLong(),
                TimeUnit.HOURS,
            )
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun startNow(context: Context, uri: Uri? = null, optionsArray: BooleanArray? = null) {
            val data = Data.Builder()
            uri?.let { data.putString(URI_KEY, it.toString()) }
            optionsArray?.let { data.putBooleanArray(OPTIONS_KEY, it) }

            val request = OneTimeWorkRequestBuilder<BackupCreateJob>()
                .addTag(TAG)
                .setInputData(data.build())
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }

        fun isManualJobRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        /**
         * Returns the suggested filename for a new backup file, delegating to
         * [BackupCreator.getFilename].  Exposed here so that [WorkSchedulerImpl]
         * can fulfil [BackupScheduler.getBackupFilename] without requiring feature
         * modules to depend on `data`.
         */
        fun getFilename(): String = BackupCreator.getFilename()
    }
}
