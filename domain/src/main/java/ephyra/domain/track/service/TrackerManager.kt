package ephyra.domain.track.service

import kotlinx.coroutines.flow.Flow

interface TrackerManager {

    companion object {
        const val ANILIST = 2L
        const val KITSU = 3L
        const val KAVITA = 8L
        const val JELLYFIN = 10L
    }

    suspend fun loggedInTrackers(): List<Tracker>

    fun loggedInTrackersFlow(): Flow<List<Tracker>>

    fun get(id: Long): Tracker?

    fun getAll(ids: Set<Long>): List<Tracker>
}
