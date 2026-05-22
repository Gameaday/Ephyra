package ephyra.domain.track.service

import kotlinx.coroutines.flow.Flow

interface TrackerManager {

    companion object {
        const val MYANIMELIST = 1L
        const val ANILIST = 2L
        const val KITSU = 3L
        const val SHIKIMORI = 4L
        const val BANGUMI = 5L
        const val KOMGA = 6L
        const val MANGAUPDATES = 7L
        const val KAVITA = 8L
        const val SUWAYOMI = 9L
        const val JELLYFIN = 10L
    }

    suspend fun loggedInTrackers(): List<Tracker>

    fun loggedInTrackersFlow(): Flow<List<Tracker>>

    fun get(id: Long): Tracker?

    fun getAll(ids: Set<Long>): List<Tracker>

    fun getAll(): List<Tracker>
}
