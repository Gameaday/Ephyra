package ephyra.domain.upcoming.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow

class GetUpcomingManga(
    private val mangaRepository: MangaRepository,
) {

    private val includedStatuses = setOf(
        SManga.ONGOING.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
    )

    suspend fun subscribe(): Flow<List<Manga>> {
        return mangaRepository.getUpcomingManga(includedStatuses)
    }
}
