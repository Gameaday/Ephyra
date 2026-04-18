package ephyra.app.track

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.core.common.util.system.workManager
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.store.TrackingQueueStore
import kotlinx.coroutines.delay
import logcat.LogPriority
import java.util.concurrent.TimeUnit

class DelayedTrackingUpdateJob(
    private val context: Context,
    workerParams: WorkerParameters,
    private val getTracks: GetTracks,
    private val trackChapter: TrackChapter,
    private val trackingQueue: TrackingQueueStore,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > 3) {
            return Result.failure()
        }

        withIOContext {
            val items = trackingQueue.getItems()
                .mapNotNull {
                    val track = getTracks.awaitOne(it.trackId)
                    if (track == null) {
                        trackingQueue.remove(it.trackId)
                    }
                    track?.copy(lastChapterRead = it.lastChapterRead.toDouble())
                }

            items.forEachIndexed { index, track ->
                logcat(LogPriority.DEBUG) {
                    "Updating delayed track item: ${track.mangaId}, last chapter read: ${track.lastChapterRead}"
                }
                trackChapter.await(track.mangaId, track.lastChapterRead, setupJobOnFailure = false)
                // Stagger updates to reduce burst load on tracker servers
                if (index < items.lastIndex) {
                    delay(STAGGER_DELAY_MS)
                }
            }
        }

        return if (trackingQueue.getItems().isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "DelayedTrackingUpdate"

        /**
         * Delay between queued tracker updates to stagger API requests
         * and reduce burst load on tracker servers.
         */
        private const val STAGGER_DELAY_MS = 500L

        fun setupTask(context: Context) {
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
            )

            val request = OneTimeWorkRequestBuilder<DelayedTrackingUpdateJob>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
