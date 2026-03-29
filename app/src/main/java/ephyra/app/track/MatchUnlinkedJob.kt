package ephyra.app.track

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import ephyra.core.common.util.system.cancelNotification
import ephyra.core.common.util.system.isRunning
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.setForegroundSafely
import ephyra.core.common.util.system.workManager
import ephyra.data.notification.Notifications
import ephyra.domain.track.interactor.MatchUnlinkedManga
import kotlinx.coroutines.CancellationException
import logcat.LogPriority

/**
 * WorkManager job that resolves canonical IDs for all unlinked library manga.
 */
class MatchUnlinkedJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val matcher: MatchUnlinkedManga,
) : CoroutineWorker(context, workerParams) {

    private val notifier = MatchUnlinkedNotifier(context)

    override suspend fun doWork(): Result {
        setForegroundSafely()

        return try {
            val result = matcher.await { current, total, title ->
                notifier.showProgressNotification(title, current, total)
            }
            notifier.showCompleteNotification(result)
            logcat(LogPriority.INFO) {
                "MatchUnlinkedJob completed: ${result.linked} linked, ${result.matched} matched, ${result.total} total"
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "MatchUnlinkedJob failed" }
            notifier.showFailureNotification()
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_MATCH_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_MATCH_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        private const val TAG = "MatchUnlinkedJob"

        fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG)
        }

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<MatchUnlinkedJob>()
                .addTag(TAG)
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
            context.cancelNotification(Notifications.ID_MATCH_PROGRESS)
        }
    }
}
