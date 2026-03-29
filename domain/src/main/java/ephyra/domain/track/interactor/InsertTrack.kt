package ephyra.domain.track.interactor

import ephyra.core.common.util.system.logcat
import ephyra.domain.track.model.Track
import ephyra.domain.track.repository.TrackRepository
import logcat.LogPriority

class InsertTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(track: Track) {
        try {
            trackRepository.insert(track)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(tracks: List<Track>) {
        try {
            trackRepository.insertAll(tracks)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
