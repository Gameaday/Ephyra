package ephyra.app.track

import android.content.Context
import ephyra.domain.track.service.TrackingJobScheduler

class TrackingJobSchedulerImpl(private val context: Context) : TrackingJobScheduler {
    override fun scheduleDelayedSync() {
        DelayedTrackingUpdateJob.setupTask(context)
    }
}
