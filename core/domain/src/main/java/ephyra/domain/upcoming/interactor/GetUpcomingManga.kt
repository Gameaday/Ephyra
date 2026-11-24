package ephyra.domain.upcoming.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaStatus
import ephyra.domain.manga.repository.MangaRepository
import kotlinx.coroutines.flow.Flow

class GetUpcomingManga(
    private val mangaRepository: MangaRepository,
) {

    private val includedStatuses = setOf(
        MangaStatus.Ongoing.value,
        MangaStatus.PublishingFinished.value,
    )

    suspend fun subscribe(): Flow<List<Manga>> {
        return mangaRepository.getUpcomingManga(includedStatuses)
    }
}
