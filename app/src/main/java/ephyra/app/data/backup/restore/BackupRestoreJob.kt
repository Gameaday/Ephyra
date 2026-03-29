package ephyra.app.data.backup.restore

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ephyra.app.data.backup.BackupNotifier
import ephyra.data.backup.restore.BackupRestorer
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.setForegroundSafely
import logcat.LogPriority

class BackupRestoreJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val backupRestorer: BackupRestorer,
) : CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        setForegroundSafely()

        val uriString = inputData.getString(LOCATION_EXTRA) ?: return Result.failure()
        val uri = Uri.parse(uriString)

        return try {
            backupRestorer.restore(uri) { progress, total, title ->
                notifier.showRestoreProgress(progress, total, title)
            }
            notifier.showRestoreComplete(0, 0, uri.path)
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showRestoreError(e.message)
            Result.failure()
        }
    }

    companion object {
        const val LOCATION_EXTRA = "location"
    }
}
