package ephyra.domain.category.interactor

import ephyra.core.common.util.system.logcat
import ephyra.domain.manga.repository.MangaRepository
import logcat.LogPriority

class SetMangaCategories(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long, categoryIds: List<Long>) {
        try {
            mangaRepository.setMangaCategories(mangaId, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
