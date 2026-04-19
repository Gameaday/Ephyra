package ephyra.domain.track.service

/**
 * Schedules deferred tracker updates. Implementations live in :app using WorkManager.
 * Domain code never needs a [android.content.Context] to enqueue work.
 */
interface TrackingJobScheduler {
    fun scheduleDelayedSync()
}
