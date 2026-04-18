package ephyra.app.track

import android.content.Context
import androidx.core.content.edit
import ephyra.core.common.util.system.logcat
import ephyra.domain.track.store.TrackingQueueStore
import logcat.LogPriority

class DelayedTrackingStore(context: Context) : TrackingQueueStore {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    override fun add(trackId: Long, lastChapterRead: Double) {
        val previousLastChapterRead = preferences.getFloat(trackId.toString(), 0f)
        if (lastChapterRead > previousLastChapterRead) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $trackId, last chapter read: $lastChapterRead" }
            preferences.edit {
                putFloat(trackId.toString(), lastChapterRead.toFloat())
            }
        }
    }

    override fun remove(trackId: Long) {
        preferences.edit {
            remove(trackId.toString())
        }
    }

    override fun getItems(): List<TrackingQueueStore.TrackingQueueItem> {
        return preferences.all.mapNotNull {
            TrackingQueueStore.TrackingQueueItem(
                trackId = it.key.toLong(),
                lastChapterRead = it.value.toString().toFloat(),
            )
        }
    }
}
