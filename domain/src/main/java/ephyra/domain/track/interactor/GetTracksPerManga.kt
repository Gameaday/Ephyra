package ephyra.domain.track.interactor

import ephyra.domain.track.model.Track
import ephyra.domain.track.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetTracksPerManga(
    private val trackRepository: TrackRepository,
) {

    fun subscribe(): Flow<Map<Long, List<Track>>> {
        return trackRepository.getTracksAsFlow().map { tracks -> tracks.groupBy { it.mangaId } }
    }
}
