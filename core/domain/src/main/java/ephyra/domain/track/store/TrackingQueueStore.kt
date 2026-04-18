package ephyra.domain.track.store

/**
 * Persistent queue of tracker updates that could not be synced immediately.
 * Implementations live in :app using SharedPreferences.
 */
interface TrackingQueueStore {
    fun add(trackId: Long, lastChapterRead: Double)
    fun remove(trackId: Long)
    fun getItems(): List<TrackingQueueItem>

    data class TrackingQueueItem(
        val trackId: Long,
        val lastChapterRead: Float,
    )
}
