package ephyra.domain.track.interactor

import ephyra.core.common.util.system.logcat
import ephyra.domain.track.repository.TrackRepository
import logcat.LogPriority

class DeleteTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(mangaId: Long, trackerId: Long) {
        try {
            trackRepository.delete(mangaId, trackerId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
