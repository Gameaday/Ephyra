package ephyra.domain.track.service

import ephyra.domain.track.model.TrackSearch

/**
 * Interface for trackers that support importing the user's remote reading list.
 */
interface ReadingListTracker {

    val id: Long

    val name: String

    suspend fun isLoggedIn(): Boolean

    /**
     * Fetches the user's full reading list with reading status, score, progress,
     * and metadata from the tracking service.
     */
    suspend fun getUserReadingList(): List<TrackSearch>
}
